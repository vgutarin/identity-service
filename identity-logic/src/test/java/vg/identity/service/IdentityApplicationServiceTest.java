package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityApplicationMapper;
import vg.identity.model.IdentityApplication;
import vg.identity.model.principal.IdentityInternalPrincipalStatus;
import vg.identity.model.principal.IdentityInternalPrincipalType;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityApplicationRepository applicationRepository;
    @Mock
    IdentityPrincipalRepository principalRepository;
    @Mock
    IdentityWorkspaceRepository workspaceRepository;
    @Mock
    IdentityApplicationMapper applicationMapper;
    @Mock
    EncryptionService encryptionService;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    TelegramService telegramService;
    @Mock
    TelegramWebhookRegistrationService telegramWebhookRegistrationService;

    @Mock
    TelegramBotLookupService telegramBotLookupService;
    @Mock
    AuthorityChecker authorityChecker;

    @InjectMocks
    IdentityApplicationService service;

    @Test
    void getTelegramWebhookStatus_withoutUpdatePermission_returnsReadOnlyStatus() {
        var id = nextUniqueId();
        when(telegramWebhookRegistrationService.inspect(id)).thenReturn(TelegramWebhookStatus.builder()
                .state(TelegramWebhookState.YES).toggleAllowed(true).build());
        assertThat(service.getTelegramWebhookStatus(id).toggleAllowed()).isFalse();
        verify(authorityChecker).hasAuthority(id, Permission.App.UPDATE);
    }

    @Test
    void getTelegramWebhookStatus_withUpdatePermission_preservesConfiguredAvailability() {
        var id = nextUniqueId();
        when(authorityChecker.hasAuthority(id, Permission.App.UPDATE)).thenReturn(true);
        when(telegramWebhookRegistrationService.inspect(id)).thenReturn(TelegramWebhookStatus.builder()
                .state(TelegramWebhookState.NO).toggleAllowed(true).build());
        assertThat(service.getTelegramWebhookStatus(id).toggleAllowed()).isTrue();
        when(telegramWebhookRegistrationService.inspect(id)).thenReturn(TelegramWebhookStatus.builder()
                .state(TelegramWebhookState.NO).toggleAllowed(false).build());
        assertThat(service.getTelegramWebhookStatus(id).toggleAllowed()).isFalse();
    }

    @Test
    void setTelegramWebhookEnabled_passesTheExplicitDesiredStateAndVersion() {
        var id = nextUniqueId();
        var status = TelegramWebhookStatus.builder().state(TelegramWebhookState.NO).toggleAllowed(true).build();
        when(telegramWebhookRegistrationService.setEnabled(id, 4, false)).thenReturn(status);
        assertThat(service.setTelegramWebhookEnabled(id, 4, false)).isSameAs(status);
    }

    @Test
    void create_whenValidInput_returnsCreatedApplication() {
        var name = nextString();
        var uri = URI.create("https://example.com/" + nextString());
        var data = nextString();
        var nameHash = new byte[]{1, 2, 3};
        var workspace = workspace(nextLong());
        var principal = principal(nextLong(), name);
        var savedEntity = applicationEntity(principal.getUniqueId());
        var savedModel = applicationModel(principal.getUniqueId());
        var applicationCaptor = ArgumentCaptor.forClass(IdentityApplicationEntity.class);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);
        var uriString = uri.toString();

        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principal);
        when(encryptionService.hashPrincipalName(uriString)).thenReturn(nameHash);
        when(applicationRepository.save(any(IdentityApplicationEntity.class))).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(name, uri, data, workspace)).isSameAs(savedModel);

        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(name);
        assertThat(principalCaptor.getValue().getName()).isEqualTo(uriString);
        assertThat(principalCaptor.getValue().getNameHash()).isEqualTo(nameHash);
        assertThat(principalCaptor.getValue().getStatus()).isEqualTo(IdentityInternalPrincipalStatus.ACTIVE);
        assertThat(principalCaptor.getValue().getType()).isEqualTo(IdentityInternalPrincipalType.APPLICATION);

        verify(applicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getUniqueId()).isEqualTo(principal.getUniqueId());
        assertThat(applicationCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(applicationCaptor.getValue().getPayload()).isEqualTo(data);
        assertThat(savedEntity.getPrincipal()).isSameAs(principal);
        verify(applicationRepository).flush();
    }

    @Test
    void createTelegramBotApplication_whenWorkspaceExists_createsApplicationFromBot() throws JacksonException {
        var workspaceId = nextUniqueId();
        var name = nextString();
        var botUsername = nextString();
        var botToken = nextString();
        var telegramBot = TelegramBot.builder()
                .token(botToken)
                .telegramBotId(999L)
                .build();
        var validatedBot = telegramBot.withTelegramBotId(100L);
        var serializedBot = nextString();
        var expectedUri = "https://t.me/" + botUsername;
        var nameHash = new byte[]{1, 2, 3};
        var workspace = workspace(workspaceId.getLongValue());
        var principal = principal(nextLong(), name);
        var savedEntity = applicationEntity(principal.getUniqueId());
        var savedModel = applicationModel(principal.getUniqueId());
        var applicationCaptor = ArgumentCaptor.forClass(IdentityApplicationEntity.class);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);

        when(objectMapper.writeValueAsString(validatedBot)).thenReturn(serializedBot);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(telegramService.getBotUser(telegramBot)).thenReturn(
                TelegramApiClient.TelegramUser.builder().id(100L).username(botUsername).build());
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principal);
        when(encryptionService.hashPrincipalName(expectedUri)).thenReturn(nameHash);
        when(applicationRepository.save(any(IdentityApplicationEntity.class))).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(
                service.createTelegramBotApplication(workspaceId, name, telegramBot)
        ).isSameAs(savedModel);

        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(name);
        assertThat(principalCaptor.getValue().getName()).isEqualTo(expectedUri);
        verify(applicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(applicationCaptor.getValue().getPayload()).isEqualTo(serializedBot);
        verify(telegramWebhookRegistrationService).scheduleRegistration(savedModel.getUniqueId());
    }

    @Test
    void createTelegramBotApplication_whenWorkspaceIsNotFound_throwsEntityNotFoundException() {
        var telegramBot = TelegramBot.builder()
                .token(nextString())
                .build();

        assertThatThrownBy(() -> service.createTelegramBotApplication(nextUniqueId(), nextString(), telegramBot))
                .isInstanceOf(EntityNotFoundException.class);

        verify(telegramService, never()).getBotUser(telegramBot);
    }

    @Test
    void createTelegramBotApplication_whenBotIsInvalid_throwsIllegalArgumentExceptionAndDoesNotCreateApplication() {
        var workspaceId = nextUniqueId();
        var workspace = workspace(workspaceId.getLongValue());
        var telegramBot = TelegramBot.builder()
                .token(nextString())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(telegramService.getBotUser(telegramBot)).thenThrow(new IllegalArgumentException("Invalid Telegram bot"));

        assertThatThrownBy(() -> service.createTelegramBotApplication(workspaceId, nextString(), telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Telegram bot");

        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    @Test
    void updateTelegramBotApplication_whenValid_validatesTokenAndUpdatesApplication() throws JacksonException {
        var applicationId = nextUniqueId();
        var version = 3;
        var name = nextString();
        var botUsername = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var validatedBot = telegramBot.withTelegramBotId(100L);
        var previousBot = TelegramBot.builder().token(nextString()).build();
        var previousPayload = nextString();
        var serializedBot = nextString();
        var expectedUri = "https://t.me/" + botUsername;
        var nameHash = new byte[]{4, 5, 6};
        var principal = principal(applicationId.getLongValue(), nextString());
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(applicationId.getLongValue())
                .version(version)
                .principal(principal)
                .payload(previousPayload)
                .build();
        var savedEntity = applicationEntity(applicationId.getLongValue());
        var savedModel = applicationModel(applicationId.getLongValue());

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existing));
        when(telegramBotLookupService.readBot(existing)).thenReturn(previousBot.withApplicationId(applicationId));
        when(telegramService.getBotUser(telegramBot)).thenReturn(
                TelegramApiClient.TelegramUser.builder().id(100L).username(botUsername).build());
        when(objectMapper.writeValueAsString(validatedBot)).thenReturn(serializedBot);
        when(encryptionService.hashPrincipalName(expectedUri)).thenReturn(nameHash);
        when(applicationRepository.save(existing)).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.updateTelegramBotApplication(applicationId, version, name, telegramBot))
                .isSameAs(savedModel);

        assertThat(principal.getDisplayName()).isEqualTo(name);
        assertThat(principal.getName()).isEqualTo(expectedUri);
        assertThat(principal.getNameHash()).isEqualTo(nameHash);
        verify(principalRepository).save(principal);
        assertThat(existing.getPayload()).isEqualTo(serializedBot);
        verify(applicationRepository).flush();
        var ordered = org.mockito.Mockito.inOrder(telegramWebhookRegistrationService, applicationRepository);
        ordered.verify(telegramWebhookRegistrationService).removePreviousBot(previousBot.withApplicationId(applicationId), validatedBot);
        ordered.verify(applicationRepository).save(existing);
        ordered.verify(telegramWebhookRegistrationService).scheduleRegistration(applicationId);
    }

    @Test
    void updateTelegramBotApplication_whenApplicationIsNotFound_throwsEntityNotFoundException() {
        var telegramBot = TelegramBot.builder().token(nextString()).build();

        assertThatThrownBy(() -> service.updateTelegramBotApplication(nextUniqueId(), 0, nextString(), telegramBot))
                .isInstanceOf(EntityNotFoundException.class);

        verify(telegramService, never()).getBotUser(telegramBot);
        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    @Test
    void updateTelegramBotApplication_whenVersionIsStale_throwsAndDoesNotValidateOrSave() {
        var applicationId = nextUniqueId();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(applicationId.getLongValue())
                .version(5)
                .build();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateTelegramBotApplication(applicationId, 4, nextString(), telegramBot))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(telegramService, never()).getBotUser(telegramBot);
        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    @Test
    void updateTelegramBotApplication_whenTokenIsInvalid_throwsIllegalArgumentExceptionAndDoesNotSave() {
        var applicationId = nextUniqueId();
        var version = 1;
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(applicationId.getLongValue())
                .version(version)
                .build();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existing));
        when(telegramService.getBotUser(telegramBot))
                .thenThrow(new IllegalArgumentException("exception.telegram.botToken.required"));

        assertThatThrownBy(() -> service.updateTelegramBotApplication(applicationId, version, nextString(), telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exception.telegram.botToken.required");

        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    @Test
    void getById_whenEntityExists_returnsApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);
        var model = applicationModel(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(applicationMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(new UniqueId(id))).isSameAs(model);
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.getById(new UniqueId(id)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findById_whenEntityExists_returnsApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);
        var model = applicationModel(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(applicationMapper.toModel(entity)).thenReturn(model);

        assertThat(service.findById(new UniqueId(id))).isSameAs(model);
    }

    @Test
    void findById_whenEntityIsNotFound_returnsNull() {
        var id = nextLong();

        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(new UniqueId(id))).isNull();
    }

    @Test
    void findByWorkspaceUniqueId_whenApplicationsExist_returnsApplications() {
        var workspaceId = nextLong();
        var entities = List.of(applicationEntity(1L), applicationEntity(2L));
        var firstModel = applicationModel(1L);
        var secondModel = applicationModel(2L);

        when(applicationRepository.findByWorkspaceUniqueId(workspaceId)).thenReturn(entities);
        when(applicationMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(applicationMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.findByWorkspaceUniqueId(new UniqueId(workspaceId))).containsExactly(firstModel, secondModel);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedApplication() {
        var id = nextLong();
        var nameHash = new byte[]{4, 5, 6};
        var model = IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .uri("https://t.me/" + nextString())
                .payload(nextString())
                .build();
        var existing = applicationEntity(id);
        var savedEntity = applicationEntity(id);
        var savedModel = applicationModel(id);

        when(applicationRepository.findById(new UniqueId(id))).thenReturn(Optional.of(existing));
        when(encryptionService.hashPrincipalName(model.getUri())).thenReturn(nameHash);
        when(applicationRepository.save(existing)).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(applicationMapper).updateEntity(existing, model);
        assertThat(existing.getPrincipal().getName()).isEqualTo(model.getUri());
        assertThat(existing.getPrincipal().getNameHash()).isEqualTo(nameHash);
        verify(principalRepository).save(existing.getPrincipal());
        verify(applicationRepository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var model = applicationModel(nextLong());

        when(applicationRepository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var id = nextLong();
        var model = IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .version(1)
                .name(nextString())
                .uri(nextString())
                .build();
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(id)
                .version(2)
                .build();

        when(applicationRepository.findById(new UniqueId(id))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete_whenEntityExists_deleteApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);
        var serializedBot = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        entity.setPayload(serializedBot);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(telegramBotLookupService.readBot(entity)).thenReturn(telegramBot.withApplicationId(new UniqueId(id)));

        service.delete(new UniqueId(id));

        verify(applicationRepository).delete(entity);
        verify(applicationRepository).flush();
        verify(telegramWebhookRegistrationService).remove(telegramBot.withApplicationId(new UniqueId(id)));
    }

    @Test
    void create_whenNameIsNotAbsoluteUri_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                service.create(nextString(), URI.create("not-a-uri/path"), nextString(), workspace(nextLong())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    private static IdentityApplicationEntity applicationEntity(long id) {
        return IdentityApplicationEntity.builder()
                .uniqueId(id)
                .version(0)
                .principal(applicationPrincipal(id, nextString()))
                .build();
    }

    private static IdentityApplication applicationModel(long id) {
        return IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .uri(nextString())
                .build();
    }

    private static IdentityPrincipalEntity principal(long id, String name) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .displayName(name)
                .status(IdentityInternalPrincipalStatus.ACTIVE)
                .type(IdentityInternalPrincipalType.APPLICATION)
                .build();
    }

    private static IdentityPrincipalEntity applicationPrincipal(long id, String uri) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .name(uri)
                .status(IdentityInternalPrincipalStatus.ACTIVE)
                .type(IdentityInternalPrincipalType.APPLICATION)
                .build();
    }

    private static IdentityWorkspaceEntity workspace(long uniqueId) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(uniqueId)
                .name(nextString())
                .build();
    }
}

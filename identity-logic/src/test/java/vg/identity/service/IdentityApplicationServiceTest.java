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
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotWithUri;
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

    @InjectMocks
    IdentityApplicationService service;

    @Test
    void create_whenValidInput_returnsCreatedApplication() {
        var name = nextString();
        var uri = URI.create("https://example.com/" + nextString());
        var data = nextString();
        var uriHash = new byte[]{1, 2, 3};
        var workspace = workspace(nextLong());
        var principal = principal(nextLong(), name);
        var savedEntity = applicationEntity(principal.getUniqueId());
        var savedModel = applicationModel(principal.getUniqueId());
        var applicationCaptor = ArgumentCaptor.forClass(IdentityApplicationEntity.class);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);
        var uriString = uri.toString();

        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principal);
        when(encryptionService.hashCaseSensitive(uriString)).thenReturn(uriHash);
        when(applicationRepository.save(any(IdentityApplicationEntity.class))).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(name, uri, data, workspace)).isSameAs(savedModel);

        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(name);
        assertThat(principalCaptor.getValue().getStatus()).isEqualTo(IdentityPrincipalStatus.ACTIVE);
        assertThat(principalCaptor.getValue().getType()).isEqualTo(IdentityPrincipalType.APPLICATION);

        verify(applicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getUniqueId()).isEqualTo(principal.getUniqueId());
        assertThat(applicationCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(applicationCaptor.getValue().getName()).isEqualTo(name);
        assertThat(applicationCaptor.getValue().getUri()).isEqualTo(uriString);
        assertThat(applicationCaptor.getValue().getUriHash()).isEqualTo(uriHash);
        assertThat(applicationCaptor.getValue().getPayload()).isEqualTo(data);
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
                .build();
        var serializedBot = nextString();
        var expectedUri = "https://t.me/" + botUsername;
        var uriHash = new byte[]{1, 2, 3};
        var workspace = workspace(workspaceId.getLongValue());
        var principal = principal(nextLong(), name);
        var savedEntity = applicationEntity(principal.getUniqueId());
        var savedModel = applicationModel(principal.getUniqueId());
        var applicationCaptor = ArgumentCaptor.forClass(IdentityApplicationEntity.class);

        when(objectMapper.writeValueAsString(telegramBot)).thenReturn(serializedBot);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(telegramService.getUsername(telegramBot)).thenReturn(botUsername);
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principal);
        when(encryptionService.hashCaseSensitive(expectedUri)).thenReturn(uriHash);
        when(applicationRepository.save(any(IdentityApplicationEntity.class))).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(
                service.createTelegramBotApplication(workspaceId, name, telegramBot)
        ).isSameAs(savedModel);

        verify(telegramService).getUsername(telegramBot);
        verify(applicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(applicationCaptor.getValue().getName()).isEqualTo(name);
        assertThat(applicationCaptor.getValue().getUri()).isEqualTo(expectedUri);
        assertThat(applicationCaptor.getValue().getPayload()).isEqualTo(serializedBot);
    }

    @Test
    void createTelegramBotApplication_whenWorkspaceIsNotFound_throwsEntityNotFoundException() {
        var telegramBot = TelegramBot.builder()
                .token(nextString())
                .build();

        assertThatThrownBy(() -> service.createTelegramBotApplication(new UniqueId(nextLong()), nextString(), telegramBot))
                .isInstanceOf(EntityNotFoundException.class);

        verify(telegramService, never()).getUsername(telegramBot);
    }

    @Test
    void createTelegramBotApplication_whenBotIsInvalid_throwsIllegalArgumentExceptionAndDoesNotCreateApplication() {
        var workspaceId = nextUniqueId();
        var workspace = workspace(workspaceId.getLongValue());
        var telegramBot = TelegramBot.builder()
                .token(nextString())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(telegramService.getUsername(telegramBot)).thenThrow(new IllegalArgumentException("Invalid Telegram bot"));

        assertThatThrownBy(() -> service.createTelegramBotApplication(workspaceId, nextString(), telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Telegram bot");

        verify(telegramService).getUsername(telegramBot);
        verify(applicationRepository, never()).save(any(IdentityApplicationEntity.class));
    }

    @Test
    void updateTelegramBotApplication_whenValid_validatesTokenAndUpdatesApplication() throws JacksonException {
        var applicationId = nextUniqueId();
        var version = 3;
        var name = nextString();
        var botUsername = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var serializedBot = nextString();
        var expectedUri = "https://t.me/" + botUsername;
        var uriHash = new byte[]{4, 5, 6};
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(applicationId.getLongValue())
                .version(version)
                .name(nextString())
                .uri(nextString())
                .build();
        var savedEntity = applicationEntity(applicationId.getLongValue());
        var savedModel = applicationModel(applicationId.getLongValue());

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existing));
        when(telegramService.getUsername(telegramBot)).thenReturn(botUsername);
        when(objectMapper.writeValueAsString(telegramBot)).thenReturn(serializedBot);
        when(encryptionService.hashCaseSensitive(expectedUri)).thenReturn(uriHash);
        when(applicationRepository.save(existing)).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.updateTelegramBotApplication(applicationId, version, name, telegramBot))
                .isSameAs(savedModel);

        verify(telegramService).getUsername(telegramBot);
        assertThat(existing.getName()).isEqualTo(name);
        assertThat(existing.getUri()).isEqualTo(expectedUri);
        assertThat(existing.getUriHash()).isEqualTo(uriHash);
        assertThat(existing.getPayload()).isEqualTo(serializedBot);
        verify(applicationRepository).flush();
    }

    @Test
    void updateTelegramBotApplication_whenApplicationIsNotFound_throwsEntityNotFoundException() {
        var telegramBot = TelegramBot.builder().token(nextString()).build();

        assertThatThrownBy(() -> service.updateTelegramBotApplication(new UniqueId(nextLong()), 0, nextString(), telegramBot))
                .isInstanceOf(EntityNotFoundException.class);

        verify(telegramService, never()).getUsername(telegramBot);
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

        verify(telegramService, never()).getUsername(telegramBot);
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
        when(telegramService.getUsername(telegramBot))
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
    void findTelegramBotByUsername_whenBotExists_returnsBotWithUrl() throws JacksonException {
        var botUsername = nextString();
        var storedUri = "https://t.me/" + botUsername;
        var uriHash = new byte[]{7, 8, 9};
        var payload = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(nextLong())
                .uri(storedUri)
                .payload(payload)
                .build();

        when(encryptionService.hashCaseSensitive(storedUri)).thenReturn(uriHash);
        when(applicationRepository.findByUriHash(uriHash)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(payload, TelegramBot.class)).thenReturn(telegramBot);

        assertThat(service.findTelegramBotByUsername(botUsername))
                .isEqualTo(new TelegramBotWithUri(url(storedUri), telegramBot));
    }

    @Test
    void findTelegramBotByUsername_whenUsernameCasingDiffers_resolvesSameApplication() throws JacksonException {
        var normalizedUri = "https://t.me/mybot";
        var uriHash = new byte[]{7, 8, 9};
        var payload = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(nextLong())
                .uri(normalizedUri)
                .payload(payload)
                .build();

        // Telegram usernames are case-insensitive, so "MyBot" must hash to the same key as "mybot".
        when(encryptionService.hashCaseSensitive(normalizedUri)).thenReturn(uriHash);
        when(applicationRepository.findByUriHash(uriHash)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(payload, TelegramBot.class)).thenReturn(telegramBot);

        assertThat(service.findTelegramBotByUsername("MyBot"))
                .isEqualTo(new TelegramBotWithUri(url(normalizedUri), telegramBot));
    }

    @Test
    void findTelegramBotByUsername_whenBotIsNotFound_returnsNull() throws JacksonException {
        var botUsername = nextString();
        var uriHash = new byte[]{7, 8, 9};

        when(encryptionService.hashCaseSensitive("https://t.me/" + botUsername)).thenReturn(uriHash);
        when(applicationRepository.findByUriHash(uriHash)).thenReturn(Optional.empty());

        assertThat(service.findTelegramBotByUsername(botUsername)).isNull();
        verify(objectMapper, never()).readValue(any(String.class), eq(TelegramBot.class));
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
        var uriHash = new byte[]{4, 5, 6};
        var model = IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .uri(nextString())
                .payload(nextString())
                .build();
        var existing = applicationEntity(id);
        var savedEntity = applicationEntity(id);
        var savedModel = applicationModel(id);

        when(applicationRepository.findById(new UniqueId(id))).thenReturn(Optional.of(existing));
        when(encryptionService.hashCaseSensitive(model.getUri())).thenReturn(uriHash);
        when(applicationRepository.save(existing)).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(applicationMapper).updateEntity(existing, model);
        assertThat(existing.getUriHash()).isEqualTo(uriHash);
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
                .name(nextString())
                .uri(nextString())
                .build();

        when(applicationRepository.findById(new UniqueId(id))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete_whenEntityExists_deleteApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(new UniqueId(id));

        verify(applicationRepository).delete(entity);
        verify(applicationRepository).flush();
    }

    private static IdentityApplicationEntity applicationEntity(long id) {
        return IdentityApplicationEntity.builder()
                .uniqueId(id)
                .version(0)
                .name(nextString())
                .uri(nextString())
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
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.APPLICATION)
                .build();
    }

    private static URI url(String value) {
        return URI.create(value);
    }

    private static IdentityWorkspaceEntity workspace(long uniqueId) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(uniqueId)
                .name(nextString())
                .build();
    }
}

package vg.identity.service;

import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.MessageContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.audit.TelegramAuthenticationAuditor;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationTelegramPhone;
import vg.identity.model.principal.IdentityInternalApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationApiServiceTest {
    @Mock
    private IdentityApplicationService applicationService;
    @Mock
    private TelegramBotLookupService telegramBotLookupService;
    @Mock
    private CurrentIdentityApplicationService currentApplicationService;
    @Mock
    private TelegramAuthenticationService telegramAuthenticationService;
    @Mock
    private IdentityApplicationUserProvisioningService userProvisioningService;
    @Mock
    private IdentityApplicationUserService applicationUserService;
    @Mock
    private IdentityApplicationClaimService claimService;
    @Mock
    private TelegramAuthenticationAuditor auditor;
    @Mock
    private IdentityUserChannelService identityUserChannelService;

    @Mock
    private IdentityMessageService messagingService;

    private IdentityApplicationApiService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationApiService(
                applicationService,
                telegramBotLookupService,
                currentApplicationService,
                telegramAuthenticationService,
                userProvisioningService,
                applicationUserService,
                claimService,
                auditor,
                identityUserChannelService,
                messagingService
        );
    }

    @Test
    void submitMessage_whenKeyIsOmitted_returnsSubmissionFromCurrentApplicationScope() {
        var applicationId = new UniqueId(42L);
        var userId = new UniqueId(84L);
        var request = IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(java.util.UUID.randomUUID()).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationId);

        var message = vg.identity.model.IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(100L)).build();
        var submission = vg.identity.model.IdentityMessageSubmission.builder().message(message).replayed(true).build();
        when(messagingService.submit(applicationId, userId, request)).thenReturn(submission);

        assertThat(service.submitMessage(userId, request)).isSameAs(submission);
    }

    @Test
    void submitMessage_whenKeyIsProvided_preservesKeyAndSubmissionMetadata() {
        var applicationId = new UniqueId(42L);
        var userId = new UniqueId(84L);
        var key = java.util.UUID.randomUUID();
        var request = IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(key).build();
        var submission = vg.identity.model.IdentityMessageSubmission.builder()
                .message(vg.identity.model.IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(100L)).build())
                .replayed(false).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationId);
        when(messagingService.submit(applicationId, userId, request)).thenReturn(submission);

        assertThat(service.submitMessage(userId, request)).isSameAs(submission);
    }

    @Test
    void submitMessage_whenApplicationAuthenticationIsMissing_throwsAccessDeniedException() {
        when(currentApplicationService.requireApplicationUniqueId()).thenThrow(new AccessDeniedException("denied"));
        assertThatThrownBy(() -> service.submitMessage(new UniqueId(84L),
                IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(java.util.UUID.randomUUID()).build()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(messagingService);
    }

    @Test
    void me_whenCurrentPrincipalIsApplication_returnsOnlySafeApplicationMetadata() {
        var uniqueId = new UniqueId(42L);
        var principal = mock(IdentityInternalApplicationPrincipal.class);
        when(principal.getUniqueId()).thenReturn(uniqueId);
        when(currentApplicationService.requireApplicationPrincipal()).thenReturn(principal);
        when(applicationService.getAuthenticatedApplication(uniqueId)).thenReturn(
                IdentityApplication.builder()
                        .uniqueId(uniqueId)
                        .workspaceUniqueId(7L)
                        .name("Orders")
                        .uri("https://example.test/orders")
                        .payload("must-not-be-returned")
                        .build()
        );

        var response = service.me();

        assertThat(response.applicationUniqueId()).isEqualTo(uniqueId);
        assertThat(response.workspaceUniqueId()).isEqualTo(new UniqueId(7L));
        assertThat(response.name()).isEqualTo("Orders");
        assertThat(response.uri()).isEqualTo(URI.create("https://example.test/orders"));
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("applicationUniqueId", "workspaceUniqueId", "name", "uri");
    }

    @Test
    void me_whenCurrentPrincipalIsNotApplication_throwsAccessDeniedException() {
        when(currentApplicationService.requireApplicationPrincipal())
                .thenThrow(new AccessDeniedException("Application authentication is required"));

        assertThatThrownBy(service::me)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");

        verifyNoInteractions(applicationService);
    }

    @Test
    void findTelegramPhone_whenCurrentApplicationMayDisclose_returnsOnlyItsPhone() {
        var applicationUniqueId = new UniqueId(42L);
        var userUniqueId = new UniqueId(84L);
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(applicationUserService.hasTelegramPhoneDisclosure(applicationUniqueId, userUniqueId)).thenReturn(true);
        when(identityUserChannelService.findTelegramPhone(userUniqueId)).thenReturn(Optional.of("+14155552671"));

        var result = service.findTelegramPhone(userUniqueId);

        assertThat(result).contains(new IdentityApplicationTelegramPhone("+14155552671"));
    }

    @Test
    void findTelegramPhone_whenCurrentApplicationMayNotDisclose_hidesGlobalPhone() {
        var applicationUniqueId = new UniqueId(42L);
        var userUniqueId = new UniqueId(84L);
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(applicationUserService.hasTelegramPhoneDisclosure(applicationUniqueId, userUniqueId)).thenReturn(false);

        var result = service.findTelegramPhone(userUniqueId);

        assertThat(result).isEmpty();
        verifyNoInteractions(identityUserChannelService);
    }

    @Test
    void revokeTelegramPhone_clearsOnlyTheCurrentApplicationsDisclosureFlag() {
        var applicationUniqueId = new UniqueId(42L);
        var userUniqueId = new UniqueId(84L);
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);

        service.revokeTelegramPhone(userUniqueId);

        verify(applicationUserService).disableTelegramPhoneDisclosure(applicationUniqueId, userUniqueId);
    }

    @Test
    void authenticateTelegram_whenUserAlreadyEstablished_returnsSubjectAndScopedPermissions() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        var telegramUser = TelegramUserPrincipal.builder().id(100L).firstName("Ada").build();
        var identityUser = IdentityUserEntity.builder().uniqueId(84L).consentToKeepPersonalDataAt(Instant.now()).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(telegramBotLookupService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "init-data")).thenReturn(Optional.of(telegramUser));
        when(userProvisioningService.findTelegramUser(telegramUser)).thenReturn(Optional.of(identityUser));
        when(claimService.findClaimsByScope(applicationUniqueId, new UniqueId(84L)))
                .thenReturn(Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read", "orders.write")));

        // Even though this request's consent flag is false, an already-consented user still exposes its subject.
        var result = service.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(false).build());

        assertThat(result).contains(IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(84L))
                .name("Ada")
                .permissions(Set.of("orders.read", "orders.write"))
                .consentGiven(true)
                .build());
        verify(applicationUserService).recordAuthentication(applicationUniqueId, 84L);
    }

    @Test
    void authenticateTelegram_whenConsentNotGivenAndNoUser_returnsProvisionalWithoutStoring() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        var telegramUser = TelegramUserPrincipal.builder().id(100L).firstName("Ada").build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(telegramBotLookupService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "init-data")).thenReturn(Optional.of(telegramUser));
        when(userProvisioningService.findTelegramUser(telegramUser)).thenReturn(Optional.empty());

        var result = service.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(false).build());

        assertThat(result).contains(IdentityApplicationUserPrincipal.builder()
                .name("Ada")
                .consentGiven(false)
                .build());
        verify(userProvisioningService, never()).establishTelegramUser(telegramUser);
        verifyNoInteractions(applicationUserService, claimService);
    }

    @Test
    void authenticateTelegram_whenConsentGivenForNewUser_createsEstablishesAndAudits() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        var telegramUser = TelegramUserPrincipal.builder().id(100L).firstName("Ada").build();
        var createdUser = IdentityUserEntity.builder().uniqueId(84L).consentToKeepPersonalDataAt(Instant.now()).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(telegramBotLookupService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "init-data")).thenReturn(Optional.of(telegramUser));
        when(userProvisioningService.findTelegramUser(telegramUser)).thenReturn(Optional.empty());
        when(userProvisioningService.establishTelegramUser(telegramUser)).thenReturn(createdUser);
        when(claimService.findClaimsByScope(applicationUniqueId, new UniqueId(84L)))
                .thenReturn(Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read")));

        var result = service.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(true).build());

        assertThat(result).contains(IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(84L))
                .name("Ada")
                .permissions(Set.of("orders.read"))
                .consentGiven(true)
                .build());
        verify(userProvisioningService).establishTelegramUser(telegramUser);
        verify(auditor).userProvisioned(applicationUniqueId, 84L);
        verify(auditor).authenticationSucceeded(applicationUniqueId, 84L);
        verify(applicationUserService).recordAuthentication(applicationUniqueId, 84L);
    }

    @Test
    void authenticateTelegram_whenConsentGivenForLegacyUnconsentedUser_backfillsWithoutProvisionAudit() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        var telegramUser = TelegramUserPrincipal.builder().id(100L).firstName("Ada").build();
        var legacyUser = IdentityUserEntity.builder().uniqueId(84L).build(); // no consent recorded yet
        var backfilledUser = IdentityUserEntity.builder().uniqueId(84L).consentToKeepPersonalDataAt(Instant.now()).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(telegramBotLookupService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "init-data")).thenReturn(Optional.of(telegramUser));
        when(userProvisioningService.findTelegramUser(telegramUser)).thenReturn(Optional.of(legacyUser));
        when(userProvisioningService.establishTelegramUser(telegramUser)).thenReturn(backfilledUser);
        when(claimService.findClaimsByScope(applicationUniqueId, new UniqueId(84L))).thenReturn(Map.of());

        var result = service.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(true).build());

        assertThat(result).contains(IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(84L))
                .name("Ada")
                .consentGiven(true)
                .build());
        verify(auditor, never()).userProvisioned(any(), anyLong()); // backfill is not a creation
        verify(auditor).authenticationSucceeded(applicationUniqueId, 84L);
    }

    @Test
    void authenticateTelegram_whenProofIsInvalid_returnsEmptyAndPersistsNothing() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(telegramBotLookupService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "invalid")).thenReturn(Optional.empty());

        // Even with consent granted, invalid data resolves to no principal and touches no persistence.
        assertThat(service.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData("invalid").consentToKeepPersonalData(true).build())).isEmpty();

        verifyNoInteractions(userProvisioningService, applicationUserService, claimService);
    }
}

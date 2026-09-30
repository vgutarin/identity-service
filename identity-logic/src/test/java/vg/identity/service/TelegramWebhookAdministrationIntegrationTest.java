package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.model.SetTelegramWebhookCommand;
import vg.identity.model.access.Permission;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.rest.TelegramWebhookEndpoint;
import vg.unique.id.model.UniqueId;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "identity.service.public-url=https://identity.example.test/identity",
        "identity.telegram.webhook.enabled=true",
        "identity.telegram.webhook.secret=integration-test-webhook-secret-at-least-32-characters"
})
@WithMockUser(username = "webhook-admin", roles = "USER")
@ExtendWith(OutputCaptureExtension.class)
class TelegramWebhookAdministrationIntegrationTest extends BaseIntegrationTest {
    @Autowired private IdentityApplicationService service;
    @Autowired private TelegramWebhookRegistrationService registration;
    @Autowired private TelegramWebhookSecretService secrets;
    @Autowired
    private TelegramBotLookupService botLookup;
    @MockitoBean private TelegramApiClient client;

    @Test
    void inspection_requiresReadAndMutationRequiresUpdate(CapturedOutput output) {
        var user = createIdentityUser("webhook-admin");
        var workspace = createWorkspace();
        var application = createApplication(workspace, "{\"telegramBotId\":100,\"token\":\"test-token\"}");
        var id = new UniqueId(application.getUniqueId());
        var principal = principalRepository.findById(id).orElseThrow();
        principal.setName("https://t.me/admin_test_bot");
        principalRepository.saveAndFlush(principal);

        assertThatThrownBy(() -> service.getTelegramWebhookStatus(id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(client);
        grantWorkspacePermission(user, workspace, Permission.App.READ);
        when(client.getWebhookInfo("test-token")).thenReturn(info(""));

        var status = service.getTelegramWebhookStatus(id);
        assertThat(status.state()).isEqualTo(TelegramWebhookState.NO);
        assertThat(status.toggleAllowed()).isFalse();
        assertThatThrownBy(() -> service.setTelegramWebhookEnabled(id, application.getVersion(), true))
                .isInstanceOf(AccessDeniedException.class);
        verify(client, never()).setWebhook(any(), any(), any());

        grantWorkspacePermission(user, workspace, Permission.App.UPDATE);
        var url = "https://identity.example.test/identity" + TelegramWebhookEndpoint.path(id);
        when(client.setWebhook("test-token", url, secrets.secretFor(id, 100L)))
                .thenReturn(TelegramApiClient.SetWebhookResponse.builder().ok(true).build());
        when(client.getWebhookInfo("test-token")).thenReturn(info(url));
        assertThat(service.getTelegramWebhookStatus(id).toggleAllowed()).isTrue();
        assertThat(service.setTelegramWebhookEnabled(id, application.getVersion(), true).state()).isEqualTo(TelegramWebhookState.YES);

        when(client.deleteWebhook("test-token")).thenReturn(TelegramApiClient.SetWebhookResponse.builder().ok(true).build());
        when(client.getWebhookInfo("test-token")).thenReturn(info(""));
        assertThat(service.setTelegramWebhookEnabled(id, application.getVersion(), false).state()).isEqualTo(TelegramWebhookState.NO);
        assertThat(applicationRepository.findById(id).orElseThrow().getVersion()).isEqualTo(application.getVersion());
        assertThat(commandRepository.count()).isZero();

        registration.scheduleRegistration(id);
        assertThat(commandRepository.count()).isEqualTo(1);
        registration.register(SetTelegramWebhookCommand.builder().applicationUniqueId(id).build());
        verify(client, atLeast(2)).setWebhook("test-token", url, secrets.secretFor(id, 100L));
        assertThat(output.getOut()).contains("Telegram bot webhook set. uri=" + url)
                .doesNotContain("test-token", secrets.secretFor(id, 100L), "api.telegram.org/bot");
    }

    @Test
    void enable_whenBotIdIsMissing_persistsEncryptedPayloadAndReturnsVersionForSubsequentDisable() {
        var application = createLegacyApplication();
        var id = new UniqueId(application.getUniqueId());
        var url = "https://identity.example.test/identity" + TelegramWebhookEndpoint.path(id);
        when(client.getWebhookInfo("test-token")).thenReturn(info(""), info(url), info(""));
        when(client.getMe("test-token")).thenReturn(validatedBot());
        when(client.setWebhook("test-token", url, secrets.secretFor(id, 100L))).thenAnswer(invocation -> {
            // Verify SQL persistence/encryption has occurred before the remote registration request.
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var committed = applicationRepository.findById(id).orElseThrow();
            assertThat(botLookup.readBot(committed).telegramBotId()).isEqualTo(100L);
            assertThat(committed.getVersion()).isEqualTo(application.getVersion() + 1);
            var raw = (byte[]) entityManager.createNativeQuery("select payload from identity_application where unique_id = :id")
                    .setParameter("id", id.getLongValue()).getSingleResult();
            assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("test-token", "telegramBotId");
            return TelegramApiClient.SetWebhookResponse.builder().ok(true).build();
        });
        when(client.deleteWebhook("test-token")).thenReturn(TelegramApiClient.SetWebhookResponse.builder().ok(true).build());

        var initial = service.getTelegramWebhookStatus(id);
        assertThat(initial.state()).isEqualTo(TelegramWebhookState.NO);
        assertThat(initial.toggleAllowed()).isTrue();
        assertThat(initial.applicationVersion()).isEqualTo(application.getVersion());
        verify(client, never()).getMe(any());

        var enabled = service.setTelegramWebhookEnabled(id, initial.applicationVersion(), true);

        assertThat(enabled.state()).isEqualTo(TelegramWebhookState.YES);
        assertThat(enabled.applicationVersion()).isEqualTo(application.getVersion() + 1);
        var stored = applicationRepository.findById(id).orElseThrow();
        var bot = botLookup.readBot(stored);
        assertThat(bot.telegramBotId()).isEqualTo(100L);
        assertThat(bot.token()).isEqualTo("test-token");
        assertThat(stored.getVersion()).isEqualTo(enabled.applicationVersion());
        assertThat(service.setTelegramWebhookEnabled(id, enabled.applicationVersion(), false).state())
                .isEqualTo(TelegramWebhookState.NO);
        verify(client, times(1)).getMe("test-token");
        assertThat(commandRepository.count()).isZero();
    }

    @Test
    void enable_whenBotValidationFails_leavesPayloadUnchangedAndDoesNotRegister() {
        var application = createLegacyApplication();
        var id = new UniqueId(application.getUniqueId());
        when(client.getMe("test-token")).thenReturn(TelegramApiClient.GetMeResponse.builder().ok(false).build());

        assertThatThrownBy(() -> service.setTelegramWebhookEnabled(id, application.getVersion(), true))
                .isInstanceOf(IllegalArgumentException.class);

        var stored = applicationRepository.findById(id).orElseThrow();
        assertThat(stored.getPayload()).isEqualTo(application.getPayload());
        assertThat(stored.getVersion()).isEqualTo(application.getVersion());
        verify(client, never()).setWebhook(any(), any(), any());
    }

    @Test
    void enable_whenRegistrationFails_retainsTheCommittedBotIdForAnExplicitRetry() {
        var application = createLegacyApplication();
        var id = new UniqueId(application.getUniqueId());
        when(client.getMe("test-token")).thenReturn(validatedBot());
        when(client.setWebhook(any(), any(), any())).thenReturn(TelegramApiClient.SetWebhookResponse.builder().ok(false).build());

        assertThatThrownBy(() -> service.setTelegramWebhookEnabled(id, application.getVersion(), true))
                .hasMessage("exception.telegram.setWebhook.failed");

        var stored = applicationRepository.findById(id).orElseThrow();
        assertThat(botLookup.readBot(stored).telegramBotId()).isEqualTo(100L);
        assertThat(stored.getVersion()).isEqualTo(application.getVersion() + 1);
        verify(client, never()).getWebhookInfo(any());
    }

    private IdentityApplicationEntity createLegacyApplication() {
        var user = createIdentityUser("webhook-admin");
        var workspace = createWorkspace();
        grantWorkspacePermission(user, workspace, Permission.App.READ);
        grantWorkspacePermission(user, workspace, Permission.App.UPDATE);
        var application = createApplication(workspace, "{\"token\":\"test-token\"}");
        var principal = principalRepository.findById(application.getUniqueId()).orElseThrow();
        principal.setName("https://t.me/admin_legacy_bot");
        principalRepository.saveAndFlush(principal);
        return application;
    }

    private static TelegramApiClient.GetMeResponse validatedBot() {
        return TelegramApiClient.GetMeResponse.builder().ok(true).result(TelegramApiClient.TelegramUser.builder()
                .id(100L).bot(true).username("admin_legacy_bot").build()).build();
    }

    private static TelegramApiClient.GetWebhookInfoResponse info(String url) {
        return TelegramApiClient.GetWebhookInfoResponse.builder().ok(true)
                .result(TelegramApiClient.WebhookInfo.builder().url(url).build()).build();
    }
}

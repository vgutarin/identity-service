package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.application.TelegramBot;
import vg.identity.rest.TelegramWebhookEndpoint;
import vg.unique.id.model.UniqueId;

import java.net.URI;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramWebhookRegistrationServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private TelegramWebhookRegistrationService registrationService;
    @MockitoBean
    private TelegramService telegramService;
    @MockitoBean
    private TelegramWebhookSecretService secretService;
    @MockitoBean
    private IdentityPublicUriBuilder publicUriBuilder;

    @Test
    void reregisterAll_loadsOnlyTelegramBotApplicationsFromPersistence() {
        var workspace = createWorkspace();
        var telegramApplication = createApplication(workspace, "{\"telegramBotId\":100,\"token\":\"telegram-token\"}");
        var telegramPrincipal = principalRepository.findById(telegramApplication.getUniqueId()).orElseThrow();
        telegramPrincipal.setName("https://t.me/rotation_test_bot");
        principalRepository.saveAndFlush(telegramPrincipal);
        createApplication(workspace, "{\"token\":\"not-a-telegram-bot\"}");
        var applicationUniqueId = new UniqueId(telegramApplication.getUniqueId());
        var callbackUrl = "https://identity.example.test" + TelegramWebhookEndpoint.path(applicationUniqueId);
        when(secretService.isEnabled()).thenReturn(true);
        when(secretService.secretFor(applicationUniqueId, 100L)).thenReturn("derived-webhook-secret");
        when(publicUriBuilder.isConfigured()).thenReturn(true);
        when(publicUriBuilder.build(TelegramWebhookEndpoint.path(applicationUniqueId))).thenReturn(URI.create(callbackUrl));

        registrationService.reregisterAll();

        verify(telegramService).setWebhook(
                TelegramBot.builder().applicationId(applicationUniqueId).telegramBotId(100L).token("telegram-token").build(),
                callbackUrl,
                "derived-webhook-secret"
        );
    }
}

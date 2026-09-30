package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.TelegramWebhookUpdate;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = {
        "identity.telegram.webhook.enabled=true",
        "identity.telegram.webhook.secret=integration-test-webhook-secret-at-least-32-characters"
})
class TelegramBotLookupIntegrationTest extends BaseIntegrationTest {
    @Autowired private TelegramBotLookupService telegramBotLookupService;
    @Autowired private TelegramPhoneWebhookService webhookService;
    @Autowired private EncryptionService encryptionService;

    @Test
    void lookup_loadsEncryptedBotPayloadAndAttachesApplicationId() {
        var application = createApplication(createWorkspace(), "{\"telegramBotId\":100,\"token\":\"test-token\"}");
        var applicationId = new UniqueId(application.getUniqueId());
        var expected = TelegramBot.builder().applicationId(applicationId).telegramBotId(100L).token("test-token").build();

        assertThat(telegramBotLookupService.findTelegramBot(applicationId)).isEqualTo(expected);
        assertThat(telegramBotLookupService.getTelegramBot(applicationId)).isEqualTo(expected);
    }

    @Test
    void usernameLookup_resolvesEncryptedPrincipalCaseInsensitively() {
        var application = createApplication(createWorkspace(), "{\"telegramBotId\":100,\"token\":\"test-token\"}");
        var applicationId = new UniqueId(application.getUniqueId());
        var uri = TelegramUriBuilder.botUri("lookup_bot");
        var principal = principalRepository.findById(applicationId).orElseThrow();
        principal.setName(uri.toString());
        principal.setNameHash(encryptionService.hashPrincipalName(uri.toString()));
        principalRepository.saveAndFlush(principal);

        var resolved = telegramBotLookupService.findTelegramBotByUsername("Lookup_Bot");

        assertThat(resolved.uri()).isEqualTo(uri);
        assertThat(resolved.bot()).isEqualTo(TelegramBot.builder()
                .applicationId(applicationId).telegramBotId(100L).token("test-token").build());
        assertThat(telegramBotLookupService.findTelegramBotByUsername("missing_bot")).isNull();
    }

    @Test
    void absentApplication_isNullableForLookupAndIgnoredByWebhookWithoutRollback() {
        var applicationId = new UniqueId(42L);

        assertThat(telegramBotLookupService.findTelegramBot(applicationId)).isNull();
        assertThatThrownBy(() -> telegramBotLookupService.getTelegramBot(applicationId)).isInstanceOf(EntityNotFoundException.class);
        assertIgnoredWithoutRollback(applicationId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid", "null", "[]"})
    void malformedBot_isRejectedByLookupAndIgnoredByWebhookWithoutRollback(String payload) {
        var application = createApplication(createWorkspace(), payload);
        var applicationId = new UniqueId(application.getUniqueId());

        assertThatThrownBy(() -> telegramBotLookupService.findTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot");
        assertThatThrownBy(() -> telegramBotLookupService.getTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot");
        assertIgnoredWithoutRollback(applicationId);
    }

    @Test
    void storedBotWithoutId_isIgnoredByWebhookWithoutRollback() {
        var application = createApplication(createWorkspace(), "{\"token\":\"test-token\"}");
        assertIgnoredWithoutRollback(new UniqueId(application.getUniqueId()));
    }

    private void assertIgnoredWithoutRollback(UniqueId applicationId) {
        var workspace = createWorkspace();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            webhookService.accept(applicationId, "invalid-secret", contact());
            assertThat(status.isRollbackOnly()).isFalse();
            var storedWorkspace = workspaceRepository.findById(workspace.getUniqueId()).orElseThrow();
            storedWorkspace.setName("committed-after-ignored-webhook");
            workspaceRepository.saveAndFlush(storedWorkspace);
        });

        assertThat(workspaceRepository.findById(workspace.getUniqueId()).orElseThrow().getName())
                .isEqualTo("committed-after-ignored-webhook");
        assertThat(applicationUserRepository.count()).isZero();
        assertThat(channelRepository.count()).isZero();
    }

    private static TelegramWebhookUpdate contact() {
        return new TelegramWebhookUpdate(new TelegramWebhookUpdate.TelegramWebhookMessage(
                new TelegramWebhookUpdate.TelegramWebhookUser(42L), new TelegramWebhookUpdate.TelegramWebhookChat("private"),
                new TelegramWebhookUpdate.TelegramWebhookContact("+14155552671", 42L)));
    }
}

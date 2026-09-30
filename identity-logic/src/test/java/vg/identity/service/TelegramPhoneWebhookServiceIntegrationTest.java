package vg.identity.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.TelegramWebhookUpdate;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "identity.telegram.webhook.enabled=true",
        "identity.telegram.webhook.secret=integration-test-webhook-secret-at-least-32-characters"
})
class TelegramPhoneWebhookServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired private TelegramPhoneWebhookService webhookService;
    @Autowired private TelegramWebhookSecretService secretService;
    @Autowired private ObjectMapper objectMapper;

    private Logger normalizerLogger;
    private Logger webhookLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        normalizerLogger = (Logger) LoggerFactory.getLogger(PhoneNumberNormalizer.class);
        webhookLogger = (Logger) LoggerFactory.getLogger(TelegramPhoneWebhookService.class);
        appender = new ListAppender<>();
        appender.start();
        normalizerLogger.addAppender(appender);
        webhookLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        normalizerLogger.detachAppender(appender);
        webhookLogger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "4155552671 | INVALID_COUNTRY_CODE | false | 10",
            "+1 (415) | TOO_SHORT | true | 4",
            "+1 (200) 555-2671 | INVALID_NUMBER | true | 11"
    })
    void accept_whenPhoneIsRejected_logsSafeDiagnosticsWithoutChangingState(String source, String reason,
                                                                          boolean leadingPlus, int digitCount) {
        var application = createApplication(createWorkspace(), "{\"telegramBotId\":100,\"token\":\"test-token\"}");
        var applicationId = new UniqueId(application.getUniqueId());
        var updateNode = objectMapper.createObjectNode();
        var message = updateNode.putObject("message");
        message.putObject("from").put("id", 42L);
        message.putObject("chat").put("type", "private");
        message.putObject("contact").put("phone_number", source).put("user_id", 42L);
        var update = objectMapper.treeToValue(updateNode, TelegramWebhookUpdate.class);

        webhookService.accept(applicationId, secretService.secretFor(applicationId, 100L), update);

        assertThat(channelRepository.count()).isZero();
        assertThat(applicationUserRepository.count()).isZero();
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("reason=" + reason,
                "leadingPlus=" + leadingPlus, "digitCount=" + digitCount);
        assertThat(appender.list.getLast().getFormattedMessage()).isEqualTo("Normalized phone number is invalid. Skipping...");
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(source, "4155552671", "2005552671",
                    "Country Code:", "National Number:", "test-token");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}

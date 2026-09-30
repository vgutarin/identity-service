package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import vg.identity.model.application.TelegramBot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TelegramServiceTest {

    @Mock
    TelegramApiClient telegramApiClient;

    private TelegramService service;

    @BeforeEach
    void setUp() {
        service = new TelegramService(telegramApiClient);
    }

    @Test
    void getUsername_whenGetMeResponseContainsBotUsername_returnsUsername() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.getMe("123456:token")).thenReturn(successfulResponse("test_bot", true));

        assertThat(service.getUsername(telegramBot)).isEqualTo("test_bot");

        verify(telegramApiClient).getMe("123456:token");
    }

    @Test
    void getUsername_whenBotTokenIsBlank_throwsIllegalArgumentException() {
        var telegramBot = telegramBot(" ");

        assertThatThrownBy(() -> service.getUsername(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.botToken.required");

        verify(telegramApiClient, never()).getMe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getUsername_whenTelegramApiCallFails_throwsIllegalArgumentException() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.getMe("123456:token")).thenThrow(new RestClientException("Unauthorized"));

        assertThatThrownBy(() -> service.getUsername(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.getUsername.failed")
                .hasNoCause();
    }

    @Test
    void getUsername_whenGetMeResponseIsNotSuccessful_throwsIllegalArgumentException() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.getMe("123456:token")).thenReturn(
                new TelegramApiClient.GetMeResponse(false, null, "Unauthorized")
        );

        assertThatThrownBy(() -> service.getUsername(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.getMe.response.notSuccessful");
    }

    @Test
    void getUsername_whenGetMeResultIsNotBot_throwsIllegalArgumentException() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.getMe("123456:token")).thenReturn(successfulResponse("test_bot", false));

        assertThatThrownBy(() -> service.getUsername(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.getMe.user.notBot");
    }

    @Test
    void getUsername_whenGetMeUsernameIsBlank_throwsIllegalArgumentException() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.getMe("123456:token")).thenReturn(successfulResponse(" ", true));

        assertThatThrownBy(() -> service.getUsername(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.getMe.username.required");
    }

    @Test
    void setWebhook_whenTelegramAcceptsTheWebhook_forwardsBotCallbackAndSecret(CapturedOutput output) {
        var telegramBot = telegramBot("123456:token");
        var callbackUrl = "https://identity.example.com/api/v1/telegram/webhooks/42";
        var secretToken = "per-application-secret";
        when(telegramApiClient.setWebhook("123456:token", callbackUrl, secretToken))
                .thenReturn(successfulSetWebhookResponse());

        service.setWebhook(telegramBot, callbackUrl, secretToken);

        assertThat(output.getOut()).contains("Telegram bot webhook set. uri=" + callbackUrl)
                .doesNotContain("123456:token", secretToken, "api.telegram.org/bot");
    }

    @Test
    void setWebhook_whenTelegramRejectsTheWebhook_throwsStableFailure(CapturedOutput output) {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.setWebhook("123456:token", "https://identity.example.com/callback", "secret"))
                .thenReturn(new TelegramApiClient.SetWebhookResponse(false, "Bad request"));

        assertThatThrownBy(() -> service.setWebhook(telegramBot, "https://identity.example.com/callback", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.setWebhook.failed");
        assertThat(output.getAll()).doesNotContain("Telegram bot webhook set.", "123456:token");
    }

    @Test
    void deleteWebhook_whenTelegramAcceptsTheDeletion_forwardsTheBotToken() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.deleteWebhook("123456:token")).thenReturn(successfulSetWebhookResponse());

        service.deleteWebhook(telegramBot);

        verify(telegramApiClient).deleteWebhook("123456:token");
    }

    @Test
    void deleteWebhook_whenTelegramApiCallFails_throwsStableFailure() {
        var telegramBot = telegramBot("123456:token");
        when(telegramApiClient.deleteWebhook("123456:token")).thenThrow(new RestClientException("Unavailable"));

        assertThatThrownBy(() -> service.deleteWebhook(telegramBot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.telegram.deleteWebhook.failed")
                .hasNoCause();
    }

    @Test
    void getBotUser_returnsTheValidatedStableId() {
        when(telegramApiClient.getMe("123456:token")).thenReturn(successfulResponse("test_bot", true));
        assertThat(service.getBotUser(telegramBot("123456:token")).id()).isEqualTo(123456L);
    }

    @Test
    void getBotUser_whenIdIsMissing_rejectsTheResponse() {
        when(telegramApiClient.getMe("123456:token")).thenReturn(new TelegramApiClient.GetMeResponse(true,
                TelegramApiClient.TelegramUser.builder().bot(true).username("test_bot").build(), null));
        assertThatThrownBy(() -> service.getBotUser(telegramBot("123456:token")))
                .hasMessage("exception.telegram.getMe.id.required");
    }

    @Test
    void deleteWebhook_whenOldTokenIsUnauthorized_isNonBlocking() {
        when(telegramApiClient.deleteWebhook("123456:token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        org.assertj.core.api.Assertions.assertThatCode(() -> service.deleteWebhook(telegramBot("123456:token")))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteWebhook_whenForbidden_stillFailsWithoutRetainingSecrets() {
        when(telegramApiClient.deleteWebhook("123456:token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "url contains 123456:token"));
        assertThatThrownBy(() -> service.deleteWebhook(telegramBot("123456:token")))
                .hasMessage("exception.telegram.deleteWebhook.failed").hasNoCause();
    }

    @Test
    void getBotUser_whenNewTokenIsUnauthorized_fails() {
        when(telegramApiClient.getMe("123456:token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.getBotUser(telegramBot("123456:token")))
                .hasMessage("exception.telegram.getUsername.failed").hasNoCause();
    }

    @Test
    void setWebhook_whenUnauthorized_failsWithoutRetainingSecrets(CapturedOutput output) {
        when(telegramApiClient.setWebhook("123456:token", "https://example.test/webhook", "secret"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "url contains 123456:token"));
        assertThatThrownBy(() -> service.setWebhook(telegramBot("123456:token"), "https://example.test/webhook", "secret"))
                .hasMessage("exception.telegram.setWebhook.failed").hasNoCause();
        assertThat(output.getAll()).doesNotContain("Telegram bot webhook set.", "url contains 123456:token");
    }

    @Test
    void getWebhookUrl_returnsEmptyOrConfiguredUrlWithoutExposingOtherFields() {
        when(telegramApiClient.getWebhookInfo("123456:token")).thenReturn(
                TelegramApiClient.GetWebhookInfoResponse.builder().ok(true)
                        .result(TelegramApiClient.WebhookInfo.builder().url("").build()).build(),
                TelegramApiClient.GetWebhookInfoResponse.builder().ok(true)
                        .result(TelegramApiClient.WebhookInfo.builder().url("https://example.test/webhook").build()).build());
        assertThat(service.getWebhookUrl(telegramBot("123456:token"))).isEmpty();
        assertThat(service.getWebhookUrl(telegramBot("123456:token"))).isEqualTo("https://example.test/webhook");
    }

    @Test
    void getWebhookUrl_rejectsMissingAndUnsuccessfulResponses() {
        when(telegramApiClient.getWebhookInfo("123456:token")).thenReturn(null,
                TelegramApiClient.GetWebhookInfoResponse.builder().ok(false).build(),
                TelegramApiClient.GetWebhookInfoResponse.builder().ok(true).build(),
                TelegramApiClient.GetWebhookInfoResponse.builder().ok(true)
                        .result(TelegramApiClient.WebhookInfo.builder().build()).build());
        for (var i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.getWebhookUrl(telegramBot("123456:token")))
                    .hasMessage("exception.telegram.getWebhookInfo.failed").hasNoCause();
        }
    }

    @Test
    void getWebhookUrl_sanitizesTransportErrors() {
        when(telegramApiClient.getWebhookInfo("123456:token"))
                .thenThrow(new RestClientException("url contains 123456:token"));
        assertThatThrownBy(() -> service.getWebhookUrl(telegramBot("123456:token")))
                .hasMessage("exception.telegram.getWebhookInfo.failed").hasNoCause();
    }

    @Test
    void deleteWebhookStrict_whenUnauthorized_failsWithoutRetainingSecrets() {
        when(telegramApiClient.deleteWebhook("123456:token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "123456:token"));
        assertThatThrownBy(() -> service.deleteWebhookStrict(telegramBot("123456:token")))
                .hasMessage("exception.telegram.deleteWebhook.failed").hasNoCause();
    }

    @Test
    void deleteWebhookStrict_whenSuccessful_forwardsTheToken() {
        when(telegramApiClient.deleteWebhook("123456:token")).thenReturn(successfulSetWebhookResponse());
        service.deleteWebhookStrict(telegramBot("123456:token"));
    }

    private static TelegramBot telegramBot(String token) {
        return TelegramBot.builder()
                .token(token)
                .build();
    }

    private static TelegramApiClient.GetMeResponse successfulResponse(String username, boolean bot) {
        return new TelegramApiClient.GetMeResponse(
                true,
                new TelegramApiClient.TelegramUser(123456L, bot, "Test", username),
                null
        );
    }

    private static TelegramApiClient.SetWebhookResponse successfulSetWebhookResponse() {
        return new TelegramApiClient.SetWebhookResponse(true, null);
    }
}

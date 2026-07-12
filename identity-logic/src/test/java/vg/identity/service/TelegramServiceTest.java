package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import vg.identity.model.application.TelegramBot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
                .hasCauseInstanceOf(RestClientException.class);
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
}

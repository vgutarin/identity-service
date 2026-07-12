package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import vg.identity.model.application.TelegramBot;

import java.util.Objects;

@RequiredArgsConstructor
@Service
public class TelegramService {

    private final TelegramApiClient telegramApiClient;

    public String getUsername(TelegramBot telegramBot) {
        Objects.requireNonNull(telegramBot, "telegramBot is required");
        if (!StringUtils.hasText(telegramBot.token())) {
            throw new IllegalArgumentException("exception.telegram.botToken.required");
        }

        TelegramApiClient.GetMeResponse response;
        try {
            response = telegramApiClient.getMe(telegramBot.token());
        } catch (RestClientException e) {
            throw new IllegalArgumentException("exception.telegram.getUsername.failed", e);
        }

        return getUsername(response);
    }

    private String getUsername(TelegramApiClient.GetMeResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("exception.telegram.getMe.response.required");
        }
        if (!Boolean.TRUE.equals(response.ok())) {
            throw new IllegalArgumentException("exception.telegram.getMe.response.notSuccessful");
        }

        var user = response.result();
        if (user == null) {
            throw new IllegalArgumentException("exception.telegram.getMe.result.required");
        }
        if (!Boolean.TRUE.equals(user.bot())) {
            throw new IllegalArgumentException("exception.telegram.getMe.user.notBot");
        }
        if (!StringUtils.hasText(user.username())) {
            throw new IllegalArgumentException("exception.telegram.getMe.username.required");
        }

        return user.username();
    }
}

package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vg.identity.model.application.TelegramBot;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class TelegramService {

    private final TelegramApiClient telegramApiClient;

    public String getUsername(TelegramBot telegramBot) {
        return getBotUser(telegramBot).username();
    }

    public TelegramApiClient.TelegramUser getBotUser(TelegramBot telegramBot) {
        Objects.requireNonNull(telegramBot, "telegramBot is required");
        if (!StringUtils.hasText(telegramBot.token())) {
            throw new IllegalArgumentException("exception.telegram.botToken.required");
        }

        TelegramApiClient.GetMeResponse response;
        try {
            response = telegramApiClient.getMe(telegramBot.token());
        } catch (RestClientException e) {
            throw new IllegalArgumentException("exception.telegram.getUsername.failed");
        }

        return validatedBotUser(response);
    }

    public void setWebhook(TelegramBot telegramBot, String callbackUrl, String secretToken) {
        try {
            var response = telegramApiClient.setWebhook(telegramBot.token(), callbackUrl, secretToken);
            if (response == null || !Boolean.TRUE.equals(response.ok())) {
                throw new IllegalArgumentException("exception.telegram.setWebhook.failed");
            }
            log.info("Telegram bot webhook set. uri={}", callbackUrl);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("exception.telegram.setWebhook.failed");
        }
    }

    public void deleteWebhook(TelegramBot telegramBot) {
        deleteWebhook(telegramBot, true);
    }

    /** Manual administration must not report successful deletion for a rejected token. */
    public void deleteWebhookStrict(TelegramBot telegramBot) {
        deleteWebhook(telegramBot, false);
    }

    public String getWebhookUrl(TelegramBot telegramBot) {
        if (!StringUtils.hasText(telegramBot.token())) {
            throw new IllegalArgumentException("exception.telegram.botToken.required");
        }
        try {
            var response = telegramApiClient.getWebhookInfo(telegramBot.token());
            if (response == null || !Boolean.TRUE.equals(response.ok())
                    || response.result() == null || response.result().url() == null) {
                throw new IllegalArgumentException("exception.telegram.getWebhookInfo.failed");
            }
            return response.result().url();
        } catch (RestClientException e) {
            throw new IllegalArgumentException("exception.telegram.getWebhookInfo.failed");
        }
    }

    private void deleteWebhook(TelegramBot telegramBot, boolean allowUnauthorized) {
        try {
            var response = telegramApiClient.deleteWebhook(telegramBot.token());
            if (response == null || !Boolean.TRUE.equals(response.ok())) {
                throw new IllegalArgumentException("exception.telegram.deleteWebhook.failed");
            }
            log.info("Telegram bot webhook deleted.");
        } catch (RestClientException e) {
            if (allowUnauthorized && e instanceof RestClientResponseException responseException && responseException.getStatusCode().value() == 401) {
                log.warn("Telegram bot webhook could not be deleted because token has expired or revoked.");
                return;
            }
            throw new IllegalArgumentException("exception.telegram.deleteWebhook.failed");
        }
    }

    private TelegramApiClient.TelegramUser validatedBotUser(TelegramApiClient.GetMeResponse response) {
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
        if (user.id() == null || user.id() <= 0) {
            throw new IllegalArgumentException("exception.telegram.getMe.id.required");
        }
        return user;
    }

}

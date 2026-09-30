package vg.identity.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramApiClient {

    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org";
    private static final int MIN_WEBHOOK_MAX_CONNECTIONS = 1;
    private static final int MAX_WEBHOOK_MAX_CONNECTIONS = 100;

    private final RestClient restClient;
    private final int webhookMaxConnections;

    public TelegramApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${identity.telegram.webhook.max-connections:2}") int webhookMaxConnections
    ) {
        if (webhookMaxConnections < MIN_WEBHOOK_MAX_CONNECTIONS || webhookMaxConnections > MAX_WEBHOOK_MAX_CONNECTIONS) {
            throw new IllegalArgumentException("identity.telegram.webhook.max-connections must be between 1 and 100");
        }
        this.restClient = restClientBuilder.clone()
                .baseUrl(TELEGRAM_API_BASE_URL)
                .build();
        this.webhookMaxConnections = webhookMaxConnections;
    }

    public GetMeResponse getMe(String token) {
        return restClient.get()
                .uri("/bot{token}/getMe", token)
                .retrieve()
                .body(GetMeResponse.class);
    }

    public SetWebhookResponse setWebhook(String token, String url, String secretToken) {
        return restClient.post()
                .uri("/bot{token}/setWebhook", token)
                .body(new SetWebhookRequest(url, secretToken, webhookMaxConnections))
                .retrieve()
                .body(SetWebhookResponse.class);
    }

    public SetWebhookResponse deleteWebhook(String token) {
        return restClient.post()
                .uri("/bot{token}/deleteWebhook", token)
                .retrieve()
                .body(SetWebhookResponse.class);
    }

    public GetWebhookInfoResponse getWebhookInfo(String token) {
        return restClient.get()
                .uri("/bot{token}/getWebhookInfo", token)
                .retrieve()
                .body(GetWebhookInfoResponse.class);
    }

    @Builder
    public record GetWebhookInfoResponse(Boolean ok, WebhookInfo result) {
    }

    @Builder
    public record WebhookInfo(String url) {
    }

    @Builder
    public record GetMeResponse(
            Boolean ok,
            TelegramUser result,
            String description
    ) {
    }

    @Builder
    public record TelegramUser(
            Long id,
            @JsonProperty("is_bot")
            Boolean bot,
            @JsonProperty("first_name")
            String firstName,
            String username
    ) {
    }

    public record SetWebhookRequest(
            String url,
            @JsonProperty("secret_token") String secretToken,
            @JsonProperty("max_connections") int maxConnections
    ) {
    }

    @Builder
    public record SetWebhookResponse(Boolean ok, String description) {
    }
}

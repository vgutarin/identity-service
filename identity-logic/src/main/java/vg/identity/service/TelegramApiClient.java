package vg.identity.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramApiClient {

    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org";

    private final RestClient restClient;

    public TelegramApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(TELEGRAM_API_BASE_URL)
                .build();
    }

    public GetMeResponse getMe(String token) {
        return restClient.get()
                .uri("/bot{token}/getMe", token)
                .retrieve()
                .body(GetMeResponse.class);
    }

    public record GetMeResponse(
            Boolean ok,
            TelegramUser result,
            String description
    ) {
    }

    public record TelegramUser(
            Long id,
            @JsonProperty("is_bot")
            Boolean bot,
            @JsonProperty("first_name")
            String firstName,
            String username
    ) {
    }
}

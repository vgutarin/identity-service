package vg.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties("identity.action-token")
public class IdentityActionTokenProperties {
    @NotBlank
    private String verifyEmailBaseUrl = "/verify/email/";

    @NotBlank
    private String resetPasswordBaseUrl = "/reset/password/";

    @NotNull
    private Duration expiresIn = Duration.ofDays(1);

    @NotNull
    private Duration requestCooldown = Duration.ofMinutes(5);

    @NotBlank
    private String telegramStartAppParam = "startapp";

    @NotNull
    private ResetRateLimit resetRateLimit = new ResetRateLimit();

    /**
     * Per-IP/client rate limit for the password-recovery request surface (FR-007a), on top of the
     * per-email {@link #requestCooldown}. Bounds how many recovery requests a single client may make in a
     * window, so the endpoint cannot be used to email-bomb many addresses or probe for accounts.
     */
    @Getter
    @Setter
    public static class ResetRateLimit {
        @NotNull
        private Integer maxRequests = 10;

        @NotNull
        private Duration window = Duration.ofMinutes(10);
    }
}

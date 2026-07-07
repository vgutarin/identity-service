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
@ConfigurationProperties("identity.user-channel-verification")
public class IdentityUserChannelVerificationProperties {
    @NotBlank
    private String linkPrefix = "/channel/verify?id=";

    @NotNull
    private Duration expiresIn = Duration.ofDays(1);

    @NotNull
    private Duration requestCooldown = Duration.ofMinutes(5);
}

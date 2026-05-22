package vg.identity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("identity.encryption")
public class EncryptionProperties {
    private String secret;
    private String salt;
}

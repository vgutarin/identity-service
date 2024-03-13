package vg.identity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(IdentityLogicConfig.class)
public class IdentityLogicAutoConfig {
}

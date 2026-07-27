package vg.identity.rest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(IdentityRestClient.class)
public class IdentityRestClientAutoConfig {
}

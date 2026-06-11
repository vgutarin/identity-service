package vg.identity.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUserAuthorityServiceTest {

    @Test
    void normalizeAuthorityNameTrimsAndLowercases() {
        assertThat(IdentityUserAuthorityService.normalizeAuthorityName(" Read "))
                .isEqualTo("read");
        assertThat(IdentityUserAuthorityService.normalizeAuthorityName("ACCOUNT:WRITE"))
                .isEqualTo("account:write");
    }

    @Test
    void resourceAuthorityNameIncludesResourceIdAndNormalizedAuthorityName() {
        assertThat(IdentityUserAuthorityService.resourceAuthorityName(123L, " Read "))
                .isEqualTo("123:read");
        assertThat(IdentityUserAuthorityService.resourceAuthorityName(987L, "ACCOUNT:WRITE"))
                .isEqualTo("987:account:write");
    }
}

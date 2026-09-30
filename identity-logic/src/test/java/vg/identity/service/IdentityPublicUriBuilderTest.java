package vg.identity.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class IdentityPublicUriBuilderTest {

    @Test
    void build_whenConfigured_combinesTheNormalizedPublicOriginAndAbsolutePath() {
        var builder = new IdentityPublicUriBuilder("https://identity.example.com/identity///");

        assertThat(builder.build("/api/v1/telegram/webhooks/42").toString())
                .isEqualTo("https://identity.example.com/identity/api/v1/telegram/webhooks/42");
    }

    @Test
    void build_whenPublicOriginIsNotConfigured_failsExplicitly() {
        var builder = new IdentityPublicUriBuilder(" ");

        assertThat(builder.isConfigured()).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> builder.build("/api"));
    }

    @Test
    void build_whenPathIsNotAbsolute_rejectsIt() {
        var builder = new IdentityPublicUriBuilder("https://identity.example.com");

        assertThatIllegalArgumentException().isThrownBy(() -> builder.build("api"));
    }
}

package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.identity.service.IdentityApplicationApiService;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constitution Principle VI: the embedded and remote implementations of {@code IdentityApplicationApi} must
 * behave equivalently. Runs {@code me()} through the embedded {@link IdentityApplicationApiService} and the
 * remote {@link IdentityApplicationApiRestClient} for the same application and asserts identical results.
 */
class EmbeddedVsRemoteConformanceTest extends BaseIntegrationTest {

    @Autowired
    private IdentityApplicationApiService embeddedApi;
    @Autowired
    private IdentityApplicationApiRestClient remoteApi;

    @Test
    void me_embeddedAndRemote_returnEquivalentMetadata() {
        var application = createApplicationWithApiKey();

        var remote = remoteApi.me();

        var principal = new IdentityApiKeyPrincipal(new UniqueId(application.uniqueId()), application.uri());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            var embedded = embeddedApi.me();
            assertThat(embedded).isEqualTo(remote);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

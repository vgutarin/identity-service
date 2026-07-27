package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationRestClientIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IdentityApplicationApiRestClient restClient;

    @Test
    void me_whenConfiguredApiKeyIsValid_returnsOnlyAuthenticatedApplicationMetadata() {
        var application = createApplicationWithApiKey();

        var response = restClient.me();

        assertThat(response.uniqueId()).isEqualTo(new UniqueId(application.uniqueId()).toString());
        assertThat(response.workspaceUniqueId()).isEqualTo(application.workspaceUniqueId());
        assertThat(response.name()).isEqualTo(application.name());
        assertThat(response.uri()).isEqualTo(application.uri());
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("uniqueId", "workspaceUniqueId", "name", "uri");
    }

}

package vg.identity.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vg.identity.service.IdentityApplicationApi;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMessageSubmissionTest {
    @Test
    void serialization_whenSubmissionIsNewOrReplayed_preservesMessageAndReplayFlag() {
        var mapper = JsonMapper.builder().build();
        var message = IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(100L))
                .userUniqueId(new UniqueId(84L)).status(IdentityMessageStatus.QUEUED)
                .createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build();
        for (var replayed : new boolean[]{false, true}) {
            var submission = IdentityMessageSubmission.builder().message(message).replayed(replayed).build();
            assertThat(mapper.readValue(mapper.writeValueAsString(submission), IdentityMessageSubmission.class)).isEqualTo(submission);
        }
    }

    @Test
    void submitMessage_whenPublicApiIsInspected_exposesSingleRequestBasedMethodWithoutSendAlias() throws Exception {
        var method = IdentityApplicationApi.class.getMethod("submitMessage", UniqueId.class,
                IdentityApplicationMessageRequest.class);
        assertThat(method.getReturnType()).isEqualTo(IdentityMessageSubmission.class);
        assertThat(method.isDefault()).isFalse();
        assertThat(Arrays.stream(IdentityApplicationApi.class.getMethods())
                .filter(candidate -> candidate.getName().equals("submitMessage"))).hasSize(1);
        assertThat(Arrays.stream(IdentityApplicationApi.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("sendMessage");
    }
}

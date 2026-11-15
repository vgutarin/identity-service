package vg.identity.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityApplicationMessage(
        UniqueId messageUniqueId,
        UniqueId userUniqueId,
        IdentityMessageStatus status,
        Instant createdAt,
        Instant updatedAt,
        IdentityMessageFailureCode failureCode
) {
}

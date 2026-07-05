package vg.identity.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record IdentityCommand(
        long id,
        Instant createdAt,
        Instant updatedAt,
        int version,
        IdentityCommandStatus commandStatus,
        IdentityCommandType commandType,
        String payload,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}

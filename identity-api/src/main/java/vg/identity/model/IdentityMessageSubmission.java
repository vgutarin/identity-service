package vg.identity.model;

import lombok.Builder;

/** Submission result shared by embedded and remote callers; replayed identifies an existing keyed message. */
@Builder
public record IdentityMessageSubmission(IdentityApplicationMessage message, boolean replayed) {
}

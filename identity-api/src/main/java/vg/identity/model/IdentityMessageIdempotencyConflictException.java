package vg.identity.model;

public class IdentityMessageIdempotencyConflictException extends IllegalStateException {
    public IdentityMessageIdempotencyConflictException() {
        super("Idempotency key is already used for a different message");
    }
}

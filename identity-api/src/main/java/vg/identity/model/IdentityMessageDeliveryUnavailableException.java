package vg.identity.model;

/** Deliberately does not distinguish unknown users, missing membership, and unavailable channels. */
public class IdentityMessageDeliveryUnavailableException extends IllegalStateException {
    public IdentityMessageDeliveryUnavailableException() {
        super("Message delivery is unavailable");
    }
}

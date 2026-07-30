package vg.identity.model;

/**
 * Inputs required to provision a brand-new identity user during email-invitation confirmation: the profile
 * display name, the raw password (encoded server-side), and the personal-data consent that user creation
 * requires. Crosses the module boundary (the Vaadin frontend builds it, the logic module consumes it). Every
 * consumer treats a {@code null} instance as "no provisioning payload supplied" (existing-user reuse, or
 * legacy callers); provisioning a new user with {@code consentGranted == false} (or a {@code null} payload) is
 * rejected.
 */
public record UserProvisioningDetails(String displayName, String rawPassword, boolean consentGranted) {
}

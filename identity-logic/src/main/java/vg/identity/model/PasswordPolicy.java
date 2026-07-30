package vg.identity.model;

/**
 * Single source of truth for the password-strength rule applied when a new identity user is provisioned:
 * at least {@link #MIN_LENGTH} characters and at least one lowercase letter, one uppercase letter and one
 * digit. {@link #isStrong(String)} backs the frontend Binder validator (inline feedback) and
 * {@link #requireStrong(String)} is the server-side safety net, so the two can never drift.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final String WEAK_PASSWORD_MESSAGE_KEY = "exception.user.password.weak";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        var lower = false;
        var upper = false;
        var digit = false;
        for (var i = 0; i < password.length(); i++) {
            var c = password.charAt(i);
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return lower && upper && digit;
    }

    /**
     * Server-side enforcement. Throws {@link IllegalArgumentException} carrying {@link #WEAK_PASSWORD_MESSAGE_KEY}
     * (an i18n key resolvable by the frontend {@code LocalizationService.i18n(Exception)}), mirroring the
     * {@code exception.user.username.invalid} convention already used for username validation.
     */
    public static void requireStrong(String password) {
        if (!isStrong(password)) {
            throw new IllegalArgumentException(WEAK_PASSWORD_MESSAGE_KEY);
        }
    }
}

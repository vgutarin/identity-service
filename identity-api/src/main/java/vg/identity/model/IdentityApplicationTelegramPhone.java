package vg.identity.model;

/**
 * A phone number voluntarily shared through Telegram and disclosed only to an application with an active
 * application-user membership flag. It is a contact detail, not proof of phone-number control.
 */
public record IdentityApplicationTelegramPhone(String phoneNumber) {
}

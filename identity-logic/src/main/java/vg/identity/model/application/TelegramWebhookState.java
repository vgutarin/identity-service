package vg.identity.model.application;

/** Observed URL configuration at Telegram, not a delivery-health or secret-validation result. */
public enum TelegramWebhookState {
    YES,
    NO,
    YES_BUT_URI_INCORRECT
}

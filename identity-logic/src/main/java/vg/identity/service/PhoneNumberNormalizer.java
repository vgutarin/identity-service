package vg.identity.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Validates Telegram-provided phone contacts and stores them only in canonical E.164 form.
 */
@Slf4j
@Component
public class PhoneNumberNormalizer {
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public @Nullable String normalize(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            var parsed = phoneNumberUtil.parse(source, "ZZ");
            if (!phoneNumberUtil.isValidNumber(parsed)) {
                var possibleNumberReason = phoneNumberUtil.isPossibleNumberWithReason(parsed);
                var reason = switch (possibleNumberReason) {
                    case IS_POSSIBLE, IS_POSSIBLE_LOCAL_ONLY -> "INVALID_NUMBER";
                    default -> possibleNumberReason.name();
                };
                logRejection(source, reason);
                return null;
            }
            return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            logRejection(source, e.getErrorType().name());
            return null;
        }
    }

    private void logRejection(String source, String reason) {
        log.info("Phone number rejected: reason={}, leadingPlus={}, digitCount={}, containsLetters={}, containsFormatting={}",
                reason,
                source.stripLeading().startsWith("+"),
                source.codePoints().filter(Character::isDigit).count(),
                source.codePoints().anyMatch(Character::isLetter),
                source.codePoints().anyMatch(character -> Character.isWhitespace(character)
                        || character == '(' || character == ')' || character == '-' || character == '.'));
    }
}

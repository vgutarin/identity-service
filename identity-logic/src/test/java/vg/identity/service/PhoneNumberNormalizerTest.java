package vg.identity.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberNormalizerTest {
    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(PhoneNumberNormalizer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void normalize_whenInternationalNumberIsValid_returnsE164() {
        assertThat(normalizer.normalize("+1 (415) 555-2671")).isEqualTo("+14155552671");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void normalize_whenValueIsMissing_returnsNullWithoutLogging() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("")).isNull();
        assertThat(normalizer.normalize(" \t\n")).isNull();
        assertThat(appender.list).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "4155552671 | INVALID_COUNTRY_CODE | false | 10 | false | false",
            "+9994155552671 | INVALID_COUNTRY_CODE | true | 13 | false | false",
            "not-a-phone | NOT_A_NUMBER | false | 0 | true | true",
            "+1 (415) | TOO_SHORT | true | 4 | false | true",
            "+1 (200) 555-2671 | INVALID_NUMBER | true | 11 | false | true",
            "+14155552671123456 | TOO_LONG | true | 17 | false | false"
    })
    void normalize_whenRejected_logsOnlyReasonAndInputShape(String source, String reason, boolean leadingPlus,
                                                           int digitCount, boolean containsLetters, boolean containsFormatting) {
        assertThat(normalizer.normalize(source)).isNull();

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo(
                "Phone number rejected: reason=%s, leadingPlus=%s, digitCount=%d, containsLetters=%s, containsFormatting=%s"
                        .formatted(reason, leadingPlus, digitCount, containsLetters, containsFormatting));
        assertThat(event.getFormattedMessage()).doesNotContain(source, "Country Code:", "National Number:");
        assertThat(event.getArgumentArray()).containsExactly(reason, leadingPlus, (long) digitCount, containsLetters, containsFormatting);
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void normalize_whenInputContainsNewlines_doesNotInjectInputIntoLogs() {
        assertThat(normalizer.normalize("4155552671\r\nforged-log-entry")).isNull();

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).contains("leadingPlus=false", "digitCount=10",
                "containsLetters=true", "containsFormatting=true");
        assertThat(event.getFormattedMessage()).doesNotContain("4155552671", "forged-log-entry", "\r", "\n");
        assertThat(event.getThrowableProxy()).isNull();
    }
}

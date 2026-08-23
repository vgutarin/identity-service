package vg.identity.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramAuthenticationAuditorTest {

    private final TelegramAuthenticationAuditor auditor = new TelegramAuthenticationAuditor();

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger("vg.identity.security.audit");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void userProvisioned_logsCreationEventReferencingSubjectOnly() {
        auditor.userProvisioned(new UniqueId(42L), 84L);

        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains(
                "event=telegram.user.provision",
                "outcome=CREATED",
                "applicationUniqueId=" + new UniqueId(42L),
                "sub=" + new UniqueId(84L));
    }

    @Test
    void authenticationSucceeded_logsSuccessAndLeaksNoPersonalDataOrCredentials() {
        auditor.authenticationSucceeded(new UniqueId(42L), 84L);

        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("event=telegram.auth", "outcome=SUCCESS", "sub=" + new UniqueId(84L));
        // The auditor is given only stable ids by contract — assert no raw Telegram data / credentials leak.
        assertThat(message).doesNotContain("first_name", "init_data", "initData", "token", "hash");
    }
}

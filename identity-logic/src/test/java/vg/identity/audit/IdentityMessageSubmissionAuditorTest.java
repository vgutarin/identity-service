package vg.identity.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMessageSubmissionAuditorTest {
    private IdentityMessageSubmissionAuditor auditor;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        auditor = new IdentityMessageSubmissionAuditor();
        logger = (Logger) LoggerFactory.getLogger("vg.identity.security.audit");
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptedAfterCommit_whenTransactionCommits_emitsIdentifiersAndNewOrReplayOutcomeOnly(boolean replayed) {
        auditor.acceptedAfterCommit(new UniqueId(1L), new UniqueId(2L), new UniqueId(3L), new UniqueId(4L), replayed);
        assertThat(appender.list).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("event=message.user.submit outcome="
                    + (replayed ? "REPLAYED" : "CREATED") + " actorUserUniqueId=" + new UniqueId(1L)
                    + " applicationUniqueId=" + new UniqueId(2L) + " userUniqueId=" + new UniqueId(3L)
                    + " messageUniqueId=" + new UniqueId(4L));
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void acceptedAfterCommit_whenTransactionRollsBack_doesNotEmitAcceptance() {
        auditor.acceptedAfterCommit(new UniqueId(1L), new UniqueId(2L), new UniqueId(3L), new UniqueId(4L), false);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(appender.list).isEmpty();
    }
}

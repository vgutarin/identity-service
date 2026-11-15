package vg.identity.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vg.unique.id.model.UniqueId;

/** Records accepted administration submissions only after the enclosing transaction commits. */
@Component
@Slf4j(topic = "vg.identity.security.audit")
public class IdentityMessageSubmissionAuditor {
    public void acceptedAfterCommit(UniqueId actorUniqueId, UniqueId applicationUniqueId, UniqueId userUniqueId,
                                    UniqueId messageUniqueId, boolean replayed) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Only typed internal identifiers and a fixed outcome enter this security event.
                log.info("event=message.user.submit outcome={} actorUserUniqueId={} applicationUniqueId={} userUniqueId={} messageUniqueId={}",
                        replayed ? "REPLAYED" : "CREATED", actorUniqueId, applicationUniqueId, userUniqueId, messageUniqueId);
            }
        });
    }
}

package vg.identity.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.MessageContentConverter;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageSnapshot;
import vg.identity.model.IdentityMessageStatus;
import vg.unique.id.model.UniqueId;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** MySQL insert-or-get in the caller's transaction, without a locking read of an absent key. */
@Repository
@RequiredArgsConstructor
public class IdentityMessageSubmissionRepository {
    private final EntityManager entityManager;
    private final MessageContentConverter contentConverter;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public IdentityMessageSnapshot insertOrGet(UniqueId candidateId, UniqueId applicationId, UniqueId userId,
                                               IdentityApplicationMessageRequest request) {
        // Preserve pending caller writes, including an earlier submission in this transaction.
        entityManager.flush();
        var key = request.idempotencyKey();
        var keyBytes = ByteBuffer.allocate(16).putLong(key.getMostSignificantBits()).putLong(key.getLeastSignificantBits()).array();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        entityManager.createNativeQuery("""
                INSERT INTO identity_message
                    (unique_id, version, application_unique_id, user_unique_id, idempotency_key,
                     content, status, created_at, updated_at)
                VALUES (:id, 0, :application, :user, :key, :content, :status, :now, :now)
                ON DUPLICATE KEY UPDATE unique_id = identity_message.unique_id
                """)
                .setParameter("id", candidateId.getLongValue())
                .setParameter("application", applicationId.getLongValue())
                .setParameter("user", userId.getLongValue())
                .setParameter("key", keyBytes)
                .setParameter("content", contentConverter.convertToDatabaseColumn(request.content()))
                .setParameter("status", IdentityMessageStatus.QUEUED.ordinal())
                .setParameter("now", now)
                .executeUpdate();

        // The key now exists. A current scalar read avoids both missing-key gap locks and stale JPA entities.
        var row = (Object[]) entityManager.createNativeQuery("""
                SELECT unique_id, user_unique_id, content, status, failure_code, created_at, updated_at
                FROM identity_message
                WHERE application_unique_id = :application AND idempotency_key = :key
                FOR UPDATE
                """)
                .setParameter("application", applicationId.getLongValue())
                .setParameter("key", keyBytes)
                .unwrap(NativeQuery.class)
                .addScalar("unique_id", Long.class)
                .addScalar("user_unique_id", Long.class)
                .addScalar("content", byte[].class)
                .addScalar("status", Integer.class)
                .addScalar("failure_code", Integer.class)
                .addScalar("created_at", Instant.class)
                .addScalar("updated_at", Instant.class)
                .getSingleResult();
        return IdentityMessageSnapshot.builder()
                .message(IdentityApplicationMessage.builder()
                        .messageUniqueId(new UniqueId((Long) row[0])).userUniqueId(new UniqueId((Long) row[1]))
                        .status(IdentityMessageStatus.values()[(Integer) row[3]])
                        .failureCode(row[4] == null ? null : IdentityMessageFailureCode.values()[(Integer) row[4]])
                        .createdAt((Instant) row[5]).updatedAt((Instant) row[6]).build())
                .content(contentConverter.convertToEntityAttribute((byte[]) row[2])).build();
    }
}

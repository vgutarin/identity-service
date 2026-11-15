package vg.identity.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.MessageContent;
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static vg.utils.HibernateHelper.effectiveClass;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "identity_message", uniqueConstraints = @UniqueConstraint(
        name = "unq_identity_message_application_key", columnNames = {"application_unique_id", "idempotency_key"}))
@EntityListeners(AuditingEntityListener.class)
public class IdentityMessageEntity implements UniqueIdEntity {
    @Id
    private Long uniqueId;
    @Version
    private int version;
    @Column(nullable = false, updatable = false)
    private Long applicationUniqueId;
    @Column(nullable = false, updatable = false)
    private Long userUniqueId;
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;
    @Convert(converter = MessageContentConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "MEDIUMBLOB")
    private MessageContent content;
    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private IdentityMessageStatus status;
    @Enumerated(EnumType.ORDINAL)
    private IdentityMessageFailureCode failureCode;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || effectiveClass(this) != effectiveClass(other)) return false;
        var that = (IdentityMessageEntity) other;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

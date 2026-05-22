package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.CommunicationChannelType;
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Table(
        name = "identity_user_communication_channel",
        uniqueConstraints = @UniqueConstraint(
                name = "unq_identity_user_communication_channel_user_id",
                columnNames = {"channel_type", "channel_user_id_hash"}
        )
)
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityUserCommunicationChannelEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "identity_user_unique_id")
    private IdentityUserEntity identityUser;

    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private CommunicationChannelType channelType;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB", nullable = false, updatable = false)
    private String channelUserId;

    @Column(unique = true, nullable = false, updatable = false, columnDefinition = "BINARY(32)")
    private byte[] channelUserIdHash;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB", nullable = false, updatable = false)
    private String data;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityUserCommunicationChannelEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

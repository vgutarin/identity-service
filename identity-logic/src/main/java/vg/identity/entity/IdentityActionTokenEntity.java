package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityPrincipalType;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_action_token")
@Entity
public class IdentityActionTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "secret_hash", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
    private byte[] secretHash;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false, updatable = false)
    private IdentityActionType actionType;

    @Enumerated(EnumType.ORDINAL)
    @Column(updatable = false)
    private IdentityPrincipalType principalType;

    @ManyToOne
    @JoinColumn(name = "principal_unique_id", updatable = false)
    private IdentityPrincipalEntity principal;

    @ManyToOne
    @JoinColumn(name = "identity_user_channel_unique_id", updatable = false)
    private IdentityUserChannelEntity identityUserChannel;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB", updatable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expireAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityActionTokenEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

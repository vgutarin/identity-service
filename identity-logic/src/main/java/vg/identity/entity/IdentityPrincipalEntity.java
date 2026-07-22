package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_principal")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityPrincipalEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String displayName;

    /**
     * The globally unique identifier a principal is known by for authentication and lookup
     * (the user's username, the application's uri). Encrypted at rest; {@link #nameHash} carries the
     * blind index used to enforce uniqueness and to resolve principals by name.
     */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String name;

    @Column(unique = true, columnDefinition = "BINARY(32)")
    private byte[] nameHash;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private IdentityPrincipalStatus status;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private IdentityPrincipalType type;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityPrincipalEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.IdentityUserSystemRole;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_user_system_role")
@Entity
@IdClass(IdentityUserSystemRoleEntityId.class)
@EntityListeners(AuditingEntityListener.class)
public class IdentityUserSystemRoleEntity {

    @Id
    @Column(name = "identity_principal_unique_id", nullable = false)
    private Long identityPrincipalUniqueId;

    @Id
    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private IdentityUserSystemRole role;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityUserSystemRoleEntity) o;
        return identityPrincipalUniqueId != null && Objects.equals(identityPrincipalUniqueId, that.identityPrincipalUniqueId)
                && role != null && Objects.equals(role, that.role);
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

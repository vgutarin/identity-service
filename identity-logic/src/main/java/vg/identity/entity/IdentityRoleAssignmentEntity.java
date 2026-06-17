package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.access.AccessScope;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * Represents an assigned role for a principal in a specific access scope.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_role_assignment")
@Entity
@IdClass(IdentityRoleAssignmentEntityId.class)
@EntityListeners(AuditingEntityListener.class)
public class IdentityRoleAssignmentEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "principal_unique_id", nullable = false)
    private IdentityPrincipalEntity principal;

    @Id
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private IdentityRoleEntity role;

    @Id
    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private AccessScope accessScope;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityRoleAssignmentEntity) o;
        return principal != null && Objects.equals(principal, that.principal)
                && role != null && Objects.equals(role, that.role)
                && accessScope != null && Objects.equals(accessScope, that.accessScope);
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

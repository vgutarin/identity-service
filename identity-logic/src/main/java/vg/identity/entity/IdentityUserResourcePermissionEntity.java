package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * Represents a user's permission for a concrete resource.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_user_resource_permission")
@Entity
@IdClass(IdentityUserResourcePermissionEntityId.class)
@EntityListeners(AuditingEntityListener.class)
public class IdentityUserResourcePermissionEntity {

    @Id
    @Column(nullable = false)
    private Long userUniqueId;

    @Id
    @Column(nullable = false)
    private Long resourceUniqueId;

    @Id
    @Column(nullable = false)
    private Long permissionId;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityUserResourcePermissionEntity) o;
        return userUniqueId != null && Objects.equals(userUniqueId, that.userUniqueId)
                && resourceUniqueId != null && Objects.equals(resourceUniqueId, that.resourceUniqueId)
                && permissionId != null && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

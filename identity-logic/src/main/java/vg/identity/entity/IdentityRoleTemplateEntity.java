package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * Represents the permission string
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_role_template")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityRoleTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "identity_role_template_permission",
            joinColumns = @JoinColumn(name = "role_template_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<IdentityPermissionEntity> permissions = new HashSet<>();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityRoleTemplateEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

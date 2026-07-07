package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * Represents the workspace.
 * Withing the workspace there are multiple applications that can be registered.
 * Users can be assigned to multiple workspaces.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_workspace")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityWorkspaceEntity implements UniqueIdEntity {

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
    private String name;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String description;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "identity_workspace_user",
            joinColumns = @JoinColumn(name = "workspace_unique_id"),
            inverseJoinColumns = @JoinColumn(name = "user_unique_id")
    )
    private Set<IdentityUserEntity> users = new HashSet<>();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityWorkspaceEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

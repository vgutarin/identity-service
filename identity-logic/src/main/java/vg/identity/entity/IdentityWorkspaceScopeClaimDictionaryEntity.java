package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * A workspace-local dictionary of interned scope/claim strings. Each distinct value is stored once per
 * workspace, encrypted at rest, and referenced by {@link IdentityApplicationUserClaimEntity}. A row carries
 * no scope-vs-claim marker: its role is decided by which foreign key of a grant points at it.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(
        name = "identity_workspace_scope_claim_dictionary",
        uniqueConstraints = @UniqueConstraint(
                name = "unq_workspace_scope_claim_dictionary",
                columnNames = {"workspace_unique_id", "name_blind_index"}
        )
)
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityWorkspaceScopeClaimDictionaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workspace_unique_id", nullable = false, updatable = false)
    private IdentityWorkspaceEntity workspace;

    /** The interned value, encrypted at rest; recoverable only in memory. */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "name", columnDefinition = "BLOB", nullable = false)
    private String name;

    /** Deterministic blind index over the value, enforcing per-workspace uniqueness and enabling lookup. */
    @Column(name = "name_blind_index", columnDefinition = "BINARY(32)", nullable = false)
    private byte[] nameBlindIndex;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityWorkspaceScopeClaimDictionaryEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

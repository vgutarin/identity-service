package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_application")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityApplicationEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @OneToOne(optional = false)
    @JoinColumn(name = "unique_id", insertable = false, updatable = false)
    private IdentityPrincipalEntity principal;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workspace_unique_id", nullable = false, updatable = false)
    private IdentityWorkspaceEntity workspace;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String name;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(nullable = false, columnDefinition = "BLOB")
    private String uri;

    @Column(unique = true, columnDefinition = "BINARY(32)")
    private byte[] uriHash;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB")
    private String data;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityApplicationEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

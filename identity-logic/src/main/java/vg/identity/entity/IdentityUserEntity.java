package vg.identity.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_user")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityUserEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @OneToOne(optional = false)
    @JoinColumn(name = "unique_id", insertable = false, updatable = false)
    private IdentityPrincipalEntity principal;

    @Version
    private int version;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String username;

    @Column(unique = true, columnDefinition = "BINARY(32)")
    private byte[] usernameHash;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "BLOB")
    private String password;

    private Instant consentToKeepPersonalDataAt;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB")
    private String data;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Builder.Default
    @ManyToMany(mappedBy = "users")
    private Set<IdentityWorkspaceEntity> workspaces = new HashSet<>();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityUserEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}

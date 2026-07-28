package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * Membership of an identity user in one identity application: the user authenticated for the application at
 * least once. Provisioned on first authentication; {@code lastAuthenticatedAt} is bumped on each subsequent one.
 * Lets workspace administrators enumerate an application's users to manage their claims.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_application_user")
@Entity
@IdClass(IdentityApplicationUserEntityId.class)
@EntityListeners(AuditingEntityListener.class)
public class IdentityApplicationUserEntity {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "application_unique_id", nullable = false)
    private IdentityApplicationEntity application;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "identity_user_unique_id", nullable = false)
    private IdentityUserEntity identityUser;

    @Column(name = "last_authenticated_at", nullable = false)
    private Instant lastAuthenticatedAt;

    /** Free-form application-supplied metadata about this membership, encrypted at rest. */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB")
    private String metadata;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;
}

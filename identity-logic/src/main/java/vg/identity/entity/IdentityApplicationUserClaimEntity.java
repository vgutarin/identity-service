package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.time.Instant;

/** A claim granted to an identity user for one identity application. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_application_user_claim")
@Entity
@IdClass(IdentityApplicationUserClaimEntityId.class)
@EntityListeners(AuditingEntityListener.class)
public class IdentityApplicationUserClaimEntity {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "application_unique_id", nullable = false)
    private IdentityApplicationEntity application;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "identity_user_unique_id", nullable = false)
    private IdentityUserEntity identityUser;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "scope_id", nullable = false)
    private IdentityWorkspaceScopeClaimDictionaryEntity scope;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private IdentityWorkspaceScopeClaimDictionaryEntity claim;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;
}

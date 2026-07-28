package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.entity.IdentityApplicationUserClaimEntityId;

import java.util.List;

public interface IdentityApplicationUserClaimRepository extends JpaRepository<IdentityApplicationUserClaimEntity, IdentityApplicationUserClaimEntityId> {

    /**
     * All claim grants for one user within one application, across every scope. The {@code scope}/{@code claim}
     * dictionary entries are eagerly loaded ({@code @ManyToOne}) and their names are decrypted by the JPA
     * converter on read, so callers can group them into a {@code scope -> claims} map in memory.
     */
    List<IdentityApplicationUserClaimEntity> findByApplication_UniqueIdAndIdentityUser_UniqueId(
            Long applicationUniqueId, Long identityUserUniqueId);
}

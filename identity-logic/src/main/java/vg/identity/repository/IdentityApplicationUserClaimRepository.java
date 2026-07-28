package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.entity.IdentityApplicationUserClaimEntityId;

import java.util.Set;

public interface IdentityApplicationUserClaimRepository extends JpaRepository<IdentityApplicationUserClaimEntity, IdentityApplicationUserClaimEntityId> {

    @Query("""
            select entry.claim.name
            from IdentityApplicationUserClaimEntity entry
            where entry.application.uniqueId = :applicationUniqueId
              and entry.identityUser.uniqueId = :identityUserUniqueId
              and entry.scope.nameBlindIndex = :scopeBlindIndex
            """)
    Set<String> findClaimNamesByScopeBlindIndex(Long applicationUniqueId, Long identityUserUniqueId, byte[] scopeBlindIndex);
}

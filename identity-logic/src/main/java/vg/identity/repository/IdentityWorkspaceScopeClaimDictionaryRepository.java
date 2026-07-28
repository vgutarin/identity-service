package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityWorkspaceScopeClaimDictionaryEntity;

import java.util.Optional;

public interface IdentityWorkspaceScopeClaimDictionaryRepository
        extends JpaRepository<IdentityWorkspaceScopeClaimDictionaryEntity, Integer> {

    Optional<IdentityWorkspaceScopeClaimDictionaryEntity> findByWorkspace_UniqueIdAndNameBlindIndex(
            Long workspaceUniqueId, byte[] nameBlindIndex);
}

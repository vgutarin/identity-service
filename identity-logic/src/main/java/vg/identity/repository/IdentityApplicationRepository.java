package vg.identity.repository;

import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityApplicationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityApplicationRepository extends UniqueIdJpaRepository<IdentityApplicationEntity> {
    Optional<IdentityApplicationEntity> findByPrincipal_NameHash(byte[] nameHash);

    List<IdentityApplicationEntity> findByWorkspaceUniqueId(Long workspaceUniqueId);

    @Query("""
            select application
            from IdentityApplicationEntity application
            join fetch application.principal
            """)
    List<IdentityApplicationEntity> findAllWithPrincipal();
}

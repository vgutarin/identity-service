package vg.identity.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vg.identity.entity.IdentityApplicationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityApplicationRepository extends UniqueIdJpaRepository<IdentityApplicationEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from IdentityApplicationEntity application where application.uniqueId = :uniqueId")
    Optional<IdentityApplicationEntity> lockForMessageSubmission(@Param("uniqueId") Long uniqueId);

    Optional<IdentityApplicationEntity> findByPrincipal_NameHash(byte[] nameHash);

    List<IdentityApplicationEntity> findByWorkspaceUniqueId(Long workspaceUniqueId);

    @Query("""
            select application
            from IdentityApplicationEntity application
            join fetch application.principal
            """)
    List<IdentityApplicationEntity> findAllWithPrincipal();
}

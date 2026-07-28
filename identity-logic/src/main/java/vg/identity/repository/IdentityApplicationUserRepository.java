package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityApplicationUserEntity;
import vg.identity.entity.IdentityApplicationUserEntityId;

import java.util.List;

public interface IdentityApplicationUserRepository
        extends JpaRepository<IdentityApplicationUserEntity, IdentityApplicationUserEntityId> {

    List<IdentityApplicationUserEntity> findByApplication_UniqueIdOrderByLastAuthenticatedAtDesc(Long applicationUniqueId);
}

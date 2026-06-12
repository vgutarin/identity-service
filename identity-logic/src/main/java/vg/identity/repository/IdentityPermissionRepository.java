package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityPermissionEntity;

import java.util.Optional;

public interface IdentityPermissionRepository extends JpaRepository<IdentityPermissionEntity, Long> {
    Optional<IdentityPermissionEntity> findByName(String name);
}

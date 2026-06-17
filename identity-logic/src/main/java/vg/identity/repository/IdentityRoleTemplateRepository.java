package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityRoleTemplateEntity;

import java.util.Optional;

public interface IdentityRoleTemplateRepository extends JpaRepository<IdentityRoleTemplateEntity, Long> {
    Optional<IdentityRoleTemplateEntity> findByName(String name);
}

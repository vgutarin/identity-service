package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityRoleAssignmentEntity;
import vg.identity.entity.IdentityRoleAssignmentEntityId;
import vg.identity.entity.IdentityRoleEntity;

import java.util.List;

public interface IdentityRoleAssignmentRepository extends JpaRepository<IdentityRoleAssignmentEntity, IdentityRoleAssignmentEntityId> {
    List<IdentityRoleAssignmentEntity> findAllByPrincipal(IdentityPrincipalEntity principal);

    List<IdentityRoleAssignmentEntity> findAllByRole(IdentityRoleEntity role);
}

package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityApiKeyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityApiKeyRepository extends JpaRepository<IdentityApiKeyEntity, UUID> {

    List<IdentityApiKeyEntity> findAllByPrincipalUniqueIdOrderByCreatedAtDesc(Long principalUniqueId);

    Optional<IdentityApiKeyEntity> findByIdAndPrincipalUniqueId(UUID id, Long principalUniqueId);
}

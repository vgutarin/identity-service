package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.model.IdentityCommandStatus;

import java.util.Optional;

public interface IdentityCommandRepository extends JpaRepository<IdentityCommandEntity, Long> {
    Optional<IdentityCommandEntity> findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(
            Long id,
            IdentityCommandStatus commandStatus
    );
}

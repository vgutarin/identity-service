package vg.identity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.identity.entity.IdentityMessageEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.Optional;

public interface IdentityMessageRepository extends UniqueIdJpaRepository<IdentityMessageEntity> {
    Page<IdentityMessageEntity> findByApplicationUniqueId(Long applicationUniqueId, Pageable pageable);

    Optional<IdentityMessageEntity> findByUniqueIdAndApplicationUniqueId(Long uniqueId, Long applicationUniqueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from IdentityMessageEntity message where message.uniqueId = :uniqueId")
    Optional<IdentityMessageEntity> findForUpdate(@Param("uniqueId") Long uniqueId);

}

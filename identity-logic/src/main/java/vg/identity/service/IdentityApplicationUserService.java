package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.entity.IdentityApplicationUserEntity;
import vg.identity.entity.IdentityApplicationUserEntityId;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.util.List;

/**
 * Owns the {@code identity_application_user} membership: which identity users have authenticated for an
 * application. Membership is recorded during the application-mediated authentication flow and read by
 * workspace administrators to enumerate an application's users.
 */
@Service
@RequiredArgsConstructor
public class IdentityApplicationUserService {
    private final IdentityApplicationUserRepository membershipRepository;
    private final IdentityApplicationRepository applicationRepository;
    private final IdentityUserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Records that a user authenticated for an application: provisions the membership row on first sight and
     * bumps {@code lastAuthenticatedAt} thereafter. Race-safe — a concurrent first-authentication that loses the
     * insert collides on the primary key and is recovered as a timestamp bump. Runs in its own transaction so a
     * bookkeeping collision never poisons the caller's authentication flow.
     */
    void recordAuthentication(UniqueId applicationUniqueId, long identityUserUniqueId) {
        var id = membershipId(applicationUniqueId.getLongValue(), identityUserUniqueId);
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> upsert(id, applicationUniqueId.getLongValue(), identityUserUniqueId));
        } catch (DataIntegrityViolationException concurrentInsert) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> bump(id));
        }
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityApplicationUser> findUsers(UniqueId applicationUniqueId) {
        return membershipRepository.findByApplication_UniqueIdOrderByLastAuthenticatedAtDesc(applicationUniqueId.getLongValue())
                .stream()
                .map(membership -> new IdentityApplicationUser(
                        new UniqueId(membership.getIdentityUser().getUniqueId()),
                        membership.getCreatedAt(),
                        membership.getLastAuthenticatedAt()
                ))
                .toList();
    }

    private void upsert(IdentityApplicationUserEntityId id, long applicationUniqueId, long identityUserUniqueId) {
        var existing = membershipRepository.findById(id).orElse(null);
        if (existing != null) {
            existing.setLastAuthenticatedAt(clock.instant());
            return;
        }
        membershipRepository.save(IdentityApplicationUserEntity.builder()
                .application(applicationRepository.getReferenceById(applicationUniqueId))
                .identityUser(userRepository.getReferenceById(identityUserUniqueId))
                .lastAuthenticatedAt(clock.instant())
                .build());
    }

    private void bump(IdentityApplicationUserEntityId id) {
        membershipRepository.findById(id).ifPresent(membership -> membership.setLastAuthenticatedAt(clock.instant()));
    }

    private static IdentityApplicationUserEntityId membershipId(long applicationUniqueId, long identityUserUniqueId) {
        var id = new IdentityApplicationUserEntityId();
        id.setApplication(applicationUniqueId);
        id.setIdentityUser(identityUserUniqueId);
        return id;
    }
}

package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.entity.IdentityApplicationUserClaimEntityId;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.entity.IdentityWorkspaceScopeClaimDictionaryEntity;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserClaimRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityWorkspaceScopeClaimDictionaryRepository;
import vg.unique.id.model.UniqueId;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Internal management and lookup service for application-local user claims.
 * Platform workspace administrators, never an authenticated application, manage these claims.
 *
 * <p>Scope and claim strings are interned per workspace in {@link IdentityWorkspaceScopeClaimDictionaryEntity}
 * (encrypted at rest) and referenced by id from each grant, so the same string is stored once regardless of
 * how many users or applications share it.</p>
 */
@Service
@RequiredArgsConstructor
public class IdentityApplicationClaimService {
    private final IdentityApplicationRepository applicationRepository;
    private final IdentityApplicationUserClaimRepository claimRepository;
    private final IdentityUserRepository userRepository;
    private final IdentityWorkspaceScopeClaimDictionaryRepository dictionaryRepository;
    private final EncryptionService encryptionService;

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.CLAIM_CREATE + "')")
    @Transactional
    public void grantClaim(UniqueId applicationUniqueId, UniqueId identityUserUniqueId, String scope, String claim) {
        var normalizedScope = normalizeScope(scope);
        var normalizedClaim = normalizeClaim(normalizedScope, claim);
        var application = applicationRepository.findById(applicationUniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);
        var workspace = application.getWorkspace();
        var scopeEntry = getOrCreateDictionaryEntry(workspace, normalizedScope);
        var claimEntry = getOrCreateDictionaryEntry(workspace, normalizedClaim);
        var id = claimId(applicationUniqueId, identityUserUniqueId, scopeEntry.getId(), claimEntry.getId());
        if (claimRepository.existsById(id)) {
            return;
        }
        var user = userRepository.findById(identityUserUniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);
        claimRepository.save(IdentityApplicationUserClaimEntity.builder()
                .application(application)
                .identityUser(user)
                .scope(scopeEntry)
                .claim(claimEntry)
                .build());
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.CLAIM_DELETE + "')")
    @Transactional
    public void revokeClaim(UniqueId applicationUniqueId, UniqueId identityUserUniqueId, String scope, String claim) {
        var normalizedScope = normalizeScope(scope);
        var normalizedClaim = normalizeClaim(normalizedScope, claim);
        var application = applicationRepository.findById(applicationUniqueId.getLongValue()).orElse(null);
        if (application == null) {
            return;
        }
        var workspaceUniqueId = application.getWorkspace().getUniqueId();
        var scopeEntry = findDictionaryEntry(workspaceUniqueId, normalizedScope);
        var claimEntry = findDictionaryEntry(workspaceUniqueId, normalizedClaim);
        if (scopeEntry == null || claimEntry == null) {
            return;
        }

        claimRepository.deleteById(
                claimId(applicationUniqueId, identityUserUniqueId, scopeEntry.getId(), claimEntry.getId())
        );

    }

    @Transactional(readOnly = true)
    Map<String, Set<String>> findClaimsByScope(UniqueId applicationUniqueId, UniqueId identityUserUniqueId) {
        var permissions = claimRepository.findClaimNamesByScopeBlindIndex(
                applicationUniqueId.getLongValue(),
                identityUserUniqueId.getLongValue(),
                encryptionService.hashCaseSensitive(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE)
        );
        if (permissions.isEmpty()) {
            return Map.of();
        }
        return Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.copyOf(permissions));
    }


    /**
     * Resolves the dictionary entry for {@code value} within the workspace, creating it when absent. A rare
     * concurrent creation of the same value collides on the unique blind index and surfaces as a
     * {@link org.springframework.dao.DataIntegrityViolationException}; this administrative operation lets it
     * propagate for the caller to retry rather than recovering mid-transaction.
     */
    private IdentityWorkspaceScopeClaimDictionaryEntity getOrCreateDictionaryEntry(
            IdentityWorkspaceEntity workspace, String value) {
        var existing = findDictionaryEntry(workspace.getUniqueId(), value);
        if (existing != null) {
            return existing;
        }
        return dictionaryRepository.save(IdentityWorkspaceScopeClaimDictionaryEntity.builder()
                .workspace(workspace)
                .name(value)
                .nameBlindIndex(encryptionService.hashCaseSensitive(value))
                .build());
    }

    private IdentityWorkspaceScopeClaimDictionaryEntity findDictionaryEntry(Long workspaceUniqueId, String value) {
        return dictionaryRepository
                .findByWorkspace_UniqueIdAndNameBlindIndex(workspaceUniqueId, encryptionService.hashCaseSensitive(value))
                .orElse(null);
    }

    private static IdentityApplicationUserClaimEntityId claimId(
            UniqueId applicationUniqueId,
            UniqueId identityUserUniqueId,
            Integer scopeId,
            Integer claimId
    ) {
        var id = new IdentityApplicationUserClaimEntityId();
        id.setApplication(applicationUniqueId.getLongValue());
        id.setIdentityUser(identityUserUniqueId.getLongValue());
        id.setScope(scopeId);
        id.setClaim(claimId);
        return id;
    }

    private static String normalizeScope(String scope) {
        if (!IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE.equals(scope)) {
            throw new IllegalArgumentException("Unsupported application claim scope: " + scope);
        }
        return scope;
    }

    private static String normalizeClaim(String scope, String claim) {
        normalizeScope(scope);
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("Application permission claim must not be blank");
        }
        return claim.trim().toLowerCase(Locale.ROOT);
    }
}

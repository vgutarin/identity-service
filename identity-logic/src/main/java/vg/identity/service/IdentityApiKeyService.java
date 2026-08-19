package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vg.identity.entity.IdentityApiKeyEntity;
import vg.identity.model.IdentityApiKey;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IssuedIdentityApiKey;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityApiKeyRepository;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and authenticates opaque API keys. Raw key values are never persisted.
 */
@RequiredArgsConstructor
@Service
public class IdentityApiKeyService {
    private static final char ID_SECRET_SEPARATOR = '.';
    public static final int MAX_LABEL_LENGTH = 256;

    private final IdentityApiKeyRepository apiKeyRepository;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityApplicationRepository applicationRepository;
    private final Clock clock;

    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')")
    public IssuedIdentityApiKey issueForApplication(UniqueId applicationUniqueId, String label, Instant expiresAt) {
        validateIssueRequest(label, expiresAt);
        assertApplicationExists(applicationUniqueId);

        var rawSecret = OpaqueKey.newSecret();
        var id = UUID.randomUUID();
        var now = clock.instant();
        var entity = IdentityApiKeyEntity.builder()
                .id(id)
                .principal(principalRepository.getReferenceById(applicationUniqueId.getLongValue()))
                .label(label.strip())
                .secretHash(OpaqueKey.sha256(rawSecret))
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        apiKeyRepository.save(entity);
        return new IssuedIdentityApiKey(toModel(entity), format(id, rawSecret));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')")
    public List<IdentityApiKey> findForApplication(UniqueId applicationUniqueId) {
        assertApplicationExists(applicationUniqueId);
        return apiKeyRepository.findAllByPrincipalUniqueIdOrderByCreatedAtDesc(applicationUniqueId.getLongValue()).stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')")
    public void revokeForApplication(UniqueId applicationUniqueId, UUID apiKeyId) {
        assertApplicationExists(applicationUniqueId);
        var apiKey = apiKeyRepository.findByIdAndPrincipalUniqueId(apiKeyId, applicationUniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);
        if (apiKey.getRevokedAt() == null) {
            apiKey.setRevokedAt(clock.instant());
            apiKeyRepository.save(apiKey);
        }
    }

    /**
     * Resolves an active API key to its application principal. Invalid credentials deliberately produce no detail.
     */
    @Transactional(readOnly = true)
    public Optional<IdentityApiKeyPrincipal> authenticate(String value) {
        return parse(value)
                .flatMap(candidate -> apiKeyRepository.findById(candidate.id())
                        .filter(apiKey -> OpaqueKey.secretMatches(apiKey.getSecretHash(), candidate.secret()))
                        .filter(apiKey -> isActive(apiKey, clock.instant()))
                        .filter(apiKey -> applicationRepository.existsById(apiKey.getPrincipal().getUniqueId()))
                        .map(apiKey -> new IdentityApiKeyPrincipal(
                                new UniqueId(apiKey.getPrincipal().getUniqueId()),
                                apiKey.getPrincipal().getName()
                        )));
    }

    /**
     * Extracts the key id (the UUID portion) from a raw key value for audit logging, without exposing the
     * secret. Returns empty when the value is absent or not in the expected {@code id.secret} form. The
     * returned id is not a credential on its own.
     */
    public Optional<String> extractKeyId(String value) {
        return parse(value).map(candidate -> candidate.id().toString());
    }

    private void assertApplicationExists(UniqueId applicationUniqueId) {
        if (applicationUniqueId == null || !applicationRepository.existsById(applicationUniqueId.getLongValue())) {
            throw new EntityNotFoundException("exception.application.notFound");
        }
    }

    private void validateIssueRequest(String label, Instant expiresAt) {
        if (!StringUtils.hasText(label) || label.strip().length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("API key label must contain at most " + MAX_LABEL_LENGTH + " characters");
        }
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("API key expiry must be in the future");
        }
    }

    private boolean isActive(IdentityApiKeyEntity apiKey, Instant now) {
        var principal = apiKey.getPrincipal();
        return apiKey.getRevokedAt() == null
                && apiKey.getExpiresAt().isAfter(now)
                && principal.getStatus() == IdentityPrincipalStatus.ACTIVE
                && principal.getType() == IdentityPrincipalType.APPLICATION;
    }

    private IdentityApiKey toModel(IdentityApiKeyEntity entity) {
        return new IdentityApiKey(
                entity.getId(),
                entity.getLabel(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }

    private Optional<ApiKeyCandidate> parse(String value) {
        return OpaqueKey.parse(value, ID_SECRET_SEPARATOR).flatMap(parsed -> {
            try {
                return Optional.of(new ApiKeyCandidate(UUID.fromString(parsed.id()), parsed.secret()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    private String format(UUID id, byte[] rawSecret) {
        return OpaqueKey.format(id.toString(), ID_SECRET_SEPARATOR, rawSecret);
    }

    private record ApiKeyCandidate(UUID id, byte[] secret) {
    }
}

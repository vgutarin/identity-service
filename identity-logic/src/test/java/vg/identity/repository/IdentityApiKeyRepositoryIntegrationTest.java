package vg.identity.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApiKeyEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.unique.id.service.UniqueIdService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApiKeyRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdentityApiKeyRepository apiKeyRepository;
    @Autowired
    private UniqueIdService uniqueIdService;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findById_whenKeyExists_loadsItsApplicationPrincipal() {
        var principal = principalRepository.saveWithNewUniqueId(
                IdentityPrincipalEntity.builder()
                        .name("https://example.test/application")
                        .nameHash(new byte[32])
                        .displayName("Application")
                        .status(IdentityPrincipalStatus.ACTIVE)
                        .type(IdentityPrincipalType.APPLICATION)
                        .build(),
                uniqueIdService
        );
        var id = UUID.randomUUID();
        apiKeyRepository.save(IdentityApiKeyEntity.builder()
                .id(id)
                .principal(principal)
                .label("a".repeat(256))
                .secretHash(new byte[32])
                .createdAt(Instant.parse("2026-07-27T12:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T12:00:00Z"))
                .build());
        apiKeyRepository.flush();
        entityManager.clear();

        var result = apiKeyRepository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getPrincipal().getUniqueId()).isEqualTo(principal.getUniqueId());
        assertThat(result.orElseThrow().getLabel()).hasSize(256);
        assertThat(result.orElseThrow().getSecretHash()).hasSize(32);
    }
}

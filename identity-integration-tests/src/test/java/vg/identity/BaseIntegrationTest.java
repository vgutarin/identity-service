package vg.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vg.identity.entity.IdentityApiKeyEntity;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.repository.IdentityApiKeyRepository;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.service.EncryptionService;
import vg.test.containers.starters.Mysql8ContainerStarter;
import vg.unique.id.service.UniqueIdService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static vg.test.TestHelper.nextString;

@SpringBootTest(classes = IdentityApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest implements Mysql8ContainerStarter {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TestApiKey TEST_API_KEY = TestApiKey.create();

    @Autowired
    protected IdentityApiKeyRepository apiKeyRepository;
    @Autowired
    protected IdentityApplicationRepository applicationRepository;
    @Autowired
    protected IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    protected IdentityPrincipalRepository principalRepository;
    @Autowired
    protected UniqueIdService uniqueIdService;
    @Autowired
    protected EncryptionService encryptionService;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @PersistenceContext
    protected EntityManager entityManager;

    @AfterEach
    protected void cleanUp() {
        apiKeyRepository.deleteAll();
        applicationRepository.deleteAll();
        workspaceRepository.deleteAll();
        principalRepository.deleteAll();
    }

    @DynamicPropertySource
    static void identityRestClientProperties(DynamicPropertyRegistry registry) {
        registry.add("vg.identity.rest-client.api-key", TEST_API_KEY::value);
    }

    protected TestApplication createApplicationWithApiKey() {
        var name = "orders-service-" + nextString();
        var uri = "https://orders-" + nextString() + ".example.test";
        var workspace = workspaceRepository.saveWithNewUniqueId(
                IdentityWorkspaceEntity.builder()
                        .name("workspace-" + nextString())
                        .build(),
                uniqueIdService
        );
        var principal = principalRepository.saveWithNewUniqueId(
                IdentityPrincipalEntity.builder()
                        .displayName(name)
                        .name(uri)
                        .nameHash(encryptionService.hashPrincipalName(uri))
                        .status(IdentityPrincipalStatus.ACTIVE)
                        .type(IdentityPrincipalType.APPLICATION)
                        .build(),
                uniqueIdService
        );

        var res = new TransactionTemplate(transactionManager).execute(status -> {
            var application = IdentityApplicationEntity.builder()
                    .uniqueId(principal.getUniqueId())
                    .principal(entityManager.getReference(IdentityPrincipalEntity.class, principal.getUniqueId()))
                    .workspace(entityManager.getReference(IdentityWorkspaceEntity.class, workspace.getUniqueId()))
                    .payload("must-not-be-returned")
                    .build();
            entityManager.persist(application);
            entityManager.flush();
            return new TestApplication(
                    application.getUniqueId(),
                    workspace.getUniqueId(),
                    name,
                    uri
            );
        });
        createApiKeyFor(res.uniqueId());
        return res;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    protected record TestApplication(
            Long uniqueId,
            Long workspaceUniqueId,
            String name,
            String uri
    ) {
    }

    private void createApiKeyFor(Long applicationUniqueId) {
        apiKeyRepository.saveAndFlush(IdentityApiKeyEntity.builder()
                .id(TEST_API_KEY.id())
                .principal(principalRepository.getReferenceById(applicationUniqueId))
                .label("integration-test")
                .secretHash(TEST_API_KEY.secretHash())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());
    }

    private record TestApiKey(UUID id, byte[] rawSecret) {
        static TestApiKey create() {
            var rawSecret = new byte[32];
            SECURE_RANDOM.nextBytes(rawSecret);
            return new TestApiKey(UUID.randomUUID(), rawSecret);
        }

        String value() {
            return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(rawSecret);
        }

        byte[] secretHash() {
            return sha256(rawSecret);
        }
    }
}

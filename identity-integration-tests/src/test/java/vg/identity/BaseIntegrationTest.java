package vg.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vg.identity.entity.IdentityApiKeyEntity;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.entity.IdentityWorkspaceScopeClaimDictionaryEntity;
import vg.identity.model.principal.IdentityInternalPrincipalStatus;
import vg.identity.model.principal.IdentityInternalPrincipalType;
import vg.identity.repository.IdentityApiKeyRepository;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserClaimRepository;
import vg.identity.repository.IdentityApplicationUserRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.repository.IdentityWorkspaceScopeClaimDictionaryRepository;
import vg.identity.app.IdentityApplication;
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

@SpringBootTest(classes = IdentityApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest implements Mysql8ContainerStarter {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TestApiKey TEST_API_KEY = TestApiKey.create();

    @Autowired
    protected IdentityApiKeyRepository apiKeyRepository;
    @Autowired
    protected IdentityApplicationRepository applicationRepository;
    @Autowired
    protected IdentityApplicationUserClaimRepository applicationUserClaimRepository;
    @Autowired
    protected IdentityApplicationUserRepository applicationUserRepository;
    @Autowired
    protected IdentityWorkspaceScopeClaimDictionaryRepository scopeClaimDictionaryRepository;
    @Autowired
    protected IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    protected IdentityPrincipalRepository principalRepository;
    @Autowired
    protected IdentityUserChannelRepository channelRepository;
    @Autowired
    protected IdentityUserRepository userRepository;
    @Autowired
    protected UniqueIdService uniqueIdService;
    @Autowired
    protected EncryptionService encryptionService;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @LocalServerPort
    protected int serverPort;
    @PersistenceContext
    protected EntityManager entityManager;

    @AfterEach
    protected void cleanUp() {
        applicationUserClaimRepository.deleteAll();
        applicationUserRepository.deleteAll();
        scopeClaimDictionaryRepository.deleteAll();
        apiKeyRepository.deleteAll();
        applicationRepository.deleteAll();
        workspaceRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    @DynamicPropertySource
    static void identityRestClientProperties(DynamicPropertyRegistry registry) {
        registry.add("vg.identity.rest-client.api-key", TEST_API_KEY::value);
        registry.add("identity.telegram.webhook.enabled", () -> true);
        registry.add("identity.telegram.webhook.secret", () -> "integration-test-telegram-webhook-secret-must-be-long-enough");
    }

    protected TestApplication createApplicationWithApiKey() {
        return createApplicationWithApiKey("must-not-be-returned");
    }

    protected TestApplication createApplicationWithApiKey(String payload) {
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
                        .status(IdentityInternalPrincipalStatus.ACTIVE)
                        .type(IdentityInternalPrincipalType.APPLICATION)
                        .build(),
                uniqueIdService
        );

        var res = new TransactionTemplate(transactionManager).execute(status -> {
            var application = IdentityApplicationEntity.builder()
                    .uniqueId(principal.getUniqueId())
                    .principal(entityManager.getReference(IdentityPrincipalEntity.class, principal.getUniqueId()))
                    .workspace(entityManager.getReference(IdentityWorkspaceEntity.class, workspace.getUniqueId()))
                    .payload(payload)
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

    protected String restClientApiKey() {
        return TEST_API_KEY.value();
    }

    protected IdentityWorkspaceScopeClaimDictionaryEntity getScopeClaim(Long workspaceUniqueId, String value) {
        var blindIndex = encryptionService.hashCaseSensitive(value);
        return scopeClaimDictionaryRepository
                .findByWorkspace_UniqueIdAndNameBlindIndex(workspaceUniqueId, blindIndex)
                .orElseGet(() -> scopeClaimDictionaryRepository.save(IdentityWorkspaceScopeClaimDictionaryEntity.builder()
                        .workspace(workspaceRepository.getReferenceById(workspaceUniqueId))
                        .name(value)
                        .nameBlindIndex(blindIndex)
                        .build()));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Builds valid Telegram WebApp init data signed with {@code botToken} (shared by REST + conformance tests). */
    protected static String telegramInitData(String botToken, String userJson) throws Exception {
        var parameters = java.util.Map.of(
                "auth_date", String.valueOf(Instant.now().getEpochSecond()),
                "query_id", "integration-test",
                "user", userJson
        );
        var dataCheckString = parameters.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
        var hash = java.util.HexFormat.of().formatHex(hmacSha256(
                hmacSha256("WebAppData".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        botToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                dataCheckString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ));
        return parameters.entrySet().stream()
                .map(entry -> java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                        + "=" + java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&")) + "&hash=" + hash;
    }

    private static byte[] hmacSha256(byte[] key, byte[] value) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
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

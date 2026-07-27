package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityApiKeyEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.repository.IdentityApiKeyRepository;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.unique.id.model.UniqueId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApiKeyServiceTest {
    private static final long APPLICATION_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Mock
    private IdentityApiKeyRepository apiKeyRepository;
    @Mock
    private IdentityPrincipalRepository principalRepository;
    @Mock
    private IdentityApplicationRepository applicationRepository;

    private IdentityApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApiKeyService(
                apiKeyRepository,
                principalRepository,
                applicationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void issueForApplication_whenRequestIsValid_persistsOnlySecretHashAndReturnsRawKeyOnce() throws Exception {
        var applicationId = new UniqueId(APPLICATION_ID);
        var principal = applicationPrincipal();
        var saved = new AtomicReference<IdentityApiKeyEntity>();
        when(applicationRepository.existsById(APPLICATION_ID)).thenReturn(true);
        when(principalRepository.getReferenceById(APPLICATION_ID)).thenReturn(principal);
        when(apiKeyRepository.save(any(IdentityApiKeyEntity.class))).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, IdentityApiKeyEntity.class);
            saved.set(entity);
            return entity;
        });

        var issued = service.issueForApplication(applicationId, "orders-service", NOW.plusSeconds(3600));

        var valueParts = issued.value().split("\\.");
        assertThat(valueParts).hasSize(2);
        assertThat(saved.get().getId().toString()).isEqualTo(valueParts[0]);
        assertThat(saved.get().getSecretHash()).isEqualTo(MessageDigest.getInstance("SHA-256")
                .digest(Base64.getUrlDecoder().decode(valueParts[1])));
        assertThat(issued.apiKey().label()).isEqualTo("orders-service");
        assertThat(issued.toString()).doesNotContain(issued.value());
    }

    @Test
    void issueForApplication_whenExpiryIsNotInFuture_rejectsRequest() {
        var applicationId = new UniqueId(APPLICATION_ID);

        assertThatThrownBy(() -> service.issueForApplication(applicationId, "orders-service", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key expiry must be in the future");
    }

    @Test
    void authenticate_whenKeyIsActiveForApplication_returnsIdentifiableApplicationPrincipal() throws Exception {
        var secret = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        assertThat(secret).hasSize(32);
        var key = IdentityApiKeyEntity.builder()
                .id(java.util.UUID.randomUUID())
                .principal(applicationPrincipal())
                .secretHash(MessageDigest.getInstance("SHA-256").digest(secret))
                .expiresAt(NOW.plusSeconds(3600))
                .build();
        var rawValue = key.getId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));
        when(applicationRepository.existsById(APPLICATION_ID)).thenReturn(true);

        var principal = service.authenticate(rawValue);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getUniqueId().getLongValue()).isEqualTo(APPLICATION_ID);
        assertThat(principal.orElseThrow().getUsername()).isEqualTo("https://example.test/application");
    }

    @Test
    void authenticate_whenKeyIsExpiredRevokedOrMalformed_returnsEmpty() throws Exception {
        var secret = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        var key = IdentityApiKeyEntity.builder()
                .id(java.util.UUID.randomUUID())
                .principal(applicationPrincipal())
                .secretHash(MessageDigest.getInstance("SHA-256").digest(secret))
                .expiresAt(NOW)
                .build();
        var rawValue = key.getId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        assertThat(service.authenticate(rawValue)).isEmpty();
        assertThat(service.authenticate("not-an-api-key")).isEmpty();

        key.setExpiresAt(NOW.plusSeconds(3600));
        key.setRevokedAt(NOW.minusSeconds(1));
        assertThat(service.authenticate(rawValue)).isEmpty();
    }

    private IdentityPrincipalEntity applicationPrincipal() {
        return IdentityPrincipalEntity.builder()
                .uniqueId(APPLICATION_ID)
                .name("https://example.test/application")
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.APPLICATION)
                .build();
    }
}

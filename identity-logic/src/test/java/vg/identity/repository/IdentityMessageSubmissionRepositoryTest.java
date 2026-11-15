package vg.identity.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.MessageContentConverter;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.MessageContent;
import vg.unique.id.model.UniqueId;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityMessageSubmissionRepositoryTest {
    @Mock private EntityManager entityManager;
    @Mock private Query insert;
    @Mock private Query read;
    @Mock private NativeQuery<Object[]> projection;
    @Mock private MessageContentConverter converter;
    private IdentityMessageSubmissionRepository repository;
    private final Instant now = Instant.parse("2026-11-18T00:00:00.123456789Z");

    @BeforeEach
    void setUp() {
        repository = new IdentityMessageSubmissionRepository(entityManager, converter, Clock.fixed(now, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void insertOrGet_whenDatabaseReturnsWinner_usesEncryptedBindingsAndFreshProjectionRegardlessOfUpdateCount(int count) {
        var key = UUID.randomUUID();
        var content = MessageContent.builder().text("hello").build();
        var request = IdentityApplicationMessageRequest.builder().content(content).idempotencyKey(key).build();
        var encrypted = new byte[]{1, 2, 3};
        var parameters = new HashMap<String, Object>();
        when(entityManager.createNativeQuery(anyString())).thenReturn(insert, read);
        when(insert.setParameter(anyString(), any())).thenAnswer(call -> {
            parameters.put(call.getArgument(0), call.getArgument(1));
            return insert;
        });
        when(insert.executeUpdate()).thenReturn(count);
        when(read.setParameter(anyString(), any())).thenReturn(read);
        when(read.unwrap(NativeQuery.class)).thenReturn(projection);
        when(projection.addScalar(anyString(), any(Class.class))).thenReturn(projection);
        when(projection.getSingleResult()).thenReturn(new Object[]{30L, 20L, encrypted, 4, 3, Instant.EPOCH, Instant.EPOCH});
        when(converter.convertToDatabaseColumn(content)).thenReturn(encrypted);
        when(converter.convertToEntityAttribute(encrypted)).thenReturn(content);

        var result = repository.insertOrGet(new UniqueId(31L), new UniqueId(10L), new UniqueId(20L), request);

        assertThat(parameters).containsEntry("id", 31L).containsEntry("application", 10L).containsEntry("user", 20L)
                .containsEntry("status", 0).containsEntry("now", Instant.parse("2026-11-18T00:00:00.123456Z"));
        assertThat((byte[]) parameters.get("key")).isEqualTo(ByteBuffer.allocate(16)
                .putLong(key.getMostSignificantBits()).putLong(key.getLeastSignificantBits()).array());
        assertThat(parameters.get("content")).isSameAs(encrypted);
        assertThat(result.content()).isSameAs(content);
        assertThat(result.message().messageUniqueId()).isEqualTo(new UniqueId(30L));
        assertThat(result.message().userUniqueId()).isEqualTo(new UniqueId(20L));
        assertThat(result.message().status()).isEqualTo(IdentityMessageStatus.UNKNOWN);
        assertThat(result.message().failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        assertThat(result.message().createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(result.message().updatedAt()).isEqualTo(Instant.EPOCH);
    }
}

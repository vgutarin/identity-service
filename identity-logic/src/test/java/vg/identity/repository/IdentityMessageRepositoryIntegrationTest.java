package vg.identity.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityMessageEntity;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.MessageContent;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.unique.id.model.UniqueId;
import vg.identity.service.EncryptionService;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityMessageRepositoryIntegrationTest extends BaseIntegrationTest {
    @Autowired private IdentityMessageRepository messages;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EncryptionService encryption;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IdentityMessageSubmissionRepository submissions;

    @AfterEach
    void clearMessages() {
        messages.deleteAllInBatch();
    }

    @Test
    void saveAndFlush_whenIdempotencyKeyIsPresent_preservesKeyWithoutRouteMetadata() {
        var key = UUID.randomUUID();

        messages.saveAndFlush(message(key));

        assertThat(messages.findById(30L).orElseThrow().getIdempotencyKey()).isEqualTo(key);
        assertThat(jdbc.queryForMap("SELECT * FROM identity_message WHERE unique_id = ?", 30L))
                .doesNotContainKeys("text", "telegram_bot_id", "channel_type", "channel_unique_id");
    }

    @Test
    void saveAndFlush_whenContentIsPresent_storesEncryptedCompactJsonAndRestoresContent() {
        var content = MessageContent.builder().text(" <b>Hello</b> {BOT_URI} 🙂 \n").build();
        var message = message(UUID.randomUUID());
        message.setContent(content);

        messages.saveAndFlush(message);

        var encrypted = jdbc.queryForObject("SELECT content FROM identity_message WHERE unique_id = ?", byte[].class, 30L);
        assertThat(new String(encrypted, StandardCharsets.UTF_8)).doesNotContain(content.text());
        var json = objectMapper.readTree(encryption.decode(encrypted));
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("t").asText()).isEqualTo(content.text());
        assertThat(messages.findById(30L).orElseThrow().getContent()).isEqualTo(content);
    }

    @Test
    void saveAndFlush_whenContentIsNull_rejectsMessage() {
        var message = message(UUID.randomUUID());
        message.setContent(null);

        assertThatThrownBy(() -> messages.saveAndFlush(message)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(messages.count()).isZero();
    }

    @Test
    void update_whenContentIsNull_rejectsChangeAtDatabaseBoundary() {
        messages.saveAndFlush(message(UUID.randomUUID()));

        assertThatThrownBy(() -> jdbc.update("UPDATE identity_message SET content = NULL WHERE unique_id = ?", 30L))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("content");

        assertThat(messages.findById(30L).orElseThrow().getContent().text()).isEqualTo("hello");
    }

    @Test
    void findById_whenStoredContentIsMalformed_failsWithoutDisclosingPlaintext() {
        messages.saveAndFlush(message(UUID.randomUUID()));
        jdbc.update("UPDATE identity_message SET content = ? WHERE unique_id = ?",
                encryption.encode("{\"t\":\"private malformed content"), 30L);

        assertThatThrownBy(() -> messages.findById(30L))
                .hasStackTraceContaining("Cannot deserialize message content")
                .hasStackTraceContaining("MessageContentConverter")
                .satisfies(error -> {
                    var trace = new java.io.StringWriter();
                    error.printStackTrace(new java.io.PrintWriter(trace));
                    assertThat(trace.toString()).doesNotContain("private malformed content");
                });
    }

    @ParameterizedTest
    @CsvSource({
            "QUEUED, 0, , ",
            "SENDING, 1, , ",
            "SENT, 2, , ",
            "FAILED, 3, DELIVERY_UNAVAILABLE, 0",
            "FAILED, 3, PROVIDER_REJECTED, 1",
            "FAILED, 3, INTERNAL_ERROR, 2",
            "UNKNOWN, 4, DELIVERY_UNKNOWN, 3"
    })
    void saveAndFlush_whenMessageHasStatusAndFailureCode_storesStableOrdinalsAndReadsEnums(
            IdentityMessageStatus status, int statusOrdinal, IdentityMessageFailureCode failureCode, Integer failureOrdinal) {
        var message = message(UUID.randomUUID());
        message.setStatus(status);
        message.setFailureCode(failureCode);

        messages.saveAndFlush(message);

        var row = jdbc.queryForMap("SELECT status, failure_code FROM identity_message WHERE unique_id = ?", 30L);
        assertThat(row.get("status")).isInstanceOf(Number.class);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(statusOrdinal);
        if (failureOrdinal == null) {
            assertThat(row.get("failure_code")).isNull();
        } else {
            assertThat(row.get("failure_code")).isInstanceOf(Number.class);
            assertThat(((Number) row.get("failure_code")).intValue()).isEqualTo(failureOrdinal);
        }
        var reloaded = messages.findById(30L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(status);
        assertThat(reloaded.getFailureCode()).isEqualTo(failureCode);
    }

    @Test
    void saveAndFlush_whenIdempotencyKeyIsNull_rejectsMessage() {
        assertThatThrownBy(() -> messages.saveAndFlush(message(null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(messages.count()).isZero();
    }

    @Test
    void insert_whenIdempotencyKeyIsNull_rejectsMessageAtDatabaseBoundary() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO identity_message
                    (unique_id, version, application_unique_id, user_unique_id, idempotency_key,
                     content, status, created_at, updated_at)
                VALUES (30, 0, 10, 20, NULL, ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, new byte[]{1}))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idempotency_key");

        assertThat(messages.count()).isZero();
    }

    @Test
    void insertOrGet_whenCandidateIsNew_storesEncryptedContentAndMetadataCompatibleWithJpa() {
        var request = request(UUID.randomUUID(), " <b>Hello</b> 🙂 ");

        var snapshot = new TransactionTemplate(transactionManager).execute(status -> submissions.insertOrGet(
                new UniqueId(30L), new UniqueId(10L), new UniqueId(20L), request));

        var stored = messages.findById(30L).orElseThrow();
        assertThat(stored.getIdempotencyKey()).isEqualTo(request.idempotencyKey());
        assertThat(stored.getContent()).isEqualTo(request.content());
        assertThat(stored.getVersion()).isZero();
        assertThat(stored.getStatus()).isEqualTo(IdentityMessageStatus.QUEUED);
        assertThat(snapshot.message().createdAt()).isEqualTo(stored.getCreatedAt());
        assertThat(snapshot.message().updatedAt()).isEqualTo(stored.getUpdatedAt());
        var bytes = jdbc.queryForObject("SELECT content FROM identity_message WHERE unique_id = 30", byte[].class);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain(request.content().text());
        assertThat(objectMapper.readTree(encryption.decode(bytes)).path("t").asText()).isEqualTo(request.content().text());
    }

    @ParameterizedTest
    @CsvSource({"QUEUED,", "SENDING,", "SENT,", "FAILED,DELIVERY_UNAVAILABLE", "FAILED,PROVIDER_REJECTED",
            "FAILED,INTERNAL_ERROR", "UNKNOWN,DELIVERY_UNKNOWN"})
    void insertOrGet_whenKeyAlreadyExists_preservesAllStoredColumnsAndProjectsCurrentEnums(
            IdentityMessageStatus state, IdentityMessageFailureCode failure) {
        var key = UUID.randomUUID();
        var original = message(key);
        original.setStatus(state);
        original.setFailureCode(failure);
        messages.saveAndFlush(original);
        jdbc.update("UPDATE identity_message SET version = 7 WHERE unique_id = 30");
        var before = jdbc.queryForMap("SELECT * FROM identity_message WHERE unique_id = 30");

        var snapshot = new TransactionTemplate(transactionManager).execute(status -> submissions.insertOrGet(
                new UniqueId(31L), new UniqueId(10L), new UniqueId(21L), request(key, "different")));

        assertThat(snapshot.message().messageUniqueId()).isEqualTo(new UniqueId(30L));
        assertThat(snapshot.message().userUniqueId()).isEqualTo(new UniqueId(20L));
        assertThat(snapshot.message().status()).isEqualTo(state);
        assertThat(snapshot.message().failureCode()).isEqualTo(failure);
        assertThat(snapshot.content().text()).isEqualTo("hello");
        assertThat(jdbc.queryForMap("SELECT * FROM identity_message WHERE unique_id = 30"))
                .usingRecursiveComparison().isEqualTo(before);
        assertThat(messages.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void insertOrGet_whenCallerHasPendingChanges_keepsThemManagedAndSharesCommitOrRollback(boolean rollback) {
        var workspace = createWorkspace();
        var originalName = workspace.getName();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var managed = entityManager.find(vg.identity.entity.IdentityWorkspaceEntity.class, workspace.getUniqueId());
            managed.setName("pending caller change");

            submissions.insertOrGet(new UniqueId(30L), new UniqueId(10L), new UniqueId(20L), request(UUID.randomUUID(), "hello"));

            assertThat(entityManager.contains(managed)).isTrue();
            assertThat(managed.getName()).isEqualTo("pending caller change");
            if (rollback) {
                status.setRollbackOnly();
            }
        });

        assertThat(messages.count()).isEqualTo(rollback ? 0 : 1);
        assertThat(workspaceRepository.findById(workspace.getUniqueId()).orElseThrow().getName())
                .isEqualTo(rollback ? originalName : "pending caller change");
    }

    @Test
    void insertOrGet_whenCallerHasNoTransaction_rejectsBeforeWriting() {
        assertThatThrownBy(() -> submissions.insertOrGet(new UniqueId(30L), new UniqueId(10L), new UniqueId(20L),
                request(UUID.randomUUID(), "hello"))).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(messages.count()).isZero();
    }

    private static IdentityApplicationMessageRequest request(UUID key, String text) {
        return IdentityApplicationMessageRequest.builder().idempotencyKey(key)
                .content(MessageContent.builder().text(text).build()).build();
    }

    private static IdentityMessageEntity message(UUID key) {
        return IdentityMessageEntity.builder().uniqueId(30L).applicationUniqueId(10L).userUniqueId(20L)
                .idempotencyKey(key).content(MessageContent.builder().text("hello").build()).status(IdentityMessageStatus.QUEUED).build();
    }
}

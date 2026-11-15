package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.ObjectMapper;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityApplicationUserEntity;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.entity.IdentityMessageEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.*;
import vg.identity.model.command.IdentityCommandStatus;
import vg.identity.model.command.IdentityCommandType;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.identity.model.principal.IdentityInternalPrincipalStatus;
import vg.identity.model.principal.IdentityInternalPrincipalType;
import vg.identity.repository.IdentityCommandRepository;
import vg.identity.repository.IdentityMessageRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApplicationMessagingIntegrationTest extends BaseIntegrationTest {
    private static final String TOKEN = "123456:messaging-test-token";
    @Autowired private IdentityApplicationApiService embeddedApi;
    @Autowired private IdentityApiKeyService apiKeys;
    @MockitoSpyBean private IdentityMessageService messageService;
    @Autowired private IdentityMessageCommandProcessor processor;
    @Autowired private IdentityCommandRepository commands;
    @Autowired private IdentityMessageRepository messages;
    @MockitoSpyBean private IdentityCommandService commandService;
    @MockitoSpyBean private IdentityUserChannelRepository messagingChannels;
    @MockitoSpyBean private UniqueIdService messageIds;
    @Autowired private EmailService emailService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TelegramWebhookRegistrationService webhooks;
    @MockitoBean private TelegramApiClient telegram;
    private IdentityApplicationApiRestClient remote;

    @BeforeEach
    void client() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey())
                .requestFactory(requestFactory).build();
        remote = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(IdentityApplicationApiRestClient.class);
    }

    @AfterEach
    void clearQueueAndSecurity() {
        SecurityContextHolder.clearContext();
        commands.deleteAll();
        messages.deleteAll();
    }

    @Test
    void submitMessage_whenCalledRemotelyAndReplayedInProcess_sharesMessageAndSendsOutsideTransaction() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID();
        var request = request("<b>Your order is ready</b> 🙂", key);
        var submission = remote.submitMessage(user, request);
        var submitted = submission.message();
        asApplication();
        var replay = embeddedApi.submitMessage(user, request);
        SecurityContextHolder.clearContext();
        assertThat(submission.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.message()).isEqualTo(submitted);
        assertThat(submitted.status()).isEqualTo(IdentityMessageStatus.QUEUED);
        assertThat(submitted.createdAt()).isNotNull();
        assertThat(submitted.updatedAt()).isNotNull();
        var queued = commands.findAll().getFirst();
        assertThat(queued.getCommandType()).isEqualTo(IdentityCommandType.SEND_APPLICATION_MESSAGE);
        var persistedCommandType = new TransactionTemplate(transactionManager).execute(status -> (Number) entityManager
                .createNativeQuery("select command_type from identity_command where id = :id")
                .setParameter("id", queued.getId()).getSingleResult());
        assertThat(persistedCommandType.intValue()).isEqualTo(2);
        var payload = objectMapper.readTree(queued.getPayload());
        assertThat(payload.size()).isEqualTo(1);
        assertThat(payload.path("messageUniqueId").asText()).isEqualTo(submitted.messageUniqueId().toString());
        assertThat(queued.getPayload()).doesNotContain(request.content().text(), TOKEN);
        var raw = new TransactionTemplate(transactionManager).execute(status -> (byte[]) entityManager
                .createNativeQuery("select content from identity_message where unique_id = :id")
                .setParameter("id", submitted.messageUniqueId().getLongValue()).getSingleResult());
        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("Your order", TOKEN);
        var storedContent = objectMapper.readTree(encryptionService.decode(raw));
        assertThat(storedContent.size()).isEqualTo(1);
        assertThat(storedContent.path("t").asText()).isEqualTo(request.content().text());
        assertThat(messages.findById(submitted.messageUniqueId()).orElseThrow().getContent()).isEqualTo(request.content());
        verifyNoInteractions(telegram);
        when(telegram.sendMessage(TOKEN, 42L, request.content().text())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var sending = remote.findMessage(submitted.messageUniqueId()).orElseThrow();
            assertThat(sending.status()).isEqualTo(IdentityMessageStatus.SENDING);
            return acknowledgement();
        });
        worker().processNextQueuedCommand();
        var sent = remote.findMessage(submitted.messageUniqueId()).orElseThrow();
        assertThat(sent.status()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(sent.failureCode()).isNull();
        assertThat(commands.findById(queued.getId()).orElseThrow().getCommandStatus()).isEqualTo(IdentityCommandStatus.COMPLETED);
        commandService.enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(sent.messageUniqueId()).build());
        worker().processNextQueuedCommand();
        verify(telegram).sendMessage(TOKEN, 42L, request.content().text());
    }

    @Test
    void submitMessage_whenMessageIsNewQueuedOrSent_returnsConsistentEmbeddedAndRemoteResults() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID();
        asApplication();
        var created = embeddedApi.submitMessage(user, request("hello", key));
        SecurityContextHolder.clearContext();
        assertThat(created.replayed()).isFalse();
        var queuedReplay = remote.submitMessage(user, request("hello", key));
        assertThat(queuedReplay.replayed()).isTrue();
        assertThat(queuedReplay.message()).isEqualTo(created.message());
        when(telegram.sendMessage(TOKEN, 42L, "hello")).thenReturn(acknowledgement());
        worker().processNextQueuedCommand();
        var sentReplay = remote.submitMessage(user, request("hello", key));
        assertThat(sentReplay.replayed()).isTrue();
        assertThat(sentReplay.message().status()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(sentReplay.message().messageUniqueId()).isEqualTo(created.message().messageUniqueId());
        asApplication();
        assertThat(embeddedApi.submitMessage(user, request("hello", key))).isEqualTo(sentReplay);
        SecurityContextHolder.clearContext();
        var newSubmission = remote.submitMessage(user, request("hello"));
        assertThat(newSubmission.replayed()).isFalse();
        assertThat(newSubmission.message().messageUniqueId()).isNotEqualTo(created.message().messageUniqueId());
    }

    @Test
    void submitMessage_whenHttpRequestIsValidOrInvalid_returnsReceiptOrRejectsWithoutPersistence() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        assertThat(post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", null, false).statusCode()).isEqualTo(401);
        for (var invalid : new String[]{"{}", "{\"content\":null}", "{\"content\":{}}", "{\"text\":\"hello\"}",
                "{\"content\":{\"text\":\"hello\"}}", "{\"content\":{\"t\":null}}", "{\"content\":{\"t\":\" \"}}",
                "{\"content\":{\"t\":\"" + "a".repeat(4097) + "\"}}"}) {
            assertThat(post(user.toString(), invalid, UUID.randomUUID().toString(), true).statusCode()).isEqualTo(400);
        }
        for (var invalid : List.of("null", "{")) {
            assertThat(post(user.toString(), invalid, null, true).statusCode()).isEqualTo(400);
        }
        assertThat(post("!invalid!", "{\"content\":{\"t\":\"hello\"}}", UUID.randomUUID().toString(), true).statusCode()).isEqualTo(400);
        for (var key : List.of("invalid", "1-1-1-1-1", "", " ")) {
            assertThat(post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", key, true).statusCode()).isEqualTo(400);
        }
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        var response = post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", UUID.randomUUID().toString(), true);
        assertThat(response.statusCode()).isEqualTo(202);
        var receipt = objectMapper.readValue(response.body(), IdentityApplicationMessage.class);
        assertThat(receipt.userUniqueId()).isEqualTo(user);
        assertThat(receipt.status()).isEqualTo(IdentityMessageStatus.QUEUED);
        var body = objectMapper.readTree(response.body());
        assertThat(body.has("failureCode")).isFalse();
        assertThat(body.has("message")).isFalse();
        assertThat(body.has("replayed")).isFalse();
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/api/v1/applications/me/messages/" + receipt.messageUniqueId());
        assertThat(get(receipt.messageUniqueId().toString(), false).statusCode()).isEqualTo(401);
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenBodyKeyIsOmittedOrNull_rejectsWithoutPersistence() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        for (var body : List.of("{\"content\":{\"t\":\"same\"}}", "{\"content\":{\"t\":\"same\"},\"idempotencyKey\":null}")) {
            var response = post(user.toString(), body, null, true);
            assertThat(response.statusCode()).isEqualTo(400);
            var problem = objectMapper.readTree(response.body());
            assertThat(problem.path("detail").asText()).isEqualTo("Invalid request body");
        }
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        verifyNoInteractions(telegram);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/applications/me/users/!invalid!/messages",
            "/api/v1/applications/me/authentications/telegram",
            "/api/v1/telegram/webhooks/!invalid!"
    })
    void restRequests_whenJsonIsUnreadable_returnSharedSanitizedBadRequest(String path) throws Exception {
        messagingApplication();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path))
                .header("Content-Type", "application/json")
                .header(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey())
                .POST(HttpRequest.BodyPublishers.ofString("{\"private-payload\":"))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        assertThat(problem.path("detail").asText()).isEqualTo("Invalid request body");
        assertThat(response.body()).doesNotContain("private-payload", "stackTrace");
        assertThat(problem.has("trace")).isFalse();
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        assertThat(channelRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
        verifyNoInteractions(telegram);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/applications/me/users/!invalid!/messages",
            "GET, /api/v1/applications/me/messages/!invalid!",
            "GET, /api/v1/applications/me/users/!invalid!/telegram-phone",
            "DELETE, /api/v1/applications/me/users/!invalid!/telegram-phone"
    })
    void applicationRequests_whenIdentifierIsMalformed_returnSanitizedBadRequest(String method, String path) throws Exception {
        messagingApplication();
        var body = method.equals("POST")
                ? HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request("hello")))
                : HttpRequest.BodyPublishers.noBody();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path))
                .header("Content-Type", "application/json").method(method, body);
        var client = HttpClient.newHttpClient();
        assertThat(client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        request.header(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey());
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        assertThat(problem.path("detail").asText()).isEqualTo("Invalid request argument");
        assertThat(problem.has("trace")).isFalse();
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenOnlyObsoleteHeaderIsPresent_rejectsMissingBodyKey() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort
                + "/api/v1/applications/me/users/" + user + "/messages"))
                .header("Content-Type", "application/json")
                .header(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":{\"t\":\"same\"}}")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenRouteOrMembershipIsUnavailable_tracksFailureOrRejectsSubmission() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        channelRepository.deleteAll();
        var receipt = remote.submitMessage(user, request("hello")).message();
        assertThat(receipt.status()).isEqualTo(IdentityMessageStatus.QUEUED);
        worker().processNextQueuedCommand();
        var failed = remote.findMessage(receipt.messageUniqueId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(IdentityMessageStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        applicationUserRepository.deleteAll();
        for (var userId : List.of(user, new UniqueId(987654L))) {
            var response = post(userId.toString(), "{\"content\":{\"t\":\"hello\"}}", UUID.randomUUID().toString(), true);
            assertThat(response.statusCode()).isEqualTo(422);
            var problem = objectMapper.readTree(response.body());
            assertThat(problem.path("detail").asText()).isEqualTo("Message delivery is unavailable");
            assertThat(problem.path("code").asText()).isEqualTo("MESSAGE_DELIVERY_UNAVAILABLE");
        }
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
        assertThat(remote.findMessage(receipt.messageUniqueId())).contains(failed);
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenKeyIsReused_returnsCurrentStateOrConflictsOnDifferentContent() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID().toString();
        var first = post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", key, true);
        var message = objectMapper.readValue(first.body(), IdentityApplicationMessage.class);
        when(telegram.sendMessage(TOKEN, 42L, "hello")).thenReturn(acknowledgement());
        worker().processNextQueuedCommand();
        applicationUserRepository.deleteAll();
        var replay = post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", key, true);
        assertThat(replay.statusCode()).isEqualTo(200);
        var current = objectMapper.readValue(replay.body(), IdentityApplicationMessage.class);
        assertThat(current.messageUniqueId()).isEqualTo(message.messageUniqueId());
        assertThat(current.status()).isEqualTo(IdentityMessageStatus.SENT);
        var conflict = post(user.toString(), "{\"content\":{\"t\":\"different\"}}", key, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(conflict.body()).path("code").asText()).isEqualTo("MESSAGE_IDEMPOTENCY_CONFLICT");
        assertThat(objectMapper.readTree(conflict.body()).path("detail").asText())
                .isEqualTo("Idempotency key is already used for a different message");
        assertThat(post(new UniqueId(99L).toString(), "{\"content\":{\"t\":\"hello\"}}", key, true).statusCode()).isEqualTo(409);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
    }

    @Test
    void submitMessage_whenKeyUsesEquivalentJacksonRepresentations_replaysSameMessage() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID();
        var bytes = ByteBuffer.allocate(16).putLong(key.getMostSignificantBits()).putLong(key.getLeastSignificantBits()).array();
        var base64 = Base64.getEncoder().encodeToString(bytes);
        var first = post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", key.toString(), true);
        assertThat(first.statusCode()).isEqualTo(202);
        var message = objectMapper.readValue(first.body(), IdentityApplicationMessage.class);

        var replay = post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", base64, true);

        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readValue(replay.body(), IdentityApplicationMessage.class)).isEqualTo(message);
        assertThat(messages.findById(message.messageUniqueId().getLongValue()).orElseThrow().getIdempotencyKey()).isEqualTo(key);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenIdenticalKeyedRequestsRunConcurrently_createsOneMessageAndCommand() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID().toString();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var calls = new ArrayList<Callable<HttpResponse<String>>>();
            for (int i = 0; i < 8; i++) calls.add(() -> post(user.toString(), "{\"content\":{\"t\":\"hello\"}}", key, true));
            var futures = executor.invokeAll(calls, 30, TimeUnit.SECONDS);
            var results = new ArrayList<HttpResponse<String>>();
            for (var future : futures) results.add(future.get());
            assertThat(results).filteredOn(r -> r.statusCode() == 202).hasSize(1);
            assertThat(results).filteredOn(r -> r.statusCode() == 200).hasSize(7);
            assertThat(results.stream().map(r -> objectMapper.readTree(r.body()).path("messageUniqueId").asText()).distinct()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
        verifyNoInteractions(telegram);
    }

    @Test
    void submitMessage_whenConcurrentRequestsReuseKeyWithDifferentContent_acceptsOneAndRejectsOther() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID().toString();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = List.<Callable<HttpResponse<String>>>of(
                    () -> post(user.toString(), "{\"content\":{\"t\":\"first\"}}", key, true),
                    () -> post(user.toString(), "{\"content\":{\"t\":\"second\"}}", key, true));
            var futures = executor.invokeAll(calls, 30, TimeUnit.SECONDS);
            var first = futures.getFirst().get();
            var second = futures.getLast().get();

            assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(202, 409);
            assertThat(messages.findAll()).singleElement().satisfies(message ->
                    assertThat(message.getContent().text()).isEqualTo(first.statusCode() == 202 ? "first" : "second"));
            assertThat(commands.count()).isEqualTo(1);
            verifyNoInteractions(telegram);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void submitMessage_whenSameKeyWaitsForRolledBackSubmission_createsOneCommittedMessageAndCommand() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var appId = new UniqueId(app.uniqueId());
        var request = request("winner after rollback");
        var inserted = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var rollback = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = messageService.submit(appId, user, request);
                inserted.countDown();
                awaitCommit(rollback);
                status.setRollbackOnly();
                return result;
            }));
            assertThat(inserted.await(30, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(messages.count()).isZero();
                waiting.countDown();
                return messageService.submit(appId, user, request);
            }));
            assertThat(waiting.await(30, TimeUnit.SECONDS)).isTrue();
            rollback.countDown();

            var rolledBack = first.get(30, TimeUnit.SECONDS);
            var winner = second.get(30, TimeUnit.SECONDS);

            assertThat(winner.replayed()).isFalse();
            assertThat(winner.message().messageUniqueId()).isNotEqualTo(rolledBack.message().messageUniqueId());
            assertThat(messages.findById(rolledBack.message().messageUniqueId())).isEmpty();
            assertThat(messageService.submit(appId, user, request).message()).isEqualTo(winner.message());
            assertThat(messages.count()).isEqualTo(1);
            assertThat(commands.count()).isEqualTo(1);
            verifyNoInteractions(telegram);
        } finally {
            rollback.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void submitMessage_whenDifferentApplicationsInsertConcurrently_keepsIndependentCommitAndRollback(boolean rollbackFirst)
            throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var firstApp = new UniqueId(app.uniqueId());
        var secondApp = otherApplication(app, user);
        var key = UUID.randomUUID();
        var firstRequest = request("first application", key);
        var secondRequest = request("second application", key);
        var barrier = new CyclicBarrier(2);
        // The old flow held missing-key gap locks at ID allocation; both callers must reach this point.
        doAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(messageIds).getNext();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = messageService.submit(firstApp, user, firstRequest);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            var second = executor.submit(() -> messageService.submit(secondApp, user, secondRequest));

            var firstResult = first.get(30, TimeUnit.SECONDS);
            var secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.replayed()).isFalse();
            assertThat(secondResult.replayed()).isFalse();
            assertThat(firstResult.message().messageUniqueId()).isNotEqualTo(secondResult.message().messageUniqueId());
            assertThat(messages.count()).isEqualTo(rollbackFirst ? 1 : 2);
            assertThat(commands.count()).isEqualTo(rollbackFirst ? 1 : 2);
            assertThat(messageService.find(firstApp, firstResult.message().messageUniqueId()).isPresent()).isEqualTo(!rollbackFirst);
            assertThat(messageService.find(secondApp, secondResult.message().messageUniqueId())).contains(secondResult.message());
            doCallRealMethod().when(messageIds).getNext();
            var retry = messageService.submit(firstApp, user, firstRequest);
            assertThat(retry.replayed()).isEqualTo(!rollbackFirst);
            assertThat(messages.count()).isEqualTo(2);
            assertThat(commands.count()).isEqualTo(2);
            verifyNoInteractions(telegram);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void submitMessage_whenManagedMessageBecomesStale_returnsCurrentReplayAndPreservesCallerChanges(boolean rollback)
            throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var appId = new UniqueId(app.uniqueId());
        var request = request("stale replay");
        var original = messageService.submit(appId, user, request).message();
        var originalWorkspaceName = workspaceRepository.findById(app.workspaceUniqueId()).orElseThrow().getName();
        when(telegram.sendMessage(TOKEN, 42L, "stale replay")).thenReturn(acknowledgement());
        var sent = new AtomicReference<IdentityMessageEntity>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var held = entityManager.find(IdentityMessageEntity.class, original.messageUniqueId().getLongValue());
                var workspace = entityManager.find(IdentityWorkspaceEntity.class, app.workspaceUniqueId());
                assertThat(held.getStatus()).isEqualTo(IdentityMessageStatus.QUEUED);
                try {
                    sent.set(executor.submit(() -> {
                        worker().processNextQueuedCommand();
                        return messages.findById(original.messageUniqueId()).orElseThrow();
                    }).get(30, TimeUnit.SECONDS));
                } catch (Exception e) {
                    throw new IllegalStateException("Concurrent delivery did not finish", e);
                }
                workspace.setName("pending replay caller change");

                var replay = messageService.submit(appId, user, request);

                assertThat(replay.replayed()).isTrue();
                assertThat(replay.message().messageUniqueId()).isEqualTo(original.messageUniqueId());
                assertThat(replay.message().status()).isEqualTo(IdentityMessageStatus.SENT);
                assertThat(replay.message().updatedAt()).isEqualTo(sent.get().getUpdatedAt());
                assertThat(entityManager.find(IdentityMessageEntity.class, held.getUniqueId())).isSameAs(held);
                assertThat(held.getStatus()).isEqualTo(IdentityMessageStatus.QUEUED);
                assertThat(entityManager.contains(workspace)).isTrue();
                assertThat(workspace.getName()).isEqualTo("pending replay caller change");
                if (rollback) {
                    status.setRollbackOnly();
                }
            });
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        var stored = messages.findById(original.messageUniqueId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(stored.getVersion()).isEqualTo(sent.get().getVersion());
        assertThat(stored.getCreatedAt()).isEqualTo(sent.get().getCreatedAt());
        assertThat(stored.getUpdatedAt()).isEqualTo(sent.get().getUpdatedAt());
        assertThat(workspaceRepository.findById(app.workspaceUniqueId()).orElseThrow().getName())
                .isEqualTo(rollback ? originalWorkspaceName : "pending replay caller change");
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
    }

    @Test
    void submitMessage_whenCallerSnapshotPredatesCommittedWinner_returnsReplayWithoutDuplicateInsertion() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var appId = new UniqueId(app.uniqueId());
        var request = request("winner outside snapshot");
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(messages.count()).isZero();
                IdentityMessageSubmission winner;
                try {
                    winner = executor.submit(() -> messageService.submit(appId, user, request)).get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("Concurrent submission did not finish", e);
                }

                var replay = messageService.submit(appId, user, request);

                assertThat(replay.replayed()).isTrue();
                assertThat(replay.message()).isEqualTo(winner.message());
            });
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
    }

    @Test
    void submitMessage_whenApplicationScopesOrKeysDiffer_createsDistinctMessages() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID();
        var first = remote.submitMessage(user, request("hello", key)).message();
        var otherAppId = otherApplication(app, user);
        var other = messageService.submit(otherAppId, user, request("hello", key)).message();
        assertThat(first.messageUniqueId()).isNotEqualTo(other.messageUniqueId());
        assertThat(get(other.messageUniqueId().toString(), true).statusCode()).isEqualTo(404);
        assertThat(remote.findMessage(other.messageUniqueId())).isEmpty();
        assertThat(remote.findMessage(new UniqueId(987654L))).isEmpty();
        var one = remote.submitMessage(user, request("same")).message();
        var two = remote.submitMessage(user, request("same")).message();
        assertThat(one.messageUniqueId()).isNotEqualTo(two.messageUniqueId());
        assertThat(messages.count()).isEqualTo(4);
        assertThat(commands.count()).isEqualTo(4);
    }

    @Test
    void submitMessage_whenEmbeddedCallerRollsBack_rollsBackMessageAndCommand() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var key = UUID.randomUUID();
        asApplication();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            embeddedApi.submitMessage(user, request("rollback", key));
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(messages.count()).isZero();
        assertThat(commands.count()).isZero();
        var accepted = embeddedApi.submitMessage(user, request("rollback", key)).message();
        assertThat(accepted.status()).isEqualTo(IdentityMessageStatus.QUEUED);
    }

    @ParameterizedTest
    @CsvSource({"1, true", "99, true", "100, false", "101, false"})
    void processNextQueuedCommand_whenSubmissionCommitsBehindCursor_appliesAcceptedLookbackLimit(
            int laterCommands, boolean eligible) throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var inserted = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var sends = new AtomicInteger();
        if (eligible) {
            when(telegram.sendMessage(TOKEN, 42L, "late commit")).thenAnswer(call -> {
                sends.incrementAndGet();
                return acknowledgement();
            });
        }
        var executor = Executors.newSingleThreadExecutor();
        var submission = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var message = messageService.submit(new UniqueId(app.uniqueId()), user, request("late commit")).message();
            inserted.countDown();
            awaitCommit(commit);
            return message;
        }));
        try {
            assertThat(inserted.await(30, TimeUnit.SECONDS)).isTrue();
            // Email commands use the shared ID sequence without taking the submission's application lock.
            for (int i = 0; i < laterCommands; i++) {
                var newer = commandService.enqueue(EmailMessage.builder().body("newer command").build());
                var claimed = commandService.startNextQueuedCommand();
                assertThat(claimed.id()).isEqualTo(newer.id());
                commandService.complete(claimed);
            }
            commit.countDown();
            var message = submission.get(30, TimeUnit.SECONDS);

            worker().processNextQueuedCommand();
            worker().processNextQueuedCommand();

            assertThat(remote.findMessage(message.messageUniqueId()).orElseThrow().status())
                    .isEqualTo(eligible ? IdentityMessageStatus.SENT : IdentityMessageStatus.QUEUED);
            var command = commands.findAll().stream()
                    .filter(candidate -> candidate.getCommandType() == IdentityCommandType.SEND_APPLICATION_MESSAGE)
                    .findFirst().orElseThrow();
            assertThat(command.getCommandStatus())
                    .isEqualTo(eligible ? IdentityCommandStatus.COMPLETED : IdentityCommandStatus.QUEUED);
            assertThat(sends).hasValue(eligible ? 1 : 0);
            if (!eligible) {
                verifyNoInteractions(telegram);
            }
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void startNextQueuedCommand_whenClaimRollsBack_recoversQueuedCommandThroughLookback() {
        var queued = commandService.enqueue(EmailMessage.builder().body("claim rollback").build());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(commandService.startNextQueuedCommand().id()).isEqualTo(queued.id());
            status.setRollbackOnly();
        });
        assertThat(commands.findById(queued.id()).orElseThrow().getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);

        var claimed = commandService.startNextQueuedCommand();

        assertThat(claimed.id()).isEqualTo(queued.id());
        assertThat(claimed.commandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
        commandService.complete(claimed);
    }

    @Test
    void processNextQueuedCommand_whenOnlyStartedAndTerminalCommandsRemain_doesNotRetryThemThroughLookback() {
        var statuses = List.of(IdentityCommandStatus.RUNNING, IdentityCommandStatus.COMPLETED, IdentityCommandStatus.FAILED);
        var stored = commands.saveAllAndFlush(statuses.stream().map(status -> IdentityCommandEntity.builder()
                .commandStatus(status).commandType(IdentityCommandType.SEND_APPLICATION_MESSAGE).payload("not a payload").build())
                .toList());

        worker().processNextQueuedCommand();
        worker().processNextQueuedCommand();

        assertThat(commands.findAllById(stored.stream().map(IdentityCommandEntity::getId).toList()))
                .extracting(IdentityCommandEntity::getCommandStatus).containsExactlyInAnyOrderElementsOf(statuses);
        verifyNoInteractions(telegram);
    }

    @Test
    void processNextQueuedCommand_whenBotChangesAndProviderRejects_usesCurrentBotAndRecordsRejection() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var message = remote.submitMessage(user, request("hello")).message();
        var stored = applicationRepository.findById(app.uniqueId()).orElseThrow();
        stored.setPayload("{\"telegramBotId\":999,\"token\":\"replacement-token\"}");
        applicationRepository.saveAndFlush(stored);
        var sends = new AtomicInteger();
        when(telegram.sendMessage("replacement-token", 42L, "hello")).thenAnswer(call -> {
            sends.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN, "private provider details");
        });
        worker().processNextQueuedCommand();
        var failed = remote.findMessage(message.messageUniqueId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(IdentityMessageStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo(IdentityMessageFailureCode.PROVIDER_REJECTED);
        assertThat(sends.get()).isEqualTo(1);
        assertThat(commands.findAll().getFirst().getErrorMessage()).doesNotContain("replacement-token", "private provider details");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void processNextQueuedCommand_whenStoredBotIdIsMissingOrNonpositive_sendsUsingTokenAndRecipient(Long botId) throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var application = applicationRepository.findById(app.uniqueId()).orElseThrow();
        application.setPayload(botId == null ? "{\"token\":\"" + TOKEN + "\"}"
                : "{\"telegramBotId\":" + botId + ",\"token\":\"" + TOKEN + "\"}");
        applicationRepository.saveAndFlush(application);
        var submitted = remote.submitMessage(user, request("token-only delivery")).message();
        var sends = new AtomicInteger();
        when(telegram.sendMessage(TOKEN, 42L, "token-only delivery")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            sends.incrementAndGet();
            return acknowledgement();
        });

        worker().processNextQueuedCommand();
        worker().processNextQueuedCommand();

        var sent = remote.findMessage(submitted.messageUniqueId()).orElseThrow();
        assertThat(sent.status()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(sent.failureCode()).isNull();
        assertThat(sends).hasValue(1);
        assertThat(commands.findAll()).singleElement()
                .extracting(command -> command.getCommandStatus()).isEqualTo(IdentityCommandStatus.COMPLETED);
        verify(telegram, never()).getMe(anyString());
    }

    @Test
    void processNextQueuedCommand_whenTelegramTimesOut_recordsSanitizedUnknownOutcomeWithoutResending() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var message = remote.submitMessage(user, request("private message")).message();
        when(telegram.sendMessage(TOKEN, 42L, "private message"))
                .thenThrow(new ResourceAccessException("https://api.telegram.org/bot" + TOKEN + " private message chat_id=42"));
        worker().processNextQueuedCommand();
        commandService.enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(message.messageUniqueId()).build());
        worker().processNextQueuedCommand();
        var current = remote.findMessage(message.messageUniqueId()).orElseThrow();
        assertThat(current.status()).isEqualTo(IdentityMessageStatus.UNKNOWN);
        assertThat(current.failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        assertThat(commands.findAll().getFirst().getErrorMessage()).contains("DELIVERY_UNKNOWN")
                .doesNotContain(TOKEN, "private message", "api.telegram.org", "chat_id");
        verify(telegram).sendMessage(TOKEN, 42L, "private message");
    }

    @Test
    void processNextQueuedCommand_whenPreparationRollsBack_failsCommandAndRetainsQueuedMessageWithoutRetry() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var request = request("private message");
        var message = remote.submitMessage(user, request).message();
        var commandId = commands.findAll().getFirst().getId();
        doThrow(new IllegalStateException("private persistence details " + TOKEN))
                .when(messagingChannels).findByIdentityUser_UniqueIdAndChannelTypeOrderByUpdatedAtDesc(
                        user.getLongValue(), IdentityChannelType.TELEGRAM_USER);
        var worker = worker();

        worker.processNextQueuedCommand();
        worker.processNextQueuedCommand();

        assertThat(remote.findMessage(message.messageUniqueId())).contains(message);
        var failed = commands.findById(commandId).orElseThrow();
        assertThat(failed.getCommandStatus()).isEqualTo(IdentityCommandStatus.FAILED);
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).contains("Cannot process application message command " + commandId)
                .doesNotContain(TOKEN, "private message", "private persistence details", "Caused by:");
        assertThat(remote.submitMessage(user, request).replayed()).isTrue();
        assertThat(commands.count()).isEqualTo(1);
        verifyNoInteractions(telegram);
    }

    @Test
    void processNextQueuedCommand_whenOutcomePersistenceRollsBack_failsCommandAndRetainsSendingMessageWithoutResending() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var request = request("private message");
        var message = remote.submitMessage(user, request).message();
        var commandId = commands.findAll().getFirst().getId();
        var sending = new AtomicReference<IdentityApplicationMessage>();
        var sends = new AtomicInteger();
        when(telegram.sendMessage(TOKEN, 42L, request.content().text())).thenAnswer(call -> {
            sends.incrementAndGet();
            sending.set(remote.findMessage(message.messageUniqueId()).orElseThrow());
            return acknowledgement();
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            call.callRealMethod();
            entityManager.flush();
            throw new IllegalStateException("private persistence details " + TOKEN);
        }).doCallRealMethod().when(messageService).finish(message.messageUniqueId(), IdentityMessageStatus.SENT, null);
        var worker = worker();

        worker.processNextQueuedCommand();
        worker.processNextQueuedCommand();

        assertThat(sending.get().status()).isEqualTo(IdentityMessageStatus.SENDING);
        assertThat(remote.findMessage(message.messageUniqueId())).contains(sending.get());
        var failed = commands.findById(commandId).orElseThrow();
        assertThat(failed.getCommandStatus()).isEqualTo(IdentityCommandStatus.FAILED);
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).contains("Cannot process application message command " + commandId)
                .doesNotContain(TOKEN, "private message", "private persistence details", "Caused by:");
        assertThat(remote.submitMessage(user, request).message()).isEqualTo(sending.get());
        assertThat(commands.count()).isEqualTo(1);
        commandService.enqueueApplicationMessage(SendApplicationMessageCommand.builder()
                .messageUniqueId(message.messageUniqueId()).build());
        worker.processNextQueuedCommand();
        assertThat(remote.findMessage(message.messageUniqueId())).contains(sending.get());
        assertThat(commands.findAll()).filteredOn(command -> command.getCommandStatus() == IdentityCommandStatus.COMPLETED).hasSize(1);
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    void processNextQueuedCommand_whenMessageIsAlreadySending_retainsInterruptedStateWithoutResending() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var message = remote.submitMessage(user, request("interrupted")).message();
        var original = commandService.startNextQueuedCommand();
        assertThat(messageService.begin(message.messageUniqueId())).isPresent();
        commandService.enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(message.messageUniqueId()).build());
        worker().processNextQueuedCommand();
        worker().processNextQueuedCommand();
        assertThat(remote.findMessage(message.messageUniqueId()).orElseThrow().status()).isEqualTo(IdentityMessageStatus.SENDING);
        assertThat(commands.findById(original.id()).orElseThrow().getCommandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
        verifyNoInteractions(telegram);
    }

    @Test
    void processNextQueuedCommand_whenCommandCompletionFails_retainsSentMessageWithoutResending() throws Exception {
        messagingApplication();
        var user = authenticateUser();
        var message = remote.submitMessage(user, request("hello")).message();
        var commandId = commands.findAll().getFirst().getId();
        var sends = new AtomicInteger();
        when(telegram.sendMessage(TOKEN, 42L, "hello")).thenAnswer(call -> {
            sends.incrementAndGet();
            return acknowledgement();
        });
        doThrow(new IllegalStateException("Command completion unavailable"))
                .doCallRealMethod().when(commandService).complete(any());
        var worker = worker();

        assertThatThrownBy(worker::processNextQueuedCommand).hasMessage("Command completion unavailable");
        var sent = remote.findMessage(message.messageUniqueId()).orElseThrow();
        assertThat(sent.status()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(sent.failureCode()).isNull();
        assertThat(commands.findById(commandId).orElseThrow().getCommandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
        worker.processNextQueuedCommand();
        commandService.enqueueApplicationMessage(SendApplicationMessageCommand.builder()
                .messageUniqueId(message.messageUniqueId()).build());
        worker.processNextQueuedCommand();

        assertThat(remote.findMessage(message.messageUniqueId())).contains(sent);
        assertThat(commands.findAll()).filteredOn(command -> command.getCommandStatus() == IdentityCommandStatus.COMPLETED).hasSize(1);
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    void processNextQueuedCommand_whenBotPayloadIsMalformedOrConsentIsRevoked_recordsSafeFailureCodes() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var first = remote.submitMessage(user, request("hello")).message();
        var application = applicationRepository.findById(app.uniqueId()).orElseThrow();
        application.setPayload("invalid-private-configuration");
        application = applicationRepository.saveAndFlush(application);
        worker().processNextQueuedCommand();
        assertThat(remote.findMessage(first.messageUniqueId()).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.INTERNAL_ERROR);
        application.setPayload("{\"telegramBotId\":123456,\"token\":\"" + TOKEN + "\"}");
        applicationRepository.saveAndFlush(application);
        var second = remote.submitMessage(user, request("hello")).message();
        var storedUser = userRepository.findById(user.getLongValue()).orElseThrow();
        storedUser.setConsentToKeepPersonalDataAt(null);
        userRepository.saveAndFlush(storedUser);
        worker().processNextQueuedCommand();
        assertThat(remote.findMessage(second.messageUniqueId()).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        verifyNoInteractions(telegram);
    }

    @Test
    void deleteById_whenApplicationAndUserHaveMessageHistory_preservesHistoryWithoutBlockingDeletion() throws Exception {
        var app = messagingApplication();
        var user = authenticateUser();
        var message = remote.submitMessage(user, request("history")).message();
        applicationUserRepository.deleteAll();
        apiKeyRepository.deleteAll();
        applicationRepository.deleteById(app.uniqueId());
        channelRepository.deleteAll();
        userRepository.deleteById(user.getLongValue());
        assertThat(messages.findById(message.messageUniqueId().getLongValue())).isPresent();
        worker().processNextQueuedCommand();
        assertThat(messages.findById(message.messageUniqueId().getLongValue()).orElseThrow().getStatus()).isEqualTo(IdentityMessageStatus.FAILED);
    }

    private TestApplication messagingApplication() {
        var app = createApplicationWithApiKey("{\"telegramBotId\":123456,\"token\":\"" + TOKEN + "\"}");
        var principal = principalRepository.findById(app.uniqueId()).orElseThrow();
        principal.setName("https://t.me/messaging_" + app.uniqueId() + "_bot");
        principal.setNameHash(encryptionService.hashPrincipalName(principal.getName()));
        principalRepository.saveAndFlush(principal);
        return app;
    }

    private static void awaitCommit(CountDownLatch commit) {
        try {
            if (!commit.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to commit submission");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Submission interrupted before commit", e);
        }
    }
    private UniqueId otherApplication(TestApplication app, UniqueId user) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var uri = "https://t.me/other_" + app.uniqueId() + "_bot";
            var principal = principalRepository.saveWithNewUniqueId(IdentityPrincipalEntity.builder().displayName("other").name(uri)
                    .nameHash(encryptionService.hashPrincipalName(uri)).status(IdentityInternalPrincipalStatus.ACTIVE)
                    .type(IdentityInternalPrincipalType.APPLICATION).build(), uniqueIdService);
            var application = IdentityApplicationEntity.builder().uniqueId(principal.getUniqueId())
                    .principal(principal).workspace(workspaceRepository.getReferenceById(app.workspaceUniqueId()))
                    .payload("{\"telegramBotId\":123456,\"token\":\"" + TOKEN + "\"}").build();
            entityManager.persist(application);
            entityManager.flush();
            applicationUserRepository.saveAndFlush(IdentityApplicationUserEntity.builder().application(application)
                    .identityUser(userRepository.getReferenceById(user.getLongValue())).lastAuthenticatedAt(Instant.now()).build());
            return new UniqueId(application.getUniqueId());
        });
    }
    private UniqueId authenticateUser() throws Exception {
        return remote.authenticateTelegram(TelegramAuthenticationRequest.builder()
                .telegramInitData(telegramInitData(TOKEN, "{\"id\":42,\"first_name\":\"John\"}"))
                .consentToKeepPersonalData(true).build()).orElseThrow().userUniqueId();
    }
    private void asApplication() {
        var principal = apiKeys.authenticate(restClientApiKey()).orElseThrow();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
    private static IdentityApplicationMessageRequest request(String text) {
        return request(text, UUID.randomUUID());
    }
    private static IdentityApplicationMessageRequest request(String text, UUID key) {
        return IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text(text).build()).idempotencyKey(key).build();
    }
    private static TelegramApiClient.SendMessageResponse acknowledgement() {
        return TelegramApiClient.SendMessageResponse.builder().ok(true)
                .result(TelegramApiClient.SentMessage.builder().messageId(7L).build()).build();
    }
    private IdentityCommandWorker worker() {
        return new IdentityCommandWorker(commandService, emailService, objectMapper, webhooks, processor);
    }
    private HttpResponse<String> post(String userId, String body, String key, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort
                + "/api/v1/applications/me/users/" + userId + "/messages")).header("Content-Type", "application/json");
        if (authenticated) request.header(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey());
        var payload = body;
        if (key != null) {
            var json = (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(body);
            json.put("idempotencyKey", key);
            payload = objectMapper.writeValueAsString(json);
        }
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String messageId, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/api/v1/applications/me/messages/" + messageId));
        if (authenticated) request.header(IdentityApplicationApiRestClient.API_KEY_HEADER, restClientApiKey());
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}

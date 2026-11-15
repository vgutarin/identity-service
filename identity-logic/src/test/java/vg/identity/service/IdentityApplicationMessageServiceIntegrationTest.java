package vg.identity.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityApplicationUserEntity;
import vg.identity.entity.IdentityRoleAssignmentEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.IdentityMessageIdempotencyConflictException;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.MessageContent;
import vg.identity.model.access.Permission;
import vg.identity.model.command.IdentityCommandStatus;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.identity.model.principal.IdentityInternalApplicationPrincipal;
import vg.identity.model.principal.IdentityInternalUser;
import vg.identity.repository.IdentityMessageRepository;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

class IdentityApplicationMessageServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired private IdentityApplicationMessageService service;
    @Autowired private IdentityApplicationApiService applicationApi;
    @Autowired private IdentityApplicationUserService members;
    @Autowired private IdentityMessageService core;
    @Autowired private IdentityMessageRepository messages;
    @Autowired private IdentityApiKeyService apiKeys;
    @MockitoSpyBean private IdentityCommandService commands;
    @Autowired private IdentityMessageCommandProcessor processor;
    @Autowired private EmailService email;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TelegramWebhookRegistrationService webhooks;
    @Autowired private EncryptionService encryption;
    @MockitoBean private TelegramApiClient telegram;
    private IdentityInternalUser actor;
    private IdentityInternalUser recipient;
    private IdentityWorkspaceEntity workspace;
    private IdentityApplicationEntity application;
    private UniqueId applicationId;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> audit;

    @BeforeEach
    void setUp() {
        actor = createIdentityUser("message-admin");
        recipient = createIdentityUser("message-recipient");
        workspace = createWorkspace();
        application = createApplication(workspace);
        applicationId = new UniqueId(application.getUniqueId());
        addMembership(application);
        login(actor);
        auditLogger = (Logger) LoggerFactory.getLogger("vg.identity.security.audit");
        audit = new ListAppender<>();
        audit.start();
        auditLogger.addAppender(audit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        messages.deleteAll();
        auditLogger.detachAppender(audit);
        audit.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLICATION", "WORKSPACE", "OWNER"})
    void messaging_whenGrantedAtSupportedScope_allowsSubmissionAndLookup(String scope) {
        if (scope.equals("OWNER")) {
            loginOwner();
        } else {
            var resourceId = scope.equals("WORKSPACE") ? workspace.getUniqueId() : application.getUniqueId();
            assignRole(actor, resourceId, createRole(workspace, Permission.App.MESSAGE_SEND));
            assignRole(actor, resourceId, createRole(workspace, Permission.App.MESSAGE_READ));
        }

        var message = service.submitMessage(applicationId, recipient.getUniqueId(), request()).message();

        assertThat(message.status()).isEqualTo(IdentityMessageStatus.QUEUED);
        assertThat(service.findMessage(applicationId, message.messageUniqueId())).contains(message);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        verifyNoInteractions(telegram);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void messaging_whenOnlyReadAndUpdateGranted_preservesMemberListingAndDeniesMessaging(boolean applicationCaller) {
        var principal = applicationCaller ? machinePrincipal() : actor;
        login(principal);
        grant(principal.getUniqueId(), application.getUniqueId(), Permission.App.READ);
        grant(principal.getUniqueId(), application.getUniqueId(), Permission.App.UPDATE);

        assertThat(members.findUsers(applicationId)).hasSize(1);
        assertBothDenied();
        assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void messaging_whenSendAndReadAreSeparate_checksPermissionOnEveryInvocationIncludingReplay(boolean applicationCaller) {
        var principal = applicationCaller ? machinePrincipal() : actor;
        login(principal);
        grant(principal.getUniqueId(), application.getUniqueId(), Permission.App.MESSAGE_SEND);
        var request = request();
        var created = service.submitMessage(applicationId, recipient.getUniqueId(), request);
        applicationUserRepository.deleteAll();
        assertThat(service.submitMessage(applicationId, recipient.getUniqueId(), request).replayed()).isTrue();
        assertThatThrownBy(() -> service.findMessage(applicationId, created.message().messageUniqueId()))
                .isInstanceOf(AccessDeniedException.class);

        roleAssignmentRepository.deleteAll();
        grant(principal.getUniqueId(), application.getUniqueId(), Permission.App.MESSAGE_READ);

        assertThat(service.findMessage(applicationId, created.message().messageUniqueId())).contains(created.message());
        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(), request))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        assertThat(acceptedEvents()).hasSize(2);
    }

    @Test
    void messaging_whenGrantBelongsToAnotherApplication_deniesBothOperations() {
        var other = createApplication(workspace);
        assignRole(actor, other.getUniqueId(), createRole(workspace, Permission.App.MESSAGE_SEND));
        assignRole(actor, other.getUniqueId(), createRole(workspace, Permission.App.MESSAGE_READ));

        assertBothDenied();
        assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLICATION", "WORKSPACE", "OWNER"})
    void messaging_whenApplicationPrincipalHasAuthority_allowsSubmissionReplayLookupAndAuditsCaller(String scope) {
        var principal = machinePrincipal();
        login(principal);
        assertBothDenied();
        assertNoMessages();
        if (scope.equals("OWNER")) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
        } else {
            var resourceId = scope.equals("WORKSPACE") ? workspace.getUniqueId() : application.getUniqueId();
            grant(principal.getUniqueId(), resourceId, Permission.App.MESSAGE_SEND);
            grant(principal.getUniqueId(), resourceId, Permission.App.MESSAGE_READ);
        }
        var request = request();

        var created = service.submitMessage(applicationId, recipient.getUniqueId(), request);
        var replay = service.submitMessage(applicationId, recipient.getUniqueId(), request);

        assertThat(created.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.message()).isEqualTo(created.message());
        assertThat(service.findMessage(applicationId, created.message().messageUniqueId())).contains(created.message());
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        assertThat(acceptedEvents()).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("actorUserUniqueId=" + principal.getUniqueId()));
        verifyNoInteractions(telegram);
    }

    @Test
    void messaging_whenApplicationHasGrantForAnotherApplication_usesExplicitSelectedApplication() {
        var principal = machinePrincipal();
        var selected = createApplication(workspace);
        addMembership(selected);
        var selectedId = new UniqueId(selected.getUniqueId());
        grant(principal.getUniqueId(), selected.getUniqueId(), Permission.App.MESSAGE_SEND);
        grant(principal.getUniqueId(), selected.getUniqueId(), Permission.App.MESSAGE_READ);
        login(principal);
        assertBothDenied();
        assertNoMessages();

        var message = service.submitMessage(selectedId, recipient.getUniqueId(), request()).message();

        assertThat(service.findMessage(selectedId, message.messageUniqueId())).contains(message);
        assertThat(messages.findById(message.messageUniqueId()).orElseThrow().getApplicationUniqueId())
                .isEqualTo(selected.getUniqueId());
        assertThatThrownBy(() -> service.findMessage(applicationId, message.messageUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void messaging_whenPrincipalIsResolvedByUsername_usesResolvedIdentifierForPermissionsAndAudit() {
        grant(Permission.App.MESSAGE_SEND);
        grant(Permission.App.MESSAGE_READ);
        login(User.withUsername(actor.getUsername()).password("unused").authorities(List.of()).build());

        var message = service.submitMessage(applicationId, recipient.getUniqueId(), request()).message();

        assertThat(service.findMessage(applicationId, message.messageUniqueId())).contains(message);
        assertThat(acceptedEvents()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("actorUserUniqueId=" + actor.getUniqueId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void messaging_whenAnonymousTokenResolvesGrantedPrincipal_usesExistingPermissionRules(boolean owner) {
        grant(Permission.App.MESSAGE_SEND);
        grant(Permission.App.MESSAGE_READ);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test", actor,
                List.of(new SimpleGrantedAuthority(owner ? "ROLE_OWNER" : "ROLE_ANONYMOUS"))));

        var message = service.submitMessage(applicationId, recipient.getUniqueId(), request()).message();

        assertThat(service.findMessage(applicationId, message.messageUniqueId())).contains(message);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "ANONYMOUS", "UNAUTHENTICATED", "APPLICATION", "OWNER_WITHOUT_ID"})
    void messaging_whenCallerHasNoIdentifierOrPermission_deniesBothOperations(String principal) {
        grant(Permission.App.MESSAGE_SEND);
        grant(Permission.App.MESSAGE_READ);
        switch (principal) {
            case "NONE" -> SecurityContextHolder.clearContext();
            case "ANONYMOUS" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            case "UNAUTHENTICATED" -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.unauthenticated(actor, null));
            case "APPLICATION" -> login(machinePrincipal());
            case "OWNER_WITHOUT_ID" -> {
                var user = IdentityInternalUser.builder().username("bootstrap")
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_OWNER"))).build();
                login(user);
            }
            default -> throw new IllegalArgumentException("Unknown test principal");
        }

        assertBothDenied();
        assertNoMessages();
    }

    @Test
    void submitMessage_whenRequestOrRecipientIsNull_rejectsWithoutPersistenceOrAcceptanceAudit() {
        grant(Permission.App.MESSAGE_SEND);

        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submitMessage(applicationId, null, request()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(),
                IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).build()))
                .isInstanceOf(IllegalArgumentException.class);

        assertNoMessages();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\n"})
    void submitMessage_whenTextIsInvalid_rejectsWithoutPersistenceOrAcceptanceAudit(String text) {
        grant(Permission.App.MESSAGE_SEND);

        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(),
                IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text(text).build()).idempotencyKey(UUID.randomUUID()).build()))
                .isInstanceOf(IllegalArgumentException.class);

        assertNoMessages();
    }

    @Test
    void submitMessage_whenRecipientIsUnknownOrNonmember_preservesNeutralRejection() {
        grant(Permission.App.MESSAGE_SEND);
        var nonmember = createIdentityUser("nonmember");

        for (var userId : List.of(nonmember.getUniqueId(), nextUniqueId())) {
            assertThatThrownBy(() -> service.submitMessage(applicationId, userId, request()))
                    .isInstanceOf(IdentityMessageDeliveryUnavailableException.class)
                    .hasMessage("Message delivery is unavailable");
        }
        assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void findMessage_whenMembershipRemovedAndBothApplicationsReadable_returnsHistoryFromEitherSource(boolean humanSource) {
        grant(Permission.App.MESSAGE_SEND);
        grant(Permission.App.MESSAGE_READ);
        login(humanSource ? actor : machinePrincipal());
        var message = humanSource ? service.submitMessage(applicationId, recipient.getUniqueId(), request()).message()
                : applicationApi.submitMessage(recipient.getUniqueId(), request()).message();
        login(actor);
        applicationUserRepository.deleteAll();
        var other = createApplication(workspace);
        assignRole(actor, other.getUniqueId(), createRole(workspace, Permission.App.MESSAGE_READ));

        assertThat(service.findMessage(applicationId, message.messageUniqueId())).contains(message);
        assertThat(service.findMessage(applicationId, nextUniqueId())).isEmpty();
        assertThat(service.findMessage(new UniqueId(other.getUniqueId()), message.messageUniqueId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void submitMessage_whenApplicationAndHumanReuseKey_sharesReceiptAndRejectsDifferentContent(boolean humanFirst) {
        grant(Permission.App.MESSAGE_SEND);
        var machine = machinePrincipal();
        var request = request();
        login(humanFirst ? actor : machine);
        var created = humanFirst ? service.submitMessage(applicationId, recipient.getUniqueId(), request)
                : applicationApi.submitMessage(recipient.getUniqueId(), request);
        login(humanFirst ? machine : actor);
        var replay = humanFirst ? applicationApi.submitMessage(recipient.getUniqueId(), request)
                : service.submitMessage(applicationId, recipient.getUniqueId(), request);

        assertThat(created.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.message()).isEqualTo(created.message());
        login(actor);
        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(),
                IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("different").build()).idempotencyKey(request.idempotencyKey()).build()))
                .isInstanceOf(IdentityMessageIdempotencyConflictException.class);
        var otherMember = createIdentityUser("another-member");
        assertThatThrownBy(() -> service.submitMessage(applicationId, otherMember.getUniqueId(), request))
                .isInstanceOf(IdentityMessageIdempotencyConflictException.class);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        assertThat(acceptedEvents()).hasSize(1);
    }

    @Test
    void submitMessage_whenApplicationAndHumanRaceWithSameKey_createsOneMessageAndCommand() throws Exception {
        grant(Permission.App.MESSAGE_SEND);
        var machine = machinePrincipal();
        var request = request();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var human = executor.submit(() -> {
                login(actor);
                try {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return service.submitMessage(applicationId, recipient.getUniqueId(), request);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var app = executor.submit(() -> {
                login(machine);
                try {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return applicationApi.submitMessage(recipient.getUniqueId(), request);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = human.get(20, TimeUnit.SECONDS);
            var second = app.get(20, TimeUnit.SECONDS);
            assertThat(first.message()).isEqualTo(second.message());
            assertThat(first.replayed()).isNotEqualTo(second.replayed());
        }
        assertThat(messages.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        assertThat(acceptedEvents()).hasSize(1);
    }

    @Test
    void submitMessage_whenOuterTransactionCommits_auditsCreationAndReplayAfterCommitOnly() {
        grant(Permission.App.MESSAGE_SEND);
        var request = request();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var created = service.submitMessage(applicationId, recipient.getUniqueId(), request);
            service.submitMessage(applicationId, recipient.getUniqueId(), request);
            assertThat(created.replayed()).isFalse();
            assertThat(acceptedEvents()).isEmpty();
        });

        var id = new UniqueId(messages.findAll().getFirst().getUniqueId());
        assertThat(acceptedEvents()).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("actorUserUniqueId=" + actor.getUniqueId(), "applicationUniqueId=" + applicationId,
                        "userUniqueId=" + recipient.getUniqueId(), "messageUniqueId=" + id)
                .doesNotContain(request.content().text(), request.idempotencyKey().toString()));
        assertThat(acceptedEvents().getFirst().getFormattedMessage()).contains("outcome=CREATED");
        assertThat(acceptedEvents().getLast().getFormattedMessage()).contains("outcome=REPLAYED");
    }

    @Test
    void submitMessage_whenOuterTransactionRollsBack_discardsMessageCommandAndAcceptanceAudit() {
        grant(Permission.App.MESSAGE_SEND);
        var request = request();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.submitMessage(applicationId, recipient.getUniqueId(), request);
            assertThat(messages.count()).isEqualTo(1);
            assertThat(commandRepository.count()).isEqualTo(1);
            assertThat(acceptedEvents()).isEmpty();
            status.setRollbackOnly();
        });

        assertNoMessages();
        assertThat(service.submitMessage(applicationId, recipient.getUniqueId(), request).replayed()).isFalse();
    }

    @Test
    void submitMessage_whenQueueInsertionFails_rollsBackMessageAndDoesNotAudit() {
        grant(Permission.App.MESSAGE_SEND);
        doThrow(new IllegalStateException("Queue unavailable")).when(commands).enqueueApplicationMessage(any());

        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(), request()))
                .hasMessage("Queue unavailable");

        assertNoMessages();
    }

    @Test
    void delivery_whenSenderPermissionRevoked_usesSelectedApplicationsCurrentBotAndSendsOnce() {
        grant(Permission.App.MESSAGE_SEND);
        var other = createApplication(workspace, "{\"telegramBotId\":200,\"token\":\"other-token\"}");
        makeBot(other, "other_bot");
        application.setPayload("{\"telegramBotId\":100,\"token\":\"old-token\"}");
        application = applicationRepository.saveAndFlush(application);
        makeBot(application, "selected_bot");
        var user = userRepository.findById(recipient.getUniqueId()).orElseThrow();
        user.setConsentToKeepPersonalDataAt(Instant.now());
        userRepository.saveAndFlush(user);
        channelRepository.saveWithNewUniqueId(IdentityUserChannelEntity.builder().identityUser(user)
                .channelType(IdentityChannelType.TELEGRAM_USER).channelUserId("42")
                .channelUserIdHash(encryption.hashCaseSensitive("42")).verifiedAt(Instant.now()).build(), uniqueIdService);
        var request = request();
        var message = service.submitMessage(applicationId, recipient.getUniqueId(), request).message();
        roleAssignmentRepository.deleteAll();
        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(), request))
                .isInstanceOf(AccessDeniedException.class);
        application.setPayload("{\"telegramBotId\":101,\"token\":\"current-token\"}");
        applicationRepository.saveAndFlush(application);
        var sends = new AtomicInteger();
        when(telegram.sendMessage("current-token", 42L, request.content().text())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(core.find(applicationId, message.messageUniqueId()).orElseThrow().status()).isEqualTo(IdentityMessageStatus.SENDING);
            sends.incrementAndGet();
            return TelegramApiClient.SendMessageResponse.builder().ok(true)
                    .result(TelegramApiClient.SentMessage.builder().messageId(7L).build()).build();
        });

        worker().processNextQueuedCommand();
        commands.enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(message.messageUniqueId()).build());
        worker().processNextQueuedCommand();

        assertThat(core.find(applicationId, message.messageUniqueId()).orElseThrow().status()).isEqualTo(IdentityMessageStatus.SENT);
        assertThat(sends.get()).isEqualTo(1);
        assertThat(commandRepository.findAll()).allSatisfy(command ->
                assertThat(command.getCommandStatus()).isEqualTo(IdentityCommandStatus.COMPLETED));
    }

    @Test
    void delivery_whenAttemptWasInterrupted_retainsSendingAndNeverResends() {
        grant(Permission.App.MESSAGE_SEND);
        var message = service.submitMessage(applicationId, recipient.getUniqueId(), request()).message();
        // Model a process exit after the preparation transaction committed and before outcome persistence.
        core.begin(message.messageUniqueId());

        worker().processNextQueuedCommand();
        commands.enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(message.messageUniqueId()).build());
        worker().processNextQueuedCommand();

        assertThat(core.find(applicationId, message.messageUniqueId()).orElseThrow().status()).isEqualTo(IdentityMessageStatus.SENDING);
        verifyNoInteractions(telegram);
    }

    @Test
    void findMessages_whenReadIsGranted_returnsOnlyApplicationHistoryAfterMembershipRemoval() throws Exception {
        var first = core.submit(applicationId, recipient.getUniqueId(), request()).message();
        var second = core.submit(applicationId, recipient.getUniqueId(), request()).message();
        var third = core.submit(applicationId, recipient.getUniqueId(), request()).message();
        var other = createApplication(workspace);
        addMembership(other);
        core.submit(new UniqueId(other.getUniqueId()), recipient.getUniqueId(), request());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager.createNativeQuery(
                        "update identity_message set created_at = :createdAt where application_unique_id = :applicationId")
                .setParameter("createdAt", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))
                .setParameter("applicationId", applicationId.getLongValue()).executeUpdate());
        applicationUserRepository.deleteAll();
        grant(Permission.App.MESSAGE_READ);

        var firstPage = service.findMessages(applicationId, 0, 2);
        var secondPage = service.findMessages(applicationId, 1, 2);

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(message -> message.messageUniqueId())
                .containsExactly(third.messageUniqueId(), second.messageUniqueId());
        assertThat(secondPage.getContent()).extracting(message -> message.messageUniqueId())
                .containsExactly(first.messageUniqueId());
        assertThat(service.findMessages(applicationId, 2, 2).getContent()).isEmpty();
        assertThat(objectMapper.writeValueAsString(firstPage.getContent())).doesNotContain("hello", "content", "idempotencyKey");
        assertThatThrownBy(() -> service.findMessages(new UniqueId(other.getUniqueId()), 0, 2))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(telegram);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLICATION", "WORKSPACE", "OWNER"})
    void findMessages_whenReadIsGrantedAtSupportedScope_allowsEmptyHistory(String scope) {
        if (scope.equals("OWNER")) {
            loginOwner();
        } else {
            var resourceId = scope.equals("WORKSPACE") ? workspace.getUniqueId() : application.getUniqueId();
            assignRole(actor, resourceId, createRole(workspace, Permission.App.MESSAGE_READ));
        }

        assertThat(service.findMessages(applicationId, 0, 50).getContent()).isEmpty();
        assertThat(service.findMessages(applicationId, 0, 50).getTotalElements()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {Permission.App.READ, Permission.App.MESSAGE_SEND})
    void findMessages_whenReadIsNotGranted_deniesEvenWithOtherApplicationPermissions(String permission) {
        grant(permission);

        assertThatThrownBy(() -> service.findMessages(applicationId, 0, 50))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void grant(String permission) {
        grant(actor.getUniqueId(), application.getUniqueId(), permission);
    }

    private void grant(UniqueId principalUniqueId, long resourceUniqueId, String permission) {
        roleAssignmentRepository.save(IdentityRoleAssignmentEntity.builder()
                .principal(principalRepository.findById(principalUniqueId).orElseThrow())
                .resourceUniqueId(resourceUniqueId).role(createRole(workspace, permission)).build());
    }

    private void addMembership(IdentityApplicationEntity app) {
        applicationUserRepository.saveAndFlush(IdentityApplicationUserEntity.builder().application(app)
                .identityUser(userRepository.findById(recipient.getUniqueId()).orElseThrow())
                .lastAuthenticatedAt(Instant.now()).build());
    }

    private void makeBot(IdentityApplicationEntity app, String username) {
        var principal = principalRepository.findById(app.getUniqueId()).orElseThrow();
        principal.setName("https://t.me/" + username);
        principal.setNameHash(encryption.hashPrincipalName(principal.getName()));
        principalRepository.saveAndFlush(principal);
    }

    private IdentityInternalApplicationPrincipal machinePrincipal() {
        var previous = SecurityContextHolder.getContext();
        try {
            loginOwner();
            var key = apiKeys.issueForApplication(applicationId, "messaging-test", clock.instant().plusSeconds(3600));
            return apiKeys.authenticate(key.value()).orElseThrow();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void loginOwner() {
        login(IdentityInternalUser.builder().uniqueId(actor.getUniqueId()).username(actor.getUsername())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_OWNER"))).build());
    }

    private static void login(UserDetails principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities() == null ? List.of() : principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private void assertBothDenied() {
        assertThatThrownBy(() -> service.submitMessage(applicationId, recipient.getUniqueId(), request()))
                .isInstanceOfAny(AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.findMessage(applicationId, nextUniqueId()))
                .isInstanceOfAny(AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.findMessages(applicationId, 0, 50))
                .isInstanceOfAny(AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class);
    }

    private void assertNoMessages() {
        assertThat(messages.count()).isZero();
        assertThat(commandRepository.count()).isZero();
        assertThat(acceptedEvents()).isEmpty();
    }

    private List<ILoggingEvent> acceptedEvents() {
        return audit.list.stream().filter(event -> event.getFormattedMessage().startsWith("event=message.user.submit ")).toList();
    }

    private IdentityCommandWorker worker() {
        return new IdentityCommandWorker(commands, email, objectMapper, webhooks, processor);
    }

    private static IdentityApplicationMessageRequest request() {
        return IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("private message body").build()).idempotencyKey(UUID.randomUUID()).build();
    }
}

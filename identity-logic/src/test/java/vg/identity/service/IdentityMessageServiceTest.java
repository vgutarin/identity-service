package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityMessageEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.*;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.identity.model.application.TelegramBot;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityMessageRepository;
import vg.identity.repository.IdentityMessageSubmissionRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityMessageServiceTest {
    private static final UniqueId APP = new UniqueId(10L);
    private static final UniqueId USER = new UniqueId(20L);
    private static final UniqueId MESSAGE = new UniqueId(30L);
    @Mock private IdentityMessageRepository messages;
    @Mock private IdentityMessageSubmissionRepository submissions;
    @Mock private IdentityApplicationRepository applications;
    @Mock private IdentityApplicationUserService memberships;
    @Mock private IdentityCommandService commands;
    @Mock private UniqueIdService ids;
    @Mock private TelegramBotLookupService bots;
    @Mock private IdentityUserChannelRepository channels;
    private IdentityMessageService service;

    @BeforeEach
    void setUp() {
        service = new IdentityMessageService(messages, submissions, applications, memberships, commands, ids, bots, channels);
    }

    @Test
    void submit_whenUserIsAnApplicationMember_insertsMessageAndQueuesOnlyItsId() {
        var request = request();
        when(applications.lockForMessageSubmission(10L)).thenReturn(Optional.of(IdentityApplicationEntity.builder().build()));
        when(ids.getNext()).thenReturn(MESSAGE);
        when(submissions.insertOrGet(MESSAGE, APP, USER, request)).thenReturn(snapshot(IdentityMessageStatus.QUEUED));
        when(memberships.hasMembership(APP, USER)).thenReturn(true);

        var submitted = service.submit(APP, USER, request);

        assertThat(submitted.replayed()).isFalse();
        assertThat(submitted.message().messageUniqueId()).isEqualTo(MESSAGE);
        assertThat(submitted.message().status()).isEqualTo(IdentityMessageStatus.QUEUED);
        verify(commands).enqueueApplicationMessage(SendApplicationMessageCommand.builder().messageUniqueId(MESSAGE).build());
        verifyNoInteractions(messages, bots, channels);
    }

    @Test
    void submit_whenArgumentsAreMissing_rejectsWithoutPersistence() {
        assertThatThrownBy(() -> service.submit(APP, null, request())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(APP, USER, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(applications, messages, submissions, ids, memberships, commands, bots, channels);
    }

    @Test
    void submit_whenUserIsNotMember_rejectsWithoutQueueing() {
        var request = request();
        when(applications.lockForMessageSubmission(10L)).thenReturn(Optional.of(IdentityApplicationEntity.builder().build()));
        when(ids.getNext()).thenReturn(MESSAGE);
        when(submissions.insertOrGet(MESSAGE, APP, USER, request)).thenReturn(snapshot(IdentityMessageStatus.QUEUED));

        assertThatThrownBy(() -> service.submit(APP, USER, request)).isInstanceOf(IdentityMessageDeliveryUnavailableException.class);

        verifyNoInteractions(messages, commands, bots, channels);
    }

    @Test
    void submit_whenKeyMatchesExistingMessage_returnsReplayWithoutRecheckingMembershipOrQueueing() {
        var request = request();
        var candidate = new UniqueId(31L);
        when(applications.lockForMessageSubmission(10L)).thenReturn(Optional.of(IdentityApplicationEntity.builder().build()));
        when(ids.getNext()).thenReturn(candidate);
        when(submissions.insertOrGet(candidate, APP, USER, request)).thenReturn(snapshot(IdentityMessageStatus.SENT));

        var replay = service.submit(APP, USER, request);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.message().status()).isEqualTo(IdentityMessageStatus.SENT);
        verifyNoInteractions(messages, memberships, commands, bots, channels);
    }

    @Test
    void submit_whenKeyIsReusedForDifferentTextOrRecipient_throwsIdempotencyConflict() {
        var key = UUID.randomUUID();
        var candidate = new UniqueId(31L);
        when(applications.lockForMessageSubmission(10L)).thenReturn(Optional.of(IdentityApplicationEntity.builder().build()));
        when(ids.getNext()).thenReturn(candidate);
        when(submissions.insertOrGet(eq(candidate), eq(APP), any(), any())).thenReturn(snapshot(IdentityMessageStatus.QUEUED));

        assertThatThrownBy(() -> service.submit(APP, USER, IdentityApplicationMessageRequest.builder()
                .content(MessageContent.builder().text("different").build()).idempotencyKey(key).build()))
                .isInstanceOf(IdentityMessageIdempotencyConflictException.class);
        assertThatThrownBy(() -> service.submit(APP, new UniqueId(21L), request(key)))
                .isInstanceOf(IdentityMessageIdempotencyConflictException.class);
        verifyNoInteractions(messages, memberships, commands);
    }

    @Test
    void find_whenApplicationOwnsMessage_returnsItWithoutCheckingMembership() {
        when(messages.findByUniqueIdAndApplicationUniqueId(30L, 10L)).thenReturn(Optional.of(entity(IdentityMessageStatus.QUEUED)));
        assertThat(service.find(APP, MESSAGE)).isPresent();
        assertThat(service.find(new UniqueId(11L), MESSAGE)).isEmpty();
        verifyNoInteractions(memberships, commands);
    }

    @Test
    void findPage_whenApplicationHasMessages_returnsSafeMetadataWithStablePaging() {
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt", "uniqueId"));
        when(messages.findByApplicationUniqueId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(entity(IdentityMessageStatus.SENT)), pageable, 3));

        var result = service.findPage(APP, 1, 2);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).singleElement().satisfies(message -> {
            assertThat(message.messageUniqueId()).isEqualTo(MESSAGE);
            assertThat(message.status()).isEqualTo(IdentityMessageStatus.SENT);
        });
        verifyNoInteractions(memberships, commands, bots, channels);
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,-1", "0,101"})
    void findPage_whenPagingIsInvalid_rejectsBeforeQuery(int page, int pageSize) {
        assertThatThrownBy(() -> service.findPage(APP, page, pageSize)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messages);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 100})
    void begin_whenTelegramRouteIsEligible_preparesDeliveryRegardlessOfStoredBotId(Long botId) {
        var entity = entity(IdentityMessageStatus.QUEUED);
        when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity));
        when(memberships.hasMembership(APP, USER)).thenReturn(true);
        when(bots.findMessagingBot(APP)).thenReturn(TelegramBot.builder().telegramBotId(botId).token("current-token").build());
        when(channels.findByIdentityUser_UniqueIdAndChannelTypeOrderByUpdatedAtDesc(20L, IdentityChannelType.TELEGRAM_USER))
                .thenReturn(List.of(channel()));

        var prepared = service.begin(MESSAGE).orElseThrow();

        assertThat(entity.getStatus()).isEqualTo(IdentityMessageStatus.SENDING);
        assertThat(prepared.messageUniqueId()).isEqualTo(MESSAGE);
        assertThat(prepared.chatId()).isEqualTo(42L);
        assertThat(prepared.token()).isEqualTo("current-token");
        assertThat(prepared.failureCode()).isNull();
        assertThat(prepared.toString()).doesNotContain("current-token", "hello");
        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t"})
    void begin_whenTokenIsMissingOrBlank_returnsUnavailableWithoutLookingUpChannels(String token) {
        var entity = entity(IdentityMessageStatus.QUEUED);
        when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity));
        when(memberships.hasMembership(APP, USER)).thenReturn(true);
        when(bots.findMessagingBot(APP)).thenReturn(TelegramBot.builder().token(token).build());

        var prepared = service.begin(MESSAGE).orElseThrow();

        assertThat(prepared.failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        verifyNoInteractions(channels, commands);
    }

    @Test
    void begin_whenMessageIsAlreadyStartedOrFinal_returnsEmptyWithoutPreparingDelivery() {
        for (var status : List.of(IdentityMessageStatus.SENDING, IdentityMessageStatus.SENT, IdentityMessageStatus.FAILED, IdentityMessageStatus.UNKNOWN)) {
            when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity(status)));
            assertThat(service.begin(MESSAGE)).isEmpty();
        }
        verifyNoInteractions(memberships, bots, channels, commands);
    }

    @Test
    void begin_whenMembershipOrBotIsMissing_returnsDeliveryUnavailable() {
        var entity = entity(IdentityMessageStatus.QUEUED);
        when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity));
        assertThat(service.begin(MESSAGE).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        entity.setStatus(IdentityMessageStatus.QUEUED);
        when(memberships.hasMembership(APP, USER)).thenReturn(true);
        assertThat(service.begin(MESSAGE).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        verifyNoInteractions(channels);
    }

    @Test
    void begin_whenChannelIsIneligibleOrBotPayloadIsMalformed_returnsSafeFailureCode() {
        var entity = entity(IdentityMessageStatus.QUEUED);
        when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity));
        when(memberships.hasMembership(APP, USER)).thenReturn(true);
        when(bots.findMessagingBot(APP)).thenReturn(TelegramBot.builder().telegramBotId(100L).token("token").build());
        var unverified = channel(); unverified.setVerifiedAt(null);
        var noConsent = channel(); noConsent.getIdentityUser().setConsentToKeepPersonalDataAt(null);
        var reassigned = channel(); reassigned.getIdentityUser().setUniqueId(21L);
        var badRecipient = channel(); badRecipient.setChannelUserId("@group");
        for (var candidates : List.of(List.<IdentityUserChannelEntity>of(), List.of(unverified), List.of(noConsent), List.of(reassigned), List.of(badRecipient))) {
            entity.setStatus(IdentityMessageStatus.QUEUED);
            when(channels.findByIdentityUser_UniqueIdAndChannelTypeOrderByUpdatedAtDesc(20L, IdentityChannelType.TELEGRAM_USER)).thenReturn(candidates);
            assertThat(service.begin(MESSAGE).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        }
        entity.setStatus(IdentityMessageStatus.QUEUED);
        when(bots.findMessagingBot(APP)).thenThrow(new IllegalArgumentException("private configuration"));
        assertThat(service.begin(MESSAGE).orElseThrow().failureCode()).isEqualTo(IdentityMessageFailureCode.INTERNAL_ERROR);
    }

    @Test
    void finish_whenOutcomeIsSentOrUnknown_recordsMessageWithoutChangingCommand() {
        var entity = entity(IdentityMessageStatus.SENDING);
        when(messages.findForUpdate(30L)).thenReturn(Optional.of(entity));
        service.finish(MESSAGE, IdentityMessageStatus.SENT, null);
        assertThat(entity.getStatus()).isEqualTo(IdentityMessageStatus.SENT);
        entity.setStatus(IdentityMessageStatus.SENDING);
        service.finish(MESSAGE, IdentityMessageStatus.UNKNOWN, IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        assertThat(entity.getStatus()).isEqualTo(IdentityMessageStatus.UNKNOWN);
        assertThat(entity.getFailureCode()).isEqualTo(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        verifyNoInteractions(commands);
    }

    @Test
    void finish_whenOutcomeIsNonterminalOrFailureCodeMismatches_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.finish(MESSAGE, IdentityMessageStatus.SENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.finish(MESSAGE, IdentityMessageStatus.SENT, IdentityMessageFailureCode.INTERNAL_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.finish(MESSAGE, IdentityMessageStatus.FAILED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.finish(MESSAGE, IdentityMessageStatus.UNKNOWN, IdentityMessageFailureCode.INTERNAL_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.finish(MESSAGE, IdentityMessageStatus.FAILED, IdentityMessageFailureCode.DELIVERY_UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messages, commands);
    }

    private static IdentityMessageSnapshot snapshot(IdentityMessageStatus status) {
        return IdentityMessageSnapshot.builder().message(IdentityApplicationMessage.builder()
                        .messageUniqueId(MESSAGE).userUniqueId(USER).status(status)
                        .createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build())
                .content(MessageContent.builder().text("hello").build()).build();
    }
    private static IdentityMessageEntity entity(IdentityMessageStatus status) {
        return IdentityMessageEntity.builder().uniqueId(30L).applicationUniqueId(10L).userUniqueId(20L)
                .content(MessageContent.builder().text("hello").build()).status(status).createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build();
    }
    private static IdentityApplicationMessageRequest request() {
        return request(UUID.randomUUID());
    }
    private static IdentityApplicationMessageRequest request(UUID key) {
        return IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(key).build();
    }
    private static IdentityUserChannelEntity channel() {
        return IdentityUserChannelEntity.builder().uniqueId(40L).channelType(IdentityChannelType.TELEGRAM_USER)
                .channelUserId("42").verifiedAt(Instant.EPOCH)
                .identityUser(IdentityUserEntity.builder().uniqueId(20L).consentToKeepPersonalDataAt(Instant.EPOCH).build()).build();
    }
}

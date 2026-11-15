package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageImpl;
import vg.identity.audit.IdentityMessageSubmissionAuditor;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.MessageContent;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.IdentityMessageIdempotencyConflictException;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.IdentityMessageSubmission;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationMessageServiceTest {
    private static final UniqueId ACTOR = new UniqueId(1L);
    private static final UniqueId APPLICATION = new UniqueId(2L);
    private static final UniqueId RECIPIENT = new UniqueId(3L);
    private static final UniqueId MESSAGE = new UniqueId(4L);
    @Mock private CurrentPrincipalService currentPrincipal;
    @Mock private IdentityMessageService messages;
    @Mock private IdentityMessageSubmissionAuditor auditor;
    private IdentityApplicationMessageService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationMessageService(currentPrincipal, messages, auditor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void submitMessage_whenHumanSubmitsOrReplays_delegatesSelectedApplicationAndReturnsUnchangedReceipt(boolean replayed) {
        authenticateHuman();
        var request = request();
        var result = IdentityMessageSubmission.builder().message(message()).replayed(replayed).build();
        when(messages.submit(APPLICATION, RECIPIENT, request)).thenReturn(result);

        assertThat(service.submitMessage(APPLICATION, RECIPIENT, request)).isSameAs(result);

        verify(auditor).acceptedAfterCommit(ACTOR, APPLICATION, RECIPIENT, MESSAGE, replayed);
    }

    @ParameterizedTest
    @MethodSource("submissionFailures")
    void submitMessage_whenCoreRejectsSubmission_preservesExceptionAndDoesNotAudit(RuntimeException error) {
        authenticateHuman();
        var request = request();
        when(messages.submit(APPLICATION, RECIPIENT, request)).thenThrow(error);

        assertThatThrownBy(() -> service.submitMessage(APPLICATION, RECIPIENT, request)).isSameAs(error);

        verifyNoInteractions(auditor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void findMessage_whenHumanLooksUpMessage_delegatesSelectedApplicationAndReturnsUnchangedResult(boolean present) {
        authenticateHuman();
        var result = present ? Optional.of(message()) : Optional.<IdentityApplicationMessage>empty();
        when(messages.find(APPLICATION, MESSAGE)).thenReturn(result);

        assertThat(service.findMessage(APPLICATION, MESSAGE)).isSameAs(result);

        verifyNoInteractions(auditor);
    }

    @Test
    void messaging_whenCurrentPrincipalGuardDenies_propagatesDenialBeforeDelegating() {
        var error = new AccessDeniedException("An authenticated principal identifier is required");
        when(currentPrincipal.requirePrincipalUniqueId()).thenThrow(error);

        assertThatThrownBy(() -> service.submitMessage(APPLICATION, RECIPIENT, request()))
                .isSameAs(error);
        assertThatThrownBy(() -> service.findMessage(APPLICATION, MESSAGE)).isSameAs(error);
        assertThatThrownBy(() -> service.findMessages(APPLICATION, 0, 50)).isSameAs(error);
        verifyNoInteractions(messages, auditor);
    }

    @Test
    void findMessages_whenPrincipalIsResolved_delegatesPageWithoutAuditing() {
        authenticateHuman();
        var result = new PageImpl<>(List.of(message()));
        when(messages.findPage(APPLICATION, 2, 50)).thenReturn(result);

        assertThat(service.findMessages(APPLICATION, 2, 50)).isSameAs(result);

        verifyNoInteractions(auditor);
    }

    private void authenticateHuman() {
        when(currentPrincipal.requirePrincipalUniqueId()).thenReturn(ACTOR);
    }

    private static Stream<RuntimeException> submissionFailures() {
        return Stream.of(new IllegalArgumentException("Invalid request"),
                new IdentityMessageDeliveryUnavailableException(), new IdentityMessageIdempotencyConflictException());
    }

    private static IdentityApplicationMessage message() {
        return IdentityApplicationMessage.builder().messageUniqueId(MESSAGE).userUniqueId(RECIPIENT)
                .status(IdentityMessageStatus.UNKNOWN).build();
    }

    private static IdentityApplicationMessageRequest request() {
        return IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(UUID.randomUUID()).build();
    }
}

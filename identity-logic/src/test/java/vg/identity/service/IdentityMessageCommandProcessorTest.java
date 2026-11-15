package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.command.IdentityCommand;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.PreparedIdentityMessage;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityMessageCommandProcessorTest {
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private IdentityMessageService messages;
    @Mock
    private TelegramApiClient telegram;
    private IdentityMessageCommandProcessor processor;
    private static final UniqueId MESSAGE = new UniqueId(30L);
    private static final IdentityCommand COMMAND = IdentityCommand.builder().id(1L).payload("id-only").build();

    @BeforeEach
    void setUp() {
        processor = new IdentityMessageCommandProcessor(objectMapper, messages, telegram);
    }

    @Test
    void process_whenTelegramAcknowledgesMessage_recordsSentOutcome() {
        prepare();
        when(telegram.sendMessage("token", 42L, "hello")).thenReturn(TelegramApiClient.SendMessageResponse.builder().ok(true)
                .result(TelegramApiClient.SentMessage.builder().messageId(7L).build()).build());
        processor.process(COMMAND);
        verify(messages).finish(MESSAGE, IdentityMessageStatus.SENT, null);
    }

    @Test
    void process_whenTelegramRejectsMessage_recordsProviderRejectedFailure() {
        prepare();
        when(telegram.sendMessage("token", 42L, "hello"))
                .thenReturn(TelegramApiClient.SendMessageResponse.builder().ok(false).build());
        assertFailure(IdentityMessageFailureCode.PROVIDER_REJECTED);
        verify(messages).finish(MESSAGE, IdentityMessageStatus.FAILED, IdentityMessageFailureCode.PROVIDER_REJECTED);
    }

    @Test
    void process_whenTelegramAcknowledgementIsMissingOrMalformed_recordsUnknownOutcome() {
        prepare();
        for (var response : new TelegramApiClient.SendMessageResponse[]{null,
                TelegramApiClient.SendMessageResponse.builder().ok(true).build(),
                TelegramApiClient.SendMessageResponse.builder().ok(true)
                        .result(TelegramApiClient.SentMessage.builder().messageId(0L).build()).build()}) {
            when(telegram.sendMessage("token", 42L, "hello")).thenReturn(response);
            assertFailure(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        }
        verify(messages, times(3)).finish(MESSAGE, IdentityMessageStatus.UNKNOWN, IdentityMessageFailureCode.DELIVERY_UNKNOWN);
    }

    @Test
    void process_whenTelegramCallFails_distinguishesRejectionFromUncertainDelivery() {
        prepare();
        when(telegram.sendMessage("token", 42L, "hello"))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
                .thenThrow(new ResourceAccessException("private details"))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        assertFailure(IdentityMessageFailureCode.PROVIDER_REJECTED);
        assertFailure(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        assertFailure(IdentityMessageFailureCode.DELIVERY_UNKNOWN);
        verify(messages).finish(MESSAGE, IdentityMessageStatus.FAILED, IdentityMessageFailureCode.PROVIDER_REJECTED);
        verify(messages, times(2)).finish(MESSAGE, IdentityMessageStatus.UNKNOWN, IdentityMessageFailureCode.DELIVERY_UNKNOWN);
    }

    @Test
    void process_whenRouteIsUnavailable_recordsFailureWithoutCallingTelegram() {
        payload();
        when(messages.begin(MESSAGE)).thenReturn(Optional.of(PreparedIdentityMessage.builder().messageUniqueId(MESSAGE)
                .failureCode(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE).build()));
        assertFailure(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        verify(messages).finish(MESSAGE, IdentityMessageStatus.FAILED, IdentityMessageFailureCode.DELIVERY_UNAVAILABLE);
        verifyNoInteractions(telegram);
    }

    @Test
    void process_whenMessageAttemptHasAlreadyStarted_returnsWithoutSendingOrChangingMessage() {
        payload();
        when(messages.begin(MESSAGE)).thenReturn(Optional.empty());
        processor.process(COMMAND);
        verifyNoInteractions(telegram);
        verify(messages, never()).finish(any(), any(), any());
    }

    @Test
    void process_whenCommandPayloadIsMalformed_throwsSanitizedException() {
        when(objectMapper.readValue(COMMAND.payload(), SendApplicationMessageCommand.class)).thenThrow(new IllegalArgumentException("private"));
        assertThatThrownBy(() -> processor.process(COMMAND))
                .hasMessage("Cannot process application message command 1").hasNoCause();
        verifyNoInteractions(messages, telegram);
    }

    @Test
    void process_whenPreparationThrows_throwsSanitizedExceptionWithoutSending() {
        payload();
        when(messages.begin(MESSAGE)).thenThrow(new IllegalStateException("private token and message",
                new RuntimeException("private cause")));

        assertThatThrownBy(() -> processor.process(COMMAND))
                .hasMessage("Cannot process application message command 1").hasNoCause();
        verify(messages, never()).finish(any(), any(), any());
        verifyNoInteractions(telegram);
    }

    @Test
    void process_whenOutcomePersistenceThrows_throwsSanitizedExceptionWithoutRetry() {
        prepare();
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        when(telegram.sendMessage("token", 42L, "hello")).thenAnswer(call -> {
            sends.incrementAndGet();
            return TelegramApiClient.SendMessageResponse.builder().ok(true)
                    .result(TelegramApiClient.SentMessage.builder().messageId(7L).build()).build();
        });
        doThrow(new IllegalStateException("private persistence details"))
                .when(messages).finish(MESSAGE, IdentityMessageStatus.SENT, null);

        assertThatThrownBy(() -> processor.process(COMMAND))
                .hasMessage("Cannot process application message command 1").hasNoCause();
        org.assertj.core.api.Assertions.assertThat(sends.get()).isEqualTo(1);
    }

    private void assertFailure(IdentityMessageFailureCode code) {
        assertThatThrownBy(() -> processor.process(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Application message outcome: " + code).hasNoCause();
    }

    private void payload() {
        when(objectMapper.readValue(COMMAND.payload(), SendApplicationMessageCommand.class))
                .thenReturn(SendApplicationMessageCommand.builder().messageUniqueId(MESSAGE).build());
    }

    private void prepare() {
        payload();
        when(messages.begin(MESSAGE)).thenReturn(Optional.of(PreparedIdentityMessage.builder().messageUniqueId(MESSAGE)
                .token("token").chatId(42L).text("hello").build()));
    }
}

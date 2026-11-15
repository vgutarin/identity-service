package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.PreparedIdentityMessage;
import vg.identity.model.command.IdentityCommand;
import vg.identity.model.command.SendApplicationMessageCommand;

/** A single delivery attempt. No database transaction spans the Telegram call. */
@Service
@RequiredArgsConstructor
public class IdentityMessageCommandProcessor {
    private final ObjectMapper objectMapper;
    private final IdentityMessageService messages;
    private final TelegramApiClient telegram;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(IdentityCommand command) {
        IdentityMessageFailureCode failure;
        try {
            failure = processMessage(command);
        } catch (RuntimeException e) {
            // The generic worker logs and persists this exception; exclude all original details and causes.
            throw new IllegalStateException("Cannot process application message command " + command.id());
        }
        if (failure != null) {
            throw new IllegalStateException("Application message outcome: " + failure);
        }
    }

    private IdentityMessageFailureCode processMessage(IdentityCommand command) {
        var payload = objectMapper.readValue(command.payload(), SendApplicationMessageCommand.class);
        if (payload == null) {
            throw new IllegalArgumentException("Missing message command");
        }
        var attempt = messages.begin(payload.messageUniqueId());
        if (attempt.isEmpty()) {
            // SENDING and terminal messages are never sent again, including through a duplicate queue job.
            return null;
        }
        var prepared = attempt.orElseThrow();
        var failure = prepared.failureCode();
        if (failure == null) {
            failure = send(prepared);
        }
        var status = failure == null ? IdentityMessageStatus.SENT
                : failure == IdentityMessageFailureCode.DELIVERY_UNKNOWN ? IdentityMessageStatus.UNKNOWN : IdentityMessageStatus.FAILED;
        messages.finish(payload.messageUniqueId(), status, failure);
        return failure;
    }

    private IdentityMessageFailureCode send(PreparedIdentityMessage prepared) {
        try {
            var response = telegram.sendMessage(prepared.token(), prepared.chatId(), prepared.text());
            if (response != null && Boolean.FALSE.equals(response.ok())) {
                return IdentityMessageFailureCode.PROVIDER_REJECTED;
            }
            if (response == null || !Boolean.TRUE.equals(response.ok()) || response.result() == null
                    || response.result().messageId() == null || response.result().messageId() <= 0) {
                return IdentityMessageFailureCode.DELIVERY_UNKNOWN;
            }
            return null;
        } catch (HttpClientErrorException e) {
            return IdentityMessageFailureCode.PROVIDER_REJECTED;
        } catch (RuntimeException e) {
            // Transport/server/conversion failures do not establish whether Telegram accepted the send.
            return IdentityMessageFailureCode.DELIVERY_UNKNOWN;
        }
    }
}

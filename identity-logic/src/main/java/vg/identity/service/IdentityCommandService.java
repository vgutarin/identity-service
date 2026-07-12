package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.mapper.IdentityCommandMapper;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityCommand;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;
import vg.identity.repository.IdentityCommandRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class IdentityCommandService {
    private static final int MAX_ERROR_STACK_TRACE_LENGTH = 15 * 1024;

    private final IdentityCommandRepository commandRepository;
    private final IdentityCommandMapper commandMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private long maxStartedCommandId;

    @Transactional
    public IdentityCommand enqueue(EmailMessage command) {
        Objects.requireNonNull(command, "command is required");

        var entity = IdentityCommandEntity.builder()
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload(toPayload(command))
                .build();

        var saved = commandRepository.save(entity);
        commandRepository.flush();
        return commandMapper.toModel(saved);
    }

    @Transactional
    public synchronized IdentityCommand startNextQueuedCommand() {
        var command = commandRepository
                .findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(
                        maxStartedCommandId,
                        IdentityCommandStatus.QUEUED
                )
                .orElse(null);
        if (command == null) {
            return null;
        }

        command.setCommandStatus(IdentityCommandStatus.RUNNING);
        command.setStartedAt(clock.instant());
        commandRepository.flush();
        maxStartedCommandId = command.getId();
        return commandMapper.toModel(command);
    }

    @Transactional
    public void complete(IdentityCommand command) {
        Objects.requireNonNull(command, "command is required");
        assertRunning(command.commandStatus());

        var commandId = command.id();
        var entity = commandRepository.findById(commandId)
                .orElseThrow();

        if (entity.getVersion() != command.version()) {
            throw new ObjectOptimisticLockingFailureException(IdentityCommandEntity.class, commandId);
        }
        assertRunning(entity.getCommandStatus());

        entity.setCommandStatus(IdentityCommandStatus.COMPLETED);
        entity.setCompletedAt(clock.instant());
        commandRepository.flush();
    }

    @Transactional
    public void fail(IdentityCommand command, Exception exception) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(exception, "exception is required");
        assertRunning(command.commandStatus());

        var commandId = command.id();
        var entity = commandRepository.findById(commandId)
                .orElseThrow();

        if (entity.getVersion() != command.version()) {
            throw new ObjectOptimisticLockingFailureException(IdentityCommandEntity.class, commandId);
        }
        assertRunning(entity.getCommandStatus());

        entity.setCommandStatus(IdentityCommandStatus.FAILED);
        entity.setErrorMessage(toErrorMessage(exception));
        entity.setCompletedAt(clock.instant());
        commandRepository.flush();
    }

    long getMaxStartedCommandId() {
        return maxStartedCommandId;
    }

    private void assertRunning(IdentityCommandStatus commandStatus) {
        if (commandStatus != IdentityCommandStatus.RUNNING) {
            throw new IllegalStateException("Command must be in RUNNING state");
        }
    }

    private String toErrorMessage(Exception exception) {
        var stackTraceWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTraceWriter));
        var stackTrace = stackTraceWriter.toString();
        var truncatedStackTrace = stackTrace.length() > MAX_ERROR_STACK_TRACE_LENGTH
                ? stackTrace.substring(0, MAX_ERROR_STACK_TRACE_LENGTH)
                : stackTrace;

        return exception.getMessage() + System.lineSeparator() + truncatedStackTrace;
    }

    private String toPayload(EmailMessage command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize send email command", e);
        }
    }
}

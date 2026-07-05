package vg.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityCommand;
import vg.identity.model.EmailMessage;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityCommandWorker {
    private final IdentityCommandService commandService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${identity.command.worker.fixed-delay-ms:5000}")
    public void processNextQueuedCommand() {
        var command = commandService.startNextQueuedCommand();
        while (command != null) {
            try {
                process(command);
            } catch (Exception e) {
                log.error("Command processing failed: {}", e.getMessage());
                commandService.fail(command, e);
                return;
            }

            commandService.complete(command);
            command = commandService.startNextQueuedCommand();
        }
    }

    void process(IdentityCommand command) throws JsonProcessingException {
        switch (command.commandStatus()) {
            case RUNNING -> processRunning(command);
            default -> throw new IllegalStateException("Unsupported command status: " + command.commandStatus());
        }
    }

    private void processRunning(IdentityCommand command) throws JsonProcessingException {
        switch (command.commandType()) {
            case SEND_EMAIL -> sendEmail(command);
            default -> throw new IllegalArgumentException("Unsupported command type: " + command.commandType());
        }
    }

    private void sendEmail(IdentityCommand command) throws JsonProcessingException {
        emailService.sendEmail(
                objectMapper.readValue(command.payload(), EmailMessage.class)
        );
    }
}

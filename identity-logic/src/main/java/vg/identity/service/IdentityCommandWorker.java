package vg.identity.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.EmailMessage;
import vg.identity.model.command.IdentityCommand;
import vg.identity.model.command.SetTelegramWebhookCommand;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityCommandWorker {
    private final IdentityCommandService commandService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final TelegramWebhookRegistrationService telegramWebhookRegistrationService;
    private final IdentityMessageCommandProcessor messageProcessor;

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

    void process(IdentityCommand command) throws JacksonException {
        switch (command.commandStatus()) {
            case RUNNING -> processRunning(command);
            default -> throw new IllegalStateException("Unsupported command status: " + command.commandStatus());
        }
    }

    private void processRunning(IdentityCommand command) throws JacksonException {
        switch (command.commandType()) {
            case SEND_EMAIL -> sendEmail(command);
            case SET_TELEGRAM_WEBHOOK -> setTelegramWebhook(command);
            case SEND_APPLICATION_MESSAGE -> messageProcessor.process(command);
            default -> throw new IllegalArgumentException("Unsupported command type: " + command.commandType());
        }
    }

    private void sendEmail(IdentityCommand command) throws JacksonException {
        emailService.sendEmail(
                objectMapper.readValue(command.payload(), EmailMessage.class)
        );
    }

    private void setTelegramWebhook(IdentityCommand command) {
        try {
            telegramWebhookRegistrationService.register(
                    objectMapper.readValue(command.payload(), SetTelegramWebhookCommand.class)
            );
        } catch (RuntimeException e) {
            // The worker logs and persists failures; never retain Telegram URLs, tokens, or causes.
            throw new IllegalStateException("Cannot set Telegram webhook for command " + command.id());
        }
    }
}

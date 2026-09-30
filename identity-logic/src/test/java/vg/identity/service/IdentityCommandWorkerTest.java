package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityCommand;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;
import vg.identity.model.SetTelegramWebhookCommand;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityCommandWorkerTest {

    @Mock
    private IdentityCommandService commandService;
    @Mock
    private EmailService emailService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TelegramWebhookRegistrationService registrationService;
    private IdentityCommandWorker worker;

    @BeforeEach
    void setUp() {
        worker = new IdentityCommandWorker(commandService, emailService, objectMapper, registrationService);
    }

    @Test
    void processNextQueuedCommand_whenQueueHasEmailCommand_sendsEmailAndCompletesCommand() throws Exception {
        var payload = "{\"type\":\"email\"}";
        var command = IdentityCommand.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload(payload)
                .build();
        var sendEmailCommand = EmailMessage.builder()
                .to(List.of("user@example.com"))
                .cc(List.of("cc@example.com"))
                .bcc(List.of("bcc@example.com"))
                .subject("Subject")
                .body("Body")
                .html(true)
                .build();
        when(commandService.startNextQueuedCommand()).thenReturn(command).thenReturn(null);
        when(objectMapper.readValue(payload, EmailMessage.class)).thenReturn(sendEmailCommand);

        worker.processNextQueuedCommand();

        verify(emailService).sendEmail(EmailMessage.builder()
                .to(List.of("user@example.com"))
                .cc(List.of("cc@example.com"))
                .bcc(List.of("bcc@example.com"))
                .subject("Subject")
                .body("Body")
                .html(true)
                .build());
        verify(commandService).complete(command);
    }

    @Test
    void processNextQueuedCommand_whenQueueIsEmpty_doesNothing() {
        when(commandService.startNextQueuedCommand()).thenReturn(null);

        worker.processNextQueuedCommand();

        verify(commandService, never()).complete(org.mockito.ArgumentMatchers.any(IdentityCommand.class));
    }

    @Test
    void processNextQueuedCommand_whenEmailProcessingFails_marksCommandFailed() throws Exception {
        var payload = "{\"type\":\"email\"}";
        var command = IdentityCommand.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload(payload)
                .build();
        var exception = new IllegalStateException("boom");
        var sendEmailCommand = EmailMessage.builder()
                .to(List.of("user@example.com"))
                .subject("Subject")
                .body("Body")
                .build();
        when(commandService.startNextQueuedCommand()).thenReturn(command);
        when(objectMapper.readValue(payload, EmailMessage.class)).thenReturn(sendEmailCommand);
        org.mockito.Mockito.doThrow(exception)
                .when(emailService)
                .sendEmail(EmailMessage.builder()
                        .to(List.of("user@example.com"))
                        .subject("Subject")
                        .body("Body")
                        .build());

        worker.processNextQueuedCommand();

        verify(commandService).fail(command, exception);
        verify(commandService, never()).complete(command);
    }

    @Test
    void process_whenCommandIsNotRunning_throwsIllegalStateException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.FAILED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();

        assertThatThrownBy(() -> worker.process(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported command status");
    }

    @Test
    void processNextQueuedCommand_dispatchesWebhookSetupAndCompletesCommand() {
        var command = webhookCommand();
        var setup = SetTelegramWebhookCommand.builder().applicationUniqueId(new UniqueId(42L)).build();
        when(commandService.startNextQueuedCommand()).thenReturn(command).thenReturn(null);
        when(objectMapper.readValue(command.payload(), SetTelegramWebhookCommand.class)).thenReturn(setup);

        worker.processNextQueuedCommand();

        verify(registrationService).register(setup);
        verify(commandService).complete(command);
    }

    @Test
    void processNextQueuedCommand_sanitizesWebhookErrorsBeforePersistingThem() {
        var command = webhookCommand();
        var setup = SetTelegramWebhookCommand.builder().applicationUniqueId(new UniqueId(42L)).build();
        when(commandService.startNextQueuedCommand()).thenReturn(command);
        when(objectMapper.readValue(command.payload(), SetTelegramWebhookCommand.class)).thenReturn(setup);
        org.mockito.Mockito.doThrow(new IllegalStateException("https://api.telegram.org/botSECRET/setWebhook"))
                .when(registrationService).register(setup);

        worker.processNextQueuedCommand();

        var error = org.mockito.ArgumentCaptor.forClass(Exception.class);
        verify(commandService).fail(org.mockito.ArgumentMatchers.eq(command), error.capture());
        assertThat(error.getValue()).hasMessage("Cannot set Telegram webhook for command 7").hasNoCause();
        verify(commandService, never()).complete(command);
    }

    private static IdentityCommand webhookCommand() {
        return IdentityCommand.builder().id(7L).commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SET_TELEGRAM_WEBHOOK).payload("identifiers-only").build();
    }
}

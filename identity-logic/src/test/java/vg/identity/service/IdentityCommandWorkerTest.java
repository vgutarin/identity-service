package vg.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.model.IdentityCommand;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;
import vg.identity.model.EmailMessage;

import java.util.List;

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
    private IdentityCommandWorker worker;

    @BeforeEach
    void setUp() {
        worker = new IdentityCommandWorker(commandService, emailService, objectMapper);
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
        when(commandService.startNextQueuedCommand()).thenReturn(command, null);
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
}

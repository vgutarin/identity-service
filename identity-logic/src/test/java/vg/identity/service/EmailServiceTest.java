package vg.identity.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import vg.identity.EmailProperties;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    JavaMailSender mailSender;

    private EmailService service;
    private EmailProperties emailProperties;

    @BeforeEach
    void setUp() {
        emailProperties = new EmailProperties();
        emailProperties.setFrom("noreply@example.com");
        service = new EmailService(mailSenderProvider, emailProperties);
    }

    @Test
    void sendTextEmail_whenMailSenderIsConfigured_sendsPlainTextMessage() throws Exception {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage());

        service.sendTextEmail(List.of("user@example.com"), "Welcome", "Hello");

        var sent = captureSentMessage();
        assertThat(sent.getFrom()[0].toString()).isEqualTo("noreply@example.com");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Welcome");
        assertThat(sent.getContentType()).containsIgnoringCase("text/plain");
        assertThat(sent.getContent()).isEqualTo("Hello");
    }

    @Test
    void sendHtmlEmail_whenMailSenderIsConfigured_sendsHtmlMessage() throws Exception {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage());

        service.sendHtmlEmail("user@example.com", "Welcome", "<strong>Hello</strong>");

        var sent = captureSentMessage();
        assertThat(sent.getContentType()).containsIgnoringCase("text/html");
        assertThat(sent.getContent()).isEqualTo("<strong>Hello</strong>");
    }

    @Test
    void sendEmail_whenCcAndBccAreProvided_setsRecipients() throws Exception {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage());

        service.sendEmail(EmailService.EmailMessage.builder()
                .to(List.of("to@example.com"))
                .cc(List.of("cc@example.com"))
                .bcc(List.of("bcc@example.com"))
                .subject("Subject")
                .body("Body")
                .build());

        var sent = captureSentMessage();
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("to@example.com");
        assertThat(sent.getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("cc@example.com");
        assertThat(sent.getRecipients(Message.RecipientType.BCC)[0].toString()).isEqualTo("bcc@example.com");
    }

    @Test
    void sendEmail_whenMailSenderIsNotConfigured_throwsClearException() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.sendEmail("user@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    void sendEmail_whenFromIsMissing_throwsClearException() {
        emailProperties.setFrom(null);

        assertThatThrownBy(() -> service.sendEmail("user@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.email.from");
    }

    @Test
    void sendEmail_whenFromIsInvalid_throwsClearException() {
        emailProperties.setFrom("not-an-email");

        assertThatThrownBy(() -> service.sendEmail("user@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.email.from");
    }

    @Test
    void sendEmail_whenRecipientIsInvalid_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.sendEmail("not-an-email", "Subject", "Body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void validateEmail_whenEmailIsValid_returnsTrue() {
        assertThat(service.validateEmail("user@example.com")).isTrue();
    }

    @Test
    void validateEmail_whenEmailIsBlankOrInvalid_returnsFalse() {
        assertThat(service.validateEmail(null)).isFalse();
        assertThat(service.validateEmail(" ")).isFalse();
        assertThat(service.validateEmail("User <user@example.com>")).isFalse();
        assertThat(service.validateEmail("not-an-email")).isFalse();
    }

    private MimeMessage captureSentMessage() throws Exception {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private MimeMessage mimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }
}

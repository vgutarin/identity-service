package vg.identity.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vg.identity.EmailProperties;
import vg.identity.model.EmailMessage;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final EmailProperties emailProperties;

    public void sendEmail(String to, String subject, String body) {
        sendTextEmail(List.of(to), subject, body);
    }

    public void sendTextEmail(String to, String subject, String body) {
        sendTextEmail(List.of(to), subject, body);
    }

    public void sendTextEmail(Collection<String> to, String subject, String body) {
        sendEmail(
                EmailMessage.builder()
                        .to(to)
                        .subject(subject)
                        .body(body)
                        .build()
        );
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendHtmlEmail(List.of(to), subject, htmlBody);
    }

    public void sendHtmlEmail(Collection<String> to, String subject, String htmlBody) {
        sendEmail(
                EmailMessage.builder()
                        .to(to)
                        .subject(subject)
                        .body(htmlBody)
                        .html(true)
                        .build()
        );
    }

    public void sendEmail(EmailMessage email) {
        validate(email);
        var from = validatedFrom();

        var mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email sending is not configured. Set spring.mail.host or provide a JavaMailSender bean.");
        }

        var mimeMessage = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toArray(email.to()));
            if (!email.cc().isEmpty()) {
                helper.setCc(toArray(email.cc()));
            }
            if (!email.bcc().isEmpty()) {
                helper.setBcc(toArray(email.bcc()));
            }
            helper.setSubject(email.subject());
            helper.setText(email.body(), email.html());
        } catch (MessagingException e) {
            throw new IllegalArgumentException("Cannot create email message", e);
        }

        mailSender.send(mimeMessage);
    }

    public boolean validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }

        try {
            var address = new InternetAddress(email, true);
            address.validate();
            return email.equals(address.getAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private String validatedFrom() {
        var from = emailProperties.getFrom();
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("Email sender is not configured. Set identity.email.from.");
        }
        if (!validateEmail(from)) {
            throw new IllegalStateException("Email sender is invalid. Set identity.email.from to a valid email address.");
        }
        return from;
    }

    private void validate(EmailMessage email) {
        Objects.requireNonNull(email, "email is required");
        validateRecipients(email.to(), "to");
        validateRecipients(email.cc(), "cc");
        validateRecipients(email.bcc(), "bcc");

        if (!StringUtils.hasText(email.subject())) {
            throw new IllegalArgumentException("Email subject is required");
        }
        if (email.body() == null) {
            throw new IllegalArgumentException("Email body is required");
        }
    }

    private void validateRecipients(Collection<String> recipients, String field) {
        Objects.requireNonNull(recipients, "Email " + field + " recipients are required");
        if ("to".equals(field) && recipients.isEmpty()) {
            throw new IllegalArgumentException("Email to recipients are required");
        }

        for (var recipient : recipients) {
            if (!validateEmail(recipient)) {
                throw new IllegalArgumentException("Invalid email " + field + " recipient: " + recipient);
            }
        }
    }

    private String[] toArray(Collection<String> recipients) {
        return recipients.toArray(String[]::new);
    }

}

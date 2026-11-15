package vg.identity.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.MessageContent;
import vg.identity.service.EncryptionService;

/** Persists typed message content as encrypted compact JSON. */
@Component
@Converter
@RequiredArgsConstructor
public class MessageContentConverter implements AttributeConverter<MessageContent, byte[]> {
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @Override
    public byte[] convertToDatabaseColumn(MessageContent content) {
        if (content == null) {
            return null;
        }
        try {
            return encryptionService.encode(objectMapper.writeValueAsString(content));
        } catch (RuntimeException e) {
            // Converter failures can reach logs; omit causes containing plaintext JSON or credentials.
            throw new IllegalStateException("Cannot serialize message content");
        }
    }

    @Override
    public MessageContent convertToEntityAttribute(byte[] encryptedContent) {
        if (encryptedContent == null) {
            return null;
        }
        try {
            var content = objectMapper.readValue(encryptionService.decode(encryptedContent), MessageContent.class);
            if (content == null) {
                throw new IllegalArgumentException("Missing content");
            }
            return content;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot deserialize message content");
        }
    }
}

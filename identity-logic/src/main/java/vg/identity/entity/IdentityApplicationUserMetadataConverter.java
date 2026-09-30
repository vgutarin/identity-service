package vg.identity.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.IdentityApplicationUserMetadata;
import vg.identity.service.EncryptionService;

/** Serializes and encrypts typed application-user membership metadata. */
@Component
@Converter
@RequiredArgsConstructor
public class IdentityApplicationUserMetadataConverter implements AttributeConverter<IdentityApplicationUserMetadata, byte[]> {
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @Override
    public byte[] convertToDatabaseColumn(IdentityApplicationUserMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return encryptionService.encode(objectMapper.writeValueAsString(metadata));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize application-user metadata", e);
        }
    }

    @Override
    public IdentityApplicationUserMetadata convertToEntityAttribute(byte[] encryptedMetadata) {
        if (encryptedMetadata == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    encryptionService.decode(encryptedMetadata),
                    IdentityApplicationUserMetadata.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot deserialize application-user metadata", e);
        }
    }
}

package vg.identity.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.identity.service.EncryptionService;

@Slf4j
@Component
@RequiredArgsConstructor
@Converter
public class StringEncryptionConverter implements AttributeConverter<String, byte[]> {

    private final EncryptionService encryptionService;

    @Override
    public byte[] convertToDatabaseColumn(String src) {
        if (src == null) {
            return null;
        }
        return encryptionService.encode(src);
    }

    @Override
    public String convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return encryptionService.decode(bytes);
    }
}

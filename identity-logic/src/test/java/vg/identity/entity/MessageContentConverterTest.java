package vg.identity.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.MessageContent;
import vg.identity.service.EncryptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageContentConverterTest {
    @Mock private ObjectMapper objectMapper;
    @Mock private EncryptionService encryptionService;
    private MessageContentConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MessageContentConverter(objectMapper, encryptionService);
    }

    @Test
    void convertToDatabaseColumn_whenContentIsPresent_encryptsCompactTypedJson() {
        var content = MessageContent.builder().text("hello").build();
        var encrypted = new byte[]{1, 2, 3};
        when(objectMapper.writeValueAsString(content)).thenReturn("{\"t\":\"hello\"}");
        when(encryptionService.encode("{\"t\":\"hello\"}")).thenReturn(encrypted);

        assertThat(converter.convertToDatabaseColumn(content)).isSameAs(encrypted);
    }

    @Test
    void convertToEntityAttribute_whenEncryptedContentIsValid_restoresTypedContent() {
        var content = MessageContent.builder().text("hello").build();
        var encrypted = new byte[]{1, 2, 3};
        when(encryptionService.decode(encrypted)).thenReturn("{\"t\":\"hello\"}");
        when(objectMapper.readValue("{\"t\":\"hello\"}", MessageContent.class)).thenReturn(content);

        assertThat(converter.convertToEntityAttribute(encrypted)).isSameAs(content);
    }

    @Test
    void conversion_whenContentIsAbsent_preservesNullWithoutCallingCollaborators() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        verifyNoInteractions(objectMapper, encryptionService);
    }

    @Test
    void convertToDatabaseColumn_whenSerializationFails_throwsSanitizedErrorWithoutCause() {
        var content = MessageContent.builder().text("private text").build();
        when(objectMapper.writeValueAsString(content))
                .thenThrow(DatabindException.from((JsonParser) null, "private text"));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(content))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot serialize message content").hasNoCause();
        verifyNoInteractions(encryptionService);
    }

    @Test
    void convertToDatabaseColumn_whenEncryptionFails_throwsSanitizedErrorWithoutCause() {
        var content = MessageContent.builder().text("hello").build();
        when(objectMapper.writeValueAsString(content)).thenReturn("{\"t\":\"hello\"}");
        when(encryptionService.encode("{\"t\":\"hello\"}")).thenThrow(new IllegalStateException("private details"));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(content))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot serialize message content").hasNoCause();
    }

    @Test
    void convertToEntityAttribute_whenJsonIsMalformed_throwsSanitizedErrorWithoutCause() {
        var encrypted = new byte[]{1, 2, 3};
        when(encryptionService.decode(encrypted)).thenReturn("private malformed JSON");
        when(objectMapper.readValue("private malformed JSON", MessageContent.class))
                .thenThrow(DatabindException.from((JsonParser) null, "private malformed JSON"));

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot deserialize message content").hasNoCause();
    }

    @Test
    void convertToEntityAttribute_whenDecryptionFails_throwsSanitizedErrorWithoutCause() {
        var encrypted = new byte[]{1, 2, 3};
        when(encryptionService.decode(encrypted)).thenThrow(new IllegalStateException("private details"));

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot deserialize message content").hasNoCause();
        verifyNoInteractions(objectMapper);
    }

    @Test
    void convertToEntityAttribute_whenStoredJsonIsNull_rejectsMissingContent() {
        var encrypted = new byte[]{1, 2, 3};
        when(encryptionService.decode(encrypted)).thenReturn("null");
        when(objectMapper.readValue("null", MessageContent.class)).thenReturn(null);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot deserialize message content").hasNoCause();
    }
}

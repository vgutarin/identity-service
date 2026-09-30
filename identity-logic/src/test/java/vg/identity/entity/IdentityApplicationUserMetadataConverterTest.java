package vg.identity.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DatabindException;
import vg.identity.model.IdentityApplicationUserMetadata;
import vg.identity.service.EncryptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationUserMetadataConverterTest {
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EncryptionService encryptionService;
    @InjectMocks
    private IdentityApplicationUserMetadataConverter converter;

    @Test
    void convertToDatabaseColumn_serializesTheTypedMetadataBeforeEncryptingIt() throws JacksonException {
        var metadata = new IdentityApplicationUserMetadata(new int[]{0});
        var encryptedMetadata = new byte[]{1, 2, 3};
        when(objectMapper.writeValueAsString(metadata)).thenReturn("{\"f\":[0]}");
        when(encryptionService.encode("{\"f\":[0]}")).thenReturn(encryptedMetadata);

        assertThat(converter.convertToDatabaseColumn(metadata)).isEqualTo(encryptedMetadata);

        verify(objectMapper).writeValueAsString(metadata);
        verify(encryptionService).encode("{\"f\":[0]}");
    }

    @Test
    void convertToEntityAttribute_decryptsAndDeserializesTheTypedMetadata() throws JacksonException {
        var encryptedMetadata = new byte[]{1, 2, 3};
        var metadata = new IdentityApplicationUserMetadata(new int[]{0});
        when(encryptionService.decode(encryptedMetadata)).thenReturn("{\"f\":[0]}");
        when(objectMapper.readValue("{\"f\":[0]}", IdentityApplicationUserMetadata.class)).thenReturn(metadata);

        assertThat(converter.convertToEntityAttribute(encryptedMetadata)).isSameAs(metadata);

        verify(encryptionService).decode(encryptedMetadata);
        verify(objectMapper).readValue("{\"f\":[0]}", IdentityApplicationUserMetadata.class);
    }

    @Test
    void convertToEntityAttribute_whenMetadataCannotBeDeserialized_failsClosed() throws JacksonException {
        var encryptedMetadata = new byte[]{1, 2, 3};
        when(encryptionService.decode(encryptedMetadata)).thenReturn("not-json");
        when(objectMapper.readValue("not-json", IdentityApplicationUserMetadata.class))
                .thenThrow(DatabindException.from((tools.jackson.core.JsonParser) null, "Malformed metadata"));

        assertThatIllegalStateException().isThrownBy(() -> converter.convertToEntityAttribute(encryptedMetadata))
                .withMessage("Cannot deserialize application-user metadata");
    }

    @Test
    void convert_whenMetadataIsAbsent_preservesNullWithoutSerializingOrDecrypting() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();

        verifyNoInteractions(objectMapper, encryptionService);
    }
}

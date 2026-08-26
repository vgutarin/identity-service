package vg.identity.model;

import org.junit.jupiter.api.Test;
import vg.unique.id.model.UniqueId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationUserPrincipalTest {

    @Test
    void isSerializable() {
        assertThat(Serializable.class).isAssignableFrom(IdentityApplicationUserPrincipal.class);
    }

    @Test
    void survivesJavaSerializationRoundTrip() throws Exception {
        var original = IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(42L))
                .name("Ada Lovelace")
                .permissions(Set.of("read", "write"))
                .consentGiven(true)
                .build();

        var restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.permissions()).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void survivesRoundTripInProvisionalState() throws Exception {
        var provisional = IdentityApplicationUserPrincipal.builder()
                .name("Ada Lovelace")
                .build();

        var restored = roundTrip(provisional);

        assertThat(restored).isEqualTo(provisional);
        assertThat(restored.userUniqueId()).isNull();
        assertThat(restored.permissions()).isEmpty();
        assertThat(restored.consentGiven()).isFalse();
    }

    private static IdentityApplicationUserPrincipal roundTrip(IdentityApplicationUserPrincipal value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (IdentityApplicationUserPrincipal) in.readObject();
        }
    }
}

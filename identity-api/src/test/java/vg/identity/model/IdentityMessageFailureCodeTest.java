package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IdentityMessageFailureCodeTest {
    @Test
    void values_whenPersistedOrExposed_preservesNamesAndOrdinals() {
        assertThat(IdentityMessageFailureCode.values()).extracting(Enum::name, Enum::ordinal).containsExactly(
                tuple("DELIVERY_UNAVAILABLE", 0),
                tuple("PROVIDER_REJECTED", 1),
                tuple("INTERNAL_ERROR", 2),
                tuple("DELIVERY_UNKNOWN", 3));
    }
}

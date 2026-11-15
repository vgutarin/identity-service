package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IdentityMessageStatusTest {
    @Test
    void values_whenPersistedOrExposed_preservesNamesAndOrdinals() {
        assertThat(IdentityMessageStatus.values()).extracting(Enum::name, Enum::ordinal).containsExactly(
                tuple("QUEUED", 0),
                tuple("SENDING", 1),
                tuple("SENT", 2),
                tuple("FAILED", 3),
                tuple("UNKNOWN", 4));
    }
}

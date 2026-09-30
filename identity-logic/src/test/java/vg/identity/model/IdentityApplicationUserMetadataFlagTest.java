package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationUserMetadataFlagTest {

    /**
     * The ordinal is persisted in application-user metadata and must never change.
     */
    @Test
    void values_whenPersistedByOrdinal_returnsStableOrdinalAndName() {
        assertThat(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE.name()).isEqualTo("TELEGRAM_PHONE_DISCLOSURE");
        assertThat(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE.ordinal()).isZero();
    }

    /**
     * Guards against inserting values in the middle or removing a value. New values must be appended.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(IdentityApplicationUserMetadataFlag.values())
                .containsExactly(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE);
    }
}

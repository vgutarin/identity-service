package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityCommandStatusTest {

    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityCommandStatus.QUEUED.name()).isEqualTo("QUEUED");
        assertThat(IdentityCommandStatus.QUEUED.ordinal()).isEqualTo(0);

        assertThat(IdentityCommandStatus.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(IdentityCommandStatus.RUNNING.ordinal()).isEqualTo(1);

        assertThat(IdentityCommandStatus.COMPLETED.name()).isEqualTo("COMPLETED");
        assertThat(IdentityCommandStatus.COMPLETED.ordinal()).isEqualTo(2);

        assertThat(IdentityCommandStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(IdentityCommandStatus.FAILED.ordinal()).isEqualTo(3);
    }

    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(IdentityCommandStatus.values()).containsExactly(
                IdentityCommandStatus.QUEUED,
                IdentityCommandStatus.RUNNING,
                IdentityCommandStatus.COMPLETED,
                IdentityCommandStatus.FAILED
        );
    }
}

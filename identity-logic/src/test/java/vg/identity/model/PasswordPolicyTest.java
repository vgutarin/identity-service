package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void isStrong_whenMeetsAllRules_returnsTrue() {
        assertThat(PasswordPolicy.isStrong("Abcdefghi1")).isTrue();
    }

    @Test
    void isStrong_whenShorterThanMinLength_returnsFalse() {
        assertThat(PasswordPolicy.isStrong("Abcdefgh1")).isFalse();
    }

    @Test
    void isStrong_whenExactlyMinLength_returnsTrue() {
        assertThat("Abcdefgh1X").hasSize(PasswordPolicy.MIN_LENGTH);
        assertThat(PasswordPolicy.isStrong("Abcdefgh1X")).isTrue();
    }

    @Test
    void isStrong_whenMissingUppercase_returnsFalse() {
        assertThat(PasswordPolicy.isStrong("abcdefghi1")).isFalse();
    }

    @Test
    void isStrong_whenMissingLowercase_returnsFalse() {
        assertThat(PasswordPolicy.isStrong("ABCDEFGHI1")).isFalse();
    }

    @Test
    void isStrong_whenMissingDigit_returnsFalse() {
        assertThat(PasswordPolicy.isStrong("Abcdefghij")).isFalse();
    }

    @Test
    void isStrong_whenNull_returnsFalse() {
        assertThat(PasswordPolicy.isStrong(null)).isFalse();
    }

    @Test
    void requireStrong_whenWeak_throwsWithMessageKey() {
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("weak"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.WEAK_PASSWORD_MESSAGE_KEY);
    }

    @Test
    void requireStrong_whenStrong_doesNotThrow() {
        PasswordPolicy.requireStrong("Abcdefghi1");
    }
}

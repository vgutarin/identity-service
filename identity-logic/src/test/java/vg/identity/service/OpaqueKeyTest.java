package vg.identity.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueKeyTest {

    // 43 base64url chars: the canonical encoding of 32 zero bytes.
    private static final String ZERO_SECRET = "A".repeat(43);

    @Test
    void format_producesIdSeparatorAndUnpaddedBase64UrlSecret() {
        var secret = secretOf(1);

        var value = OpaqueKey.format("42", '_', secret);

        assertThat(value).isEqualTo("42_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        assertThat(value).matches("42_[A-Za-z0-9_-]{43}");
    }

    @Test
    void formatThenParse_roundTripsIdAndSecret() {
        var secret = secretOf(7);

        var parsed = OpaqueKey.parse(OpaqueKey.format("12345", '.', secret), '.').orElseThrow();

        assertThat(parsed.id()).isEqualTo("12345");
        assertThat(parsed.secret()).isEqualTo(secret);
    }

    @Test
    void parse_splitsAtFixedOffsetSoIdMayContainTheSeparator() {
        // The id half is opaque to the helper; splitting from the right at the fixed secret length
        // recovers it correctly even when it contains the separator character itself.
        var secret = secretOf(3);

        var parsed = OpaqueKey.parse(OpaqueKey.format("a_b_c", '_', secret), '_').orElseThrow();

        assertThat(parsed.id()).isEqualTo("a_b_c");
        assertThat(parsed.secret()).isEqualTo(secret);
    }

    @Test
    void parse_whenNullOrBlank_returnsEmpty() {
        assertThat(OpaqueKey.parse(null, '_')).isEmpty();
        assertThat(OpaqueKey.parse("", '_')).isEmpty();
        assertThat(OpaqueKey.parse("   ", '_')).isEmpty();
    }

    @Test
    void parse_whenShorterThanIdSeparatorAndSecret_returnsEmpty() {
        // 44 chars = separator + 43-char secret, leaving no room for a non-empty id.
        assertThat("_" + ZERO_SECRET).hasSize(44);
        assertThat(OpaqueKey.parse("_" + ZERO_SECRET, '_')).isEmpty();
    }

    @Test
    void parse_whenSeparatorIsNotAtExpectedOffset_returnsEmpty() {
        // Well-formed length, but the char before the trailing 43 is '.', not the requested '_'.
        assertThat(OpaqueKey.parse("7." + ZERO_SECRET, '_')).isEmpty();
    }

    @Test
    void parse_whenSecretIsNotBase64Url_returnsEmpty() {
        var invalidSecret = "A".repeat(42) + "*";

        assertThat(OpaqueKey.parse("7_" + invalidSecret, '_')).isEmpty();
    }

    @Test
    void newSecret_returns32IndependentRandomBytes() {
        var first = OpaqueKey.newSecret();
        var second = OpaqueKey.newSecret();

        assertThat(first).hasSize(32);
        assertThat(second).hasSize(32);
        assertThat(first).isNotEqualTo(new byte[32]);   // not the zero array
        assertThat(first).isNotEqualTo(second);          // two independent draws differ
    }

    @Test
    void sha256_matchesKnownVectorAndIsDeterministic() {
        var input = "abc".getBytes(StandardCharsets.US_ASCII);

        var digest = OpaqueKey.sha256(input);

        assertThat(digest).hasSize(32);
        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(OpaqueKey.sha256(input)).isEqualTo(digest);
    }

    @Test
    void sha256_differsForDifferentInput() {
        assertThat(OpaqueKey.sha256(secretOf(1))).isNotEqualTo(OpaqueKey.sha256(secretOf(2)));
    }

    @Test
    void secretMatches_whenHashIsOfTheSecret_returnsTrue() {
        var secret = secretOf(5);

        assertThat(OpaqueKey.secretMatches(OpaqueKey.sha256(secret), secret)).isTrue();
    }

    @Test
    void secretMatches_whenSecretIsWrong_returnsFalse() {
        assertThat(OpaqueKey.secretMatches(OpaqueKey.sha256(secretOf(5)), secretOf(6))).isFalse();
    }

    @Test
    void secretMatches_whenStoredHashHasWrongLength_returnsFalse() {
        assertThat(OpaqueKey.secretMatches(new byte[16], secretOf(5))).isFalse();
    }

    @Test
    void secretMatches_whenHashOrSecretIsNull_returnsFalse() {
        var secret = secretOf(5);
        var hash = OpaqueKey.sha256(secret);

        assertThat(OpaqueKey.secretMatches(null, secret)).isFalse();
        assertThat(OpaqueKey.secretMatches(hash, null)).isFalse();
        assertThat(OpaqueKey.secretMatches(null, null)).isFalse();
    }

    private static byte[] secretOf(int seed) {
        var secret = new byte[32];
        for (var i = 0; i < secret.length; i++) {
            secret[i] = (byte) (seed * 31 + i);
        }
        return secret;
    }
}

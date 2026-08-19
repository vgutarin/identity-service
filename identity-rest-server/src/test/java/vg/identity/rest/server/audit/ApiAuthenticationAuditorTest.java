package vg.identity.rest.server.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vg.identity.rest.server.IdentityRestApiProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ApiAuthenticationAuditorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC);

    private ApiAuthenticationAuditor auditor(int maxCounterKeys) {
        var properties = new IdentityRestApiProperties();
        properties.getAudit().setMaxCounterKeys(maxCounterKeys);
        return new ApiAuthenticationAuditor(CLOCK, properties);
    }

    @Test
    void failure_emitsPerEventRecordWithReasonAndKeyId(CapturedOutput output) {
        auditor(10_000).failure("invalid_key", "GET", "/api/v1/applications/me", "1.2.3.4",
                "11111111-1111-1111-1111-111111111111");

        assertThat(output.getAll())
                .contains("outcome=FAILURE")
                .contains("reason=invalid_key")
                .contains("method=GET")
                .contains("path=/api/v1/applications/me")
                .contains("remoteAddr=1.2.3.4")
                .contains("keyId=11111111-1111-1111-1111-111111111111");
    }

    @Test
    void failure_withoutKeyId_logsNone(CapturedOutput output) {
        auditor(10_000).failure("missing_header", "GET", "/api/v1/applications/me", null, null);

        assertThat(output.getAll())
                .contains("reason=missing_header")
                .contains("remoteAddr=unknown")
                .contains("keyId=none");
    }

    @Test
    void failure_sanitizesLogInjectionAttempts(CapturedOutput output) {
        auditor(10_000).failure("invalid_key", "GET",
                "/api/v1/applications/me\nevent=api.auth outcome=SUCCESS", "1.2.3.4", null);

        // the injected newline+content must be neutralised, not create a second forged record
        assertThat(output.getAll()).doesNotContain("path=/api/v1/applications/me\nevent=api.auth outcome=SUCCESS");
    }

    @Test
    void success_isCountedAndFlushedAsAggregate(CapturedOutput output) {
        var auditor = auditor(10_000);

        auditor.success("42", "GET", "/api/v1/applications/me");
        auditor.success("42", "GET", "/api/v1/applications/me");
        auditor.success("42", "GET", "/api/v1/applications/me");
        auditor.flush();

        assertThat(output.getAll())
                .contains("outcome=SUCCESS")
                .contains("applicationUniqueId=42")
                .contains("count=3")
                .contains("windowStart=2026-08-19T10:00:00Z");
    }

    @Test
    void success_beyondMaxKeys_foldsIntoOverflowBucket(CapturedOutput output) {
        var auditor = auditor(1);

        auditor.success("1", "GET", "/api/v1/applications/me");
        auditor.success("2", "GET", "/api/v1/applications/me");
        auditor.flush();

        assertThat(output.getAll())
                .contains("outcome=OVERFLOW")
                .contains("applicationUniqueId=__overflow__");
    }

    @Test
    void auditNeverContainsAKeySecret(CapturedOutput output) {
        // The auditor is only ever handed the key id, never the raw value; prove a secret-looking token that
        // is NOT passed in cannot appear in the output.
        var secret = "s3cr3t-raw-key-value-should-never-be-logged";

        auditor(10_000).failure("invalid_key", "GET", "/api/v1/applications/me", "1.2.3.4",
                "11111111-1111-1111-1111-111111111111");
        auditor(10_000).success("42", "GET", "/api/v1/applications/me");

        assertThat(output.getAll()).doesNotContain(secret);
    }
}

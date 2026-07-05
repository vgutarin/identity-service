package vg.identity.model;

import lombok.Builder;

import java.util.Collection;
import java.util.Collections;

@Builder
public record EmailMessage(
        Collection<String> to,
        Collection<String> cc,
        Collection<String> bcc,
        String subject,
        String body,
        boolean html
) {
    public EmailMessage {
        to = emptyIfNull(to);
        cc = emptyIfNull(cc);
        bcc = emptyIfNull(bcc);
    }

    private static Collection<String> emptyIfNull(Collection<String> value) {
        return value == null ? Collections.emptyList() : value;
    }
}

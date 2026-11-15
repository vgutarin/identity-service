package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.audit.IdentityMessageSubmissionAuditor;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityMessageSubmission;
import vg.identity.model.access.Permission;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/** Permission-gated administration of messages for an explicitly selected application. */
@Service
@RequiredArgsConstructor
public class IdentityApplicationMessageService {
    private final CurrentPrincipalService currentPrincipalService;
    private final IdentityMessageService messages;
    private final IdentityMessageSubmissionAuditor auditor;

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.MESSAGE_SEND + "')")
    @Transactional
    public IdentityMessageSubmission submitMessage(UniqueId applicationUniqueId, UniqueId userUniqueId,
                                                    IdentityApplicationMessageRequest request) {
        var actorUniqueId = currentPrincipalService.requirePrincipalUniqueId();
        var submission = messages.submit(applicationUniqueId, userUniqueId, request);
        auditor.acceptedAfterCommit(actorUniqueId, applicationUniqueId, submission.message().userUniqueId(),
                submission.message().messageUniqueId(), submission.replayed());
        return submission;
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.MESSAGE_READ + "')")
    @Transactional(readOnly = true)
    public Optional<IdentityApplicationMessage> findMessage(UniqueId applicationUniqueId, UniqueId messageUniqueId) {
        currentPrincipalService.requirePrincipalUniqueId();
        return messages.find(applicationUniqueId, messageUniqueId);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.MESSAGE_READ + "')")
    @Transactional(readOnly = true)
    public Page<IdentityApplicationMessage> findMessages(UniqueId applicationUniqueId, int page, int pageSize) {
        currentPrincipalService.requirePrincipalUniqueId();
        return messages.findPage(applicationUniqueId, page, pageSize);
    }
}

package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.model.TelegramUserPrincipal;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Embedded implementation of {@link IdentityApplicationApi}.
 */
@Service
@RequiredArgsConstructor
public class IdentityApplicationApiService implements IdentityApplicationApi {
    private final IdentityApplicationService applicationService;
    private final CurrentIdentityApplicationService currentIdentityApplicationService;
    private final TelegramAuthenticationService telegramAuthenticationService;
    private final IdentityApplicationUserProvisioningService userProvisioningService;
    private final IdentityApplicationUserService applicationUserService;
    private final IdentityApplicationClaimService claimService;
    private final TelegramAuthenticationAuditor auditor;

    @Override
    public IdentityApplicationPrincipal me() {
        var principal = currentIdentityApplicationService.requireApplicationPrincipal();
        var application = applicationService.getAuthenticatedApplication(principal.getUniqueId());
        return new IdentityApplicationPrincipal(
                application.getUniqueId(),
                new UniqueId(application.getWorkspaceUniqueId()),
                application.getName(),
                URI.create(application.getUri())
        );
    }

    @Override
    public Optional<IdentityApplicationUserPrincipal> authenticateTelegram(TelegramAuthenticationRequest request) {
        var applicationUniqueId = currentIdentityApplicationService.requireApplicationUniqueId();
        var bot = applicationService.getTelegramBot(applicationUniqueId);
        return telegramAuthenticationService.parseUser(bot, request.telegramInitData())
                .map(telegramUser -> resolvePrincipal(applicationUniqueId, telegramUser, request.consentToKeepPersonalData()));
    }

    private IdentityApplicationUserPrincipal resolvePrincipal(
            UniqueId applicationUniqueId,
            TelegramUserPrincipal telegramUser,
            boolean consentToKeepPersonalData
    ) {
        var existing = userProvisioningService.findTelegramUser(telegramUser).orElse(null);

        // Already-established user (consent recorded previously): read-only, expose the subject.
        if (existing != null && existing.getConsentToKeepPersonalDataAt() != null) {
            return establishedPrincipal(applicationUniqueId, telegramUser, existing);
        }

        // Consent granted but a write is required: create a new user, or backfill consent on a legacy row.
        if (consentToKeepPersonalData) {
            var created = existing == null;
            var user = userProvisioningService.establishTelegramUser(telegramUser);
            if (created) {
                auditor.userProvisioned(applicationUniqueId, user.getUniqueId());
            }
            return establishedPrincipal(applicationUniqueId, telegramUser, user);
        }

        // No consent, not established: provisional principal, nothing persisted.
        return preConsentPrincipal(telegramUser);
    }

    private IdentityApplicationUserPrincipal establishedPrincipal(
            UniqueId applicationUniqueId,
            TelegramUserPrincipal telegramUser,
            IdentityUserEntity user
    ) {
        var userUniqueId = new UniqueId(user.getUniqueId());
        applicationUserService.recordAuthentication(applicationUniqueId, user.getUniqueId());
        auditor.authenticationSucceeded(applicationUniqueId, user.getUniqueId());
        var permissions = claimService.findClaimsByScope(applicationUniqueId, userUniqueId)
                .getOrDefault(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of());
        return IdentityApplicationUserPrincipal.builder()
                .userUniqueId(userUniqueId)
                .name(telegramUser.displayName())
                .permissions(permissions)
                .consentGiven(true)
                .build();
    }

    private IdentityApplicationUserPrincipal preConsentPrincipal(TelegramUserPrincipal telegramUser) {
        return IdentityApplicationUserPrincipal.builder()
                .name(telegramUser.displayName())
                .consentGiven(false)
                .build();
    }
}

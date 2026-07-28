package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

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

    @Override
    public AuthenticatedIdentityApplication me() {
        var principal = currentIdentityApplicationService.requireApplicationPrincipal();
        var application = applicationService.getAuthenticatedApplication(principal.getUniqueId());
        return new AuthenticatedIdentityApplication(
                application.getUniqueId().toString(),
                application.getWorkspaceUniqueId(),
                application.getName(),
                application.getUri()
        );
    }

    @Override
    public Optional<IdentityApplicationUserPrincipal> authenticateTelegram(String initData) {
        var applicationUniqueId = currentIdentityApplicationService.requireApplicationUniqueId();
        var bot = applicationService.getTelegramBot(applicationUniqueId);
        return telegramAuthenticationService.parseUser(bot, initData)
                .map(userProvisioningService::resolveTelegramUser)
                .map(identityUser -> {
                    applicationUserService.recordAuthentication(applicationUniqueId, identityUser.getUniqueId());
                    return new IdentityApplicationUserPrincipal(
                            applicationUniqueId.toString(),
                            new UniqueId(identityUser.getUniqueId()).toString(),
                            claimService.findClaimsByScope(
                                    applicationUniqueId,
                                    new UniqueId(identityUser.getUniqueId())
                            )
                    );
                });
    }
}

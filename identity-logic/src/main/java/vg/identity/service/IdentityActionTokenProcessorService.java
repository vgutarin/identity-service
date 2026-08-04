package vg.identity.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.UserProvisioningDetails;
import vg.identity.repository.IdentityUserChannelRepository;

import java.net.URI;
import java.time.Clock;

/**
 * Executes the domain effects requested by action tokens.
 * <p>
 * This service completes interactions initiated through {@link IdentityActionTokenService}, including email
 * confirmation and Telegram-assisted confirmation. It resolves or provisions the affected identity, updates
 * its channels and consent, then consumes the completed action.
 * <p>
 * {@code IdentityActionTokenService} owns issuing, inspecting, locking, and deleting token records. This
 * processor owns the transactional business workflow around those records, so issuing an action never creates
 * a dependency on the user or channel services needed to process it.
 */
@RequiredArgsConstructor
@Service
public class IdentityActionTokenProcessorService {

    private final IdentityUserChannelRepository channelRepository;
    private final IdentityUserService userService;
    private final IdentityUserChannelService channelService;
    private final IdentityActionTokenService actionTokenService;
    private final Clock clock;

    @Transactional
    public ConfirmationResult confirmEmail(@NotNull String actionKey) {
        return confirmEmail(actionKey, null);
    }

    /**
     * Confirms the email channel behind a {@code CONFIRM_EMAIL} action. When the channel has no user yet and
     * {@code provisioning} is supplied, a new identity is provisioned with the given display name and password;
     * an existing user is reused and the payload is ignored.
     */
    @Transactional
    public ConfirmationResult confirmEmail(@NotNull String actionKey, UserProvisioningDetails provisioning) {
        var verification = actionTokenService.findConfirmEmailActionForUpdate(actionKey);
        if (verification == null) {
            return new ConfirmationResult(false, null);
        }

        var user = resolveEmailUser(verification.getIdentityUserChannel(), provisioning);
        completeEmailConfirmation(verification, user);
        return new ConfirmationResult(true, actionTokenService.createBindTelegramUrlIfTelegramIsMissing(user));
    }

    /**
     * Completes a Telegram-assisted email confirmation in an independent transaction. A Telegram-channel race
     * rolls back only this attempt, so the caller can retain the email action and return its neutral failure UI.
     * <p>
     * The returned entity is detached once this transaction commits; callers may only read fields that are
     * already loaded (notably the eager {@code principal}). If {@code IdentityUserEntity.principal} is ever made
     * lazy, downstream mapping in the caller's transaction would fail with a {@code LazyInitializationException}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdentityUserEntity confirmEmailWithTelegram(@NotNull String actionKey, @NotNull TelegramUserPrincipal telegramUser) {
        return confirmEmailWithTelegram(actionKey, telegramUser, null);
    }

    /**
     * Telegram-assisted confirmation with optional provisioning. When neither an email-user nor a telegram-user
     * exists a brand-new identity must be provisioned; if {@code provisioning} is still {@code null} this signals
     * {@link CredentialsRequiredException} <em>before</em> any mutation, so the {@code REQUIRES_NEW} transaction
     * rolls back cleanly and the caller can collect credentials and retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdentityUserEntity confirmEmailWithTelegram(
            @NotNull String actionKey,
            @NotNull TelegramUserPrincipal telegramUser,
            UserProvisioningDetails provisioning
    ) {
        var verification = actionTokenService.findConfirmEmailActionForUpdate(actionKey);
        if (verification == null) {
            return null;
        }

        var emailChannel = verification.getIdentityUserChannel();
        var emailUser = findEmailUser(emailChannel);
        var telegramUserEntity = channelService.findUserByTelegramId(telegramUser.id());
        if (emailUser != null && telegramUserEntity != null && !emailUser.equals(telegramUserEntity)) {
            return null;
        }

        if (emailUser == null && telegramUserEntity == null && provisioning == null) {
            throw new CredentialsRequiredException();
        }

        var user = emailUser != null
                ? emailUser
                : telegramUserEntity != null
                        ? telegramUserEntity
                        : userService.getOrCreateEntityForEmailChannel(emailChannel.getChannelUserId(), provisioning);
        channelService.attachUser(emailChannel, user);

        if (channelService.bindTelegramUser(telegramUser, user)
                != IdentityUserChannelService.TelegramBindResult.SUCCESS) {
            throw new TelegramChannelConflictException();
        }

        completeEmailConfirmation(verification, user);
        return user;
    }

    @Transactional
    public void consumeAction(@NotNull Long id) {
        actionTokenService.consumeAction(id);
    }

    private IdentityUserEntity resolveEmailUser(IdentityUserChannelEntity channel, UserProvisioningDetails provisioning) {
        var existing = findEmailUser(channel);
        var user = existing != null
                ? existing
                : userService.getOrCreateEntityForEmailChannel(channel.getChannelUserId(), provisioning);
        channelService.attachUser(channel, user);
        return user;
    }

    private IdentityUserEntity findEmailUser(IdentityUserChannelEntity channel) {
        if (channel.getIdentityUser() != null) {
            return channel.getIdentityUser();
        }
        return userService.findEntityByUsername(channel.getChannelUserId()).orElse(null);
    }

    private void completeEmailConfirmation(IdentityActionTokenEntity verification, IdentityUserEntity user) {
        var channel = verification.getIdentityUserChannel();
        var now = clock.instant();
        channel.setVerifiedAt(now);
        if (user.getConsentToKeepPersonalDataAt() == null) {
            user.setConsentToKeepPersonalDataAt(now);
        }
        channelRepository.save(channel);
        channelRepository.flush();
        actionTokenService.consumeAction(verification.getId());
    }

    public record ConfirmationResult(boolean success, URI bindTelegramUrl) {
    }

    static final class TelegramChannelConflictException extends RuntimeException {
    }

    static final class CredentialsRequiredException extends RuntimeException {
    }
}

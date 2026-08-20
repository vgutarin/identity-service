package vg.identity.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.PasswordPolicy;
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
@Slf4j
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

    /**
     * Completes a password recovery: validates the presented action key under a pessimistic lock, enforces the
     * password policy server-side, sets the new one-way-hashed password on the target user, and consumes the
     * token (single-use). A weak password throws {@code IllegalArgumentException("exception.user.password.weak")}
     * <em>before</em> the token is consumed, so the link stays usable for another attempt (FR-008). A missing,
     * expired, tampered, or already-used key yields {@code ResetResult(false, null)} (FR-011).
     *
     * @return on success, {@code ResetResult(true, username)} carrying the user's login name so the caller can
     * sign the user in and invalidate their other sessions (FR-014/FR-015).
     */
    @Transactional
    public ResetResult resetPassword(@NotNull String actionKey, @NotNull String rawPassword) {
        var token = actionTokenService.findResetPasswordActionForUpdate(actionKey);
        if (token == null) {
            return new ResetResult(false, null);
        }

        // Server-side policy net (mirrors the UI). Throwing here leaves the token unconsumed and reusable.
        PasswordPolicy.requireStrong(rawPassword);

        var channel = token.getIdentityUserChannel();
        var user = channel != null ? channel.getIdentityUser() : null;
        if (user == null) {
            return new ResetResult(false, null);
        }

        userService.setPassword(user, rawPassword);
        actionTokenService.consumeAction(token.getId());
        log.info("Password reset completed and token consumed: tokenId={}", token.getId());
        return new ResetResult(true, channel.getChannelUserId());
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

    /**
     * Outcome of {@link #resetPassword}. On success, {@code username} is the login name to sign the user in
     * with (via the frontend's authentication + session-invalidation step); it is {@code null} on failure.
     */
    public record ResetResult(boolean success, String username) {
    }

    static final class TelegramChannelConflictException extends RuntimeException {
    }

    static final class CredentialsRequiredException extends RuntimeException {
    }
}

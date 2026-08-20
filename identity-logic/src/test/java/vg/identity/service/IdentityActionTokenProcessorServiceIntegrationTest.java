package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.IdentityActionTokenProperties;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.UserProvisioningDetails;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityActionTokenProcessorServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdentityWorkspaceService workspaceService;
    @Autowired
    private IdentityActionTokenProcessorService actionTokenProcessorService;
    @Autowired
    private IdentityActionTokenService actionTokenService;
    @Autowired
    private IdentityUserChannelService channelService;
    @Autowired
    private IdentityUserService userService;
    @Autowired
    private IdentityActionTokenProperties properties;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetProperties() {
        // Shared properties bean: reset to sane positive defaults before each test so a test that mutates
        // them (e.g. a negative expiry for the expired-link case) cannot leak into other tests.
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
    }

    @Test
    void confirmEmail_whenWorkspaceInvitationIsPending_provisionsUserWithDisplayNameAndPassword() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "user" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);
        var action = replaceWithKnownSecretAction(actionTokenRepository.findAll().getFirst());

        var result = actionTokenProcessorService.confirmEmail(
                actionKey(action.getId()),
                new UserProvisioningDetails("Jane Doe", "Abcdefghi1", true)
        );

        assertThat(result.success()).isTrue();
        var membership = workspaceService.getUserChannels(workspace.getUniqueId()).getFirst();
        assertThat(membership.isVerified()).isTrue();
        assertThat(membership.getIdentityUserUniqueId()).isNotNull();
        assertThat(userRepository.findById(membership.getIdentityUserUniqueId().getLongValue()))
                .hasValueSatisfying(user -> {
                    assertThat(user.getPrincipal().getName()).isEqualTo(email);
                    assertThat(user.getPrincipal().getDisplayName()).isEqualTo("Jane Doe");
                    assertThat(passwordEncoder.matches("Abcdefghi1", user.getPassword())).isTrue();
                });
        assertThat(actionTokenRepository.findById(action.getId())).isEmpty();
    }

    @Test
    void invitedPendingChannelUser_setsInitialPasswordViaConfirmEmail_withoutResetPasswordToken() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "invitee" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);

        // Initial setup rides the invitation's CONFIRM_EMAIL link — never a RESET_PASSWORD surface (FR-001b/FR-017).
        var issued = actionTokenRepository.findAll();
        assertThat(issued).hasSize(1);
        assertThat(issued.getFirst().getActionType()).isEqualTo(IdentityActionType.CONFIRM_EMAIL);

        var action = replaceWithKnownSecretAction(issued.getFirst());
        var result = actionTokenProcessorService.confirmEmail(
                actionKey(action.getId()),
                new UserProvisioningDetails("Invited User", "Abcdefghi1", true)
        );

        assertThat(result.success()).isTrue();
        // The invited user now has a usable, one-way-hashed initial password and can authenticate.
        var principal = userService.loadUserByUsername(email);
        assertThat(principal.getPassword()).startsWith("{argon2}");
        assertThat(passwordEncoder.matches("Abcdefghi1", principal.getPassword())).isTrue();
        // No password-reset token is ever involved in the invited-user initial-setup path.
        assertThat(actionTokenRepository.findAll())
                .noneMatch(token -> token.getActionType() == IdentityActionType.RESET_PASSWORD);
    }

    @Test
    void confirmEmail_whenPendingInvitationAndPasswordWeak_throwsAndKeepsActionUnconsumed() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "user" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);
        var action = replaceWithKnownSecretAction(actionTokenRepository.findAll().getFirst());

        assertThatThrownBy(() -> actionTokenProcessorService.confirmEmail(
                actionKey(action.getId()),
                new UserProvisioningDetails("Jane Doe", "weak", true)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(actionTokenRepository.findById(action.getId())).isPresent();
        assertThat(workspaceService.getUserChannels(workspace.getUniqueId()).getFirst().isVerified()).isFalse();
    }

    @Test
    void confirmEmail_whenPendingInvitationAndConsentNotGranted_throwsAndCreatesNoUser() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "user" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);
        var action = replaceWithKnownSecretAction(actionTokenRepository.findAll().getFirst());

        assertThatThrownBy(() -> actionTokenProcessorService.confirmEmail(
                actionKey(action.getId()),
                new UserProvisioningDetails("Jane Doe", "Abcdefghi1", false)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(actionTokenRepository.findById(action.getId())).isPresent();
        assertThat(workspaceService.getUserChannels(workspace.getUniqueId()).getFirst().getIdentityUserUniqueId())
                .isNull();
    }

    @Test
    void confirmEmail_whenExistingUserChannelHasVerification_verifiesItAndConsumesTheAction() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);
        actionTokenService.confirm(channel);
        var verification = replaceWithKnownSecretAction(actionTokenRepository.findAll().getFirst());

        assertThat(actionTokenProcessorService.confirmEmail("1_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").success()).isFalse();
        assertThat(actionTokenProcessorService.confirmEmail(actionKey(verification.getId())).success()).isTrue();
        assertThat(actionTokenProcessorService.confirmEmail(actionKey(verification.getId())).success()).isFalse();

        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        assertThat(channelEntity.getVerifiedAt()).isNotNull();
    }

    @Test
    void resetPassword_whenValid_changesPasswordConsumesTokenAndStoresArgonHash() {
        var context = issueKnownSecretResetToken();

        var result = actionTokenProcessorService.resetPassword(actionKey(context.tokenId()), "Abcdefghi1");

        assertThat(result.success()).isTrue();
        assertThat(result.username()).isEqualTo(context.email());
        assertThat(actionTokenRepository.findById(context.tokenId())).isEmpty();

        var updated = userRepository.findById(context.userUniqueId()).orElseThrow();
        // Stored only as a one-way argon2 hash, never the raw input (FR-009 / SC-005).
        assertThat(updated.getPassword()).startsWith("{argon2}");
        assertThat(updated.getPassword()).isNotEqualTo("Abcdefghi1");
        assertThat(passwordEncoder.matches("Abcdefghi1", updated.getPassword())).isTrue();
    }

    @Test
    void resetPassword_whenPasswordWeak_throwsAndKeepsTokenUsable() {
        var context = issueKnownSecretResetToken();

        assertThatThrownBy(() -> actionTokenProcessorService.resetPassword(actionKey(context.tokenId()), "weak"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception.user.password.weak");

        // Not consumed: the link remains usable for another attempt (FR-008).
        assertThat(actionTokenRepository.findById(context.tokenId())).isPresent();
    }

    @Test
    void resetPassword_whenLinkExpired_failsWithoutChangingPassword() {
        properties.setExpiresIn(Duration.ofSeconds(-1)); // token is born already expired
        var context = issueKnownSecretResetToken();
        var before = userRepository.findById(context.userUniqueId()).orElseThrow().getPassword();

        assertThat(actionTokenProcessorService.resetPassword(actionKey(context.tokenId()), "Abcdefghi1").success())
                .isFalse();

        assertThat(userRepository.findById(context.userUniqueId()).orElseThrow().getPassword()).isEqualTo(before);
    }

    @Test
    void resetPassword_whenAlreadyUsed_secondAttemptFails() {
        var context = issueKnownSecretResetToken();

        assertThat(actionTokenProcessorService.resetPassword(actionKey(context.tokenId()), "Abcdefghi1").success())
                .isTrue();
        assertThat(actionTokenProcessorService.resetPassword(actionKey(context.tokenId()), "Abcdefghi1").success())
                .isFalse();
    }

    @Test
    void resetPassword_whenSecretTampered_failsAndKeepsTokenUsable() {
        var context = issueKnownSecretResetToken();
        var tamperedKey = context.tokenId() + "_" + "B".repeat(43);

        assertThat(actionTokenProcessorService.resetPassword(tamperedKey, "Abcdefghi1").success()).isFalse();

        assertThat(actionTokenRepository.findById(context.tokenId())).isPresent();
    }

    /**
     * Provisions a user with a verified email channel, issues a {@code RESET_PASSWORD} token via the real
     * service, then swaps it for one with a known secret so a valid action key can be built.
     */
    private ResetContext issueKnownSecretResetToken() {
        var email = "john" + nextLong() + "@example.com";
        var user = createIdentityUser(email);
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel(email, userEntity);
        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        channelEntity.setVerifiedAt(clock.instant());
        channelRepository.save(channelEntity);
        channelRepository.flush();
        actionTokenRepository.deleteAll();
        commandRepository.deleteAll();

        actionTokenService.requestPasswordReset(email, null);
        var token = replaceWithKnownSecretAction(actionTokenRepository.findAll().getFirst());
        return new ResetContext(token.getId(), user.getUniqueId().getLongValue(), email);
    }

    private record ResetContext(Long tokenId, Long userUniqueId, String email) {
    }

    private vg.identity.entity.IdentityActionTokenEntity replaceWithKnownSecretAction(
            vg.identity.entity.IdentityActionTokenEntity action
    ) {
        actionTokenRepository.delete(action);
        return actionTokenRepository.save(
                vg.identity.entity.IdentityActionTokenEntity.builder()
                        .secretHash(OpaqueKey.sha256(rawSecret()))
                        .actionType(action.getActionType())
                        .principalType(action.getPrincipalType())
                        .principal(action.getPrincipal())
                        .identityUserChannel(action.getIdentityUserChannel())
                        .payload(action.getPayload())
                        .createdAt(action.getCreatedAt())
                        .expireAt(action.getExpireAt())
                        .build()
        );
    }

    private static String actionKey(Long id) {
        return id + "_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    }

    private static byte[] rawSecret() {
        return Base64.getUrlDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    }
}

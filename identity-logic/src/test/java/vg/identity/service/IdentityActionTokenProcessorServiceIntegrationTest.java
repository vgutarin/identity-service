package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.UserProvisioningDetails;

import java.util.UUID;

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
    private PasswordEncoder passwordEncoder;

    @Test
    void confirmEmail_whenWorkspaceInvitationIsPending_provisionsUserWithDisplayNameAndPassword() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "user" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);
        var action = actionTokenRepository.findAll().getFirst();

        var result = actionTokenProcessorService.confirmEmail(
                action.getId(),
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
    void confirmEmail_whenPendingInvitationAndPasswordWeak_throwsAndKeepsActionUnconsumed() {
        var workspace = workspaceService.create(IdentityWorkspace.builder().name(nextString()).build());
        var email = "user" + nextLong() + "@example.com";

        workspaceService.addUser(workspace.getUniqueId(), email);
        var action = actionTokenRepository.findAll().getFirst();

        assertThatThrownBy(() -> actionTokenProcessorService.confirmEmail(
                action.getId(),
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
        var action = actionTokenRepository.findAll().getFirst();

        assertThatThrownBy(() -> actionTokenProcessorService.confirmEmail(
                action.getId(),
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
        var verification = actionTokenRepository.findAll().getFirst();

        assertThat(actionTokenProcessorService.confirmEmail(UUID.randomUUID()).success()).isFalse();
        assertThat(actionTokenProcessorService.confirmEmail(verification.getId()).success()).isTrue();
        assertThat(actionTokenProcessorService.confirmEmail(verification.getId()).success()).isFalse();

        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        assertThat(channelEntity.getVerifiedAt()).isNotNull();
    }
}

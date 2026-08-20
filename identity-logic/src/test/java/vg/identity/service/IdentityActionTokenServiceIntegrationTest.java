package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import vg.identity.BaseIntegrationTest;
import vg.identity.IdentityActionTokenProperties;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;
import vg.identity.model.IdentityPrincipalType;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

class IdentityActionTokenServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdentityActionTokenService service;
    @Autowired
    private IdentityUserChannelService channelService;
    @Autowired
    private IdentityActionTokenProperties properties;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanUp();
        properties.setVerifyEmailBaseUrl("https://example.com/verify/");
        properties.setResetPasswordBaseUrl("https://example.com/reset/");
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
    }

    @Test
    void confirm_whenEmailChannelProvided_createsVerificationAndEnqueuesEmail() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);

        service.confirm(channel);

        var verifications = actionTokenRepository.findAll();
        assertThat(verifications).hasSize(1);
        var verification = verifications.getFirst();
        assertThat(verification.getId()).isNotNull();
        assertThat(verification.getActionType()).isEqualTo(IdentityActionType.CONFIRM_EMAIL);
        assertThat(verification.getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(verification.getPrincipal().getUniqueId()).isEqualTo(user.getUniqueId().getLongValue());
        assertThat(verification.getIdentityUserChannel().getUniqueId()).isEqualTo(channel.getUniqueId().getLongValue());
        assertThat(verification.getPayload()).isNull();
        assertThat(verification.getCreatedAt()).isNotNull();
        assertThat(verification.getExpireAt()).isEqualTo(verification.getCreatedAt().plus(Duration.ofHours(2)));

        var commands = commandRepository.findAll();
        assertThat(commands).hasSize(1);
        var command = commands.getFirst();
        assertThat(command.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(command.getCommandType()).isEqualTo(IdentityCommandType.SEND_EMAIL);

        var email = objectMapper.readValue(command.getPayload(), EmailMessage.class);
        assertThat(email.to()).containsExactly("john@example.com");
        assertThat(email.html()).isTrue();
        // Bilingual subject: Ukrainian first, English second.
        assertThat(email.subject())
                .contains("Підтвердьте вашу електронну адресу")
                .contains("Confirm your email address");
        // No Telegram bot is registered here, so the email is web-only in both languages.
        assertThat(email.body())
                .contains("Вітаємо!")
                .contains("Hello,")
                .contains("https://example.com/verify/" + verification.getId())
                .doesNotContain("t.me");
    }

    @Test
    void confirm_whenVerificationWasRequestedInsideCooldown_doesNotCreateVerificationAndDoesNotEnqueueEmail() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);

        service.confirm(channel);
        service.confirm(channel);

        assertThat(actionTokenRepository.findAll()).hasSize(1);
        assertThat(commandRepository.findAll()).hasSize(1);
    }

    @Test
    void requestPasswordReset_whenVerifiedEmail_issuesTokenAndEnqueuesEmail() {
        var verified = setupVerifiedUser();

        service.requestPasswordReset(verified.email(), null);

        var tokens = actionTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        var token = tokens.getFirst();
        assertThat(token.getActionType()).isEqualTo(IdentityActionType.RESET_PASSWORD);
        assertThat(token.getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(token.getPrincipal().getUniqueId()).isEqualTo(verified.userUniqueId());
        assertThat(token.getIdentityUserChannel().getUniqueId()).isEqualTo(verified.channelUniqueId());
        assertThat(token.getExpireAt()).isEqualTo(token.getCreatedAt().plus(Duration.ofHours(2)));

        var commands = commandRepository.findAll();
        assertThat(commands).hasSize(1);
        var command = commands.getFirst();
        assertThat(command.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(command.getCommandType()).isEqualTo(IdentityCommandType.SEND_EMAIL);

        var email = objectMapper.readValue(command.getPayload(), EmailMessage.class);
        assertThat(email.to()).containsExactly(verified.email());
        assertThat(email.subject())
                .contains("Скидання пароля")
                .contains("Reset your password");
        assertThat(email.body()).contains("https://example.com/reset/" + token.getId());
    }

    @Test
    void requestPasswordReset_whenUnknownOrUnverifiedEmail_issuesNothing() {
        // Unknown address → neutral no-op.
        service.requestPasswordReset("nobody" + nextLong() + "@example.com", null);
        assertThat(actionTokenRepository.findAll()).isEmpty();
        assertThat(commandRepository.findAll()).isEmpty();

        // Existing but unverified channel (attached to a user, no verifiedAt) → still a neutral no-op.
        var user = createIdentityUser("jane" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var email = "jane" + nextLong() + "@example.com";
        channelService.createEmailChannel(email, userEntity);
        actionTokenRepository.deleteAll();
        commandRepository.deleteAll();

        service.requestPasswordReset(email, null);

        assertThat(actionTokenRepository.findAll()).isEmpty();
        assertThat(commandRepository.findAll()).isEmpty();
    }

    @Test
    void requestPasswordReset_whenWithinCooldown_issuesOnlyOnce() {
        var verified = setupVerifiedUser();

        service.requestPasswordReset(verified.email(), null);
        service.requestPasswordReset(verified.email(), null);

        assertThat(actionTokenRepository.findAll()).hasSize(1);
        assertThat(commandRepository.findAll()).hasSize(1);
    }

    @Test
    void requestPasswordReset_afterPreviousLinkConsumed_issuesFreshUsableToken() {
        var verified = setupVerifiedUser();

        service.requestPasswordReset(verified.email(), null);
        var firstTokenId = actionTokenRepository.findAll().getFirst().getId();

        // Simulate the first link being used or expired: a consumed token is hard-deleted, and once it is
        // gone the per-email cooldown no longer blocks a fresh request (US3).
        actionTokenRepository.deleteAll();
        commandRepository.deleteAll();

        service.requestPasswordReset(verified.email(), null);

        var tokens = actionTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getId()).isNotEqualTo(firstTokenId);
        assertThat(tokens.getFirst().getActionType()).isEqualTo(IdentityActionType.RESET_PASSWORD);
        assertThat(commandRepository.findAll()).hasSize(1);
    }

    /**
     * Creates a user with a verified email channel and clears the confirm-email token/command that
     * {@code createEmailChannel} enqueues, so reset assertions start from a clean slate.
     */
    private VerifiedUser setupVerifiedUser() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var email = "john" + nextLong() + "@example.com";
        var channel = channelService.createEmailChannel(email, userEntity);
        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        channelEntity.setVerifiedAt(clock.instant());
        channelRepository.save(channelEntity);
        channelRepository.flush();
        actionTokenRepository.deleteAll();
        commandRepository.deleteAll();
        return new VerifiedUser(user.getUniqueId().getLongValue(), email, channel.getUniqueId().getLongValue());
    }

    private record VerifiedUser(Long userUniqueId, String email, Long channelUniqueId) {
    }

}

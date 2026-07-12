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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
    }

    @Test
    void confirm_whenEmailChannelProvided_createsVerificationAndEnqueuesEmail() throws Exception {
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
        assertThat(email.subject()).isEqualTo("Verify your email");
        assertThat(email.body()).isEqualTo("https://example.com/verify/" + verification.getId());
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
    void confirmEmail_whenVerificationExistsAndIsNotExpired_setsChannelVerifiedAtAndReturnsTrue() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);
        service.confirm(channel);
        var verification = actionTokenRepository.findAll().getFirst();

        assertThat(service.confirmEmail(verification.getId()).success()).isTrue();
        assertThat(service.confirmEmail(UUID.randomUUID()).success()).isFalse();

        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        assertThat(channelEntity.getVerifiedAt()).isNotNull();
    }
}

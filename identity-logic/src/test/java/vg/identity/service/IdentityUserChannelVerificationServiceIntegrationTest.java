package vg.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.IdentityUserChannelVerificationProperties;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

class IdentityUserChannelVerificationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdentityUserChannelVerificationService service;
    @Autowired
    private IdentityUserChannelService channelService;
    @Autowired
    private IdentityUserChannelVerificationProperties properties;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties.setLinkPrefix("https://example.com/verify/");
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
    }

    @Test
    void verify_whenEmailChannelProvided_createsVerificationAndEnqueuesEmail() throws Exception {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);

        service.verify(channel);

        var verifications = channelVerificationRepository.findAll();
        assertThat(verifications).hasSize(1);
        var verification = verifications.getFirst();
        assertThat(verification.getId()).isNotNull();
        assertThat(verification.getIdentityUserChannel().getUniqueId()).isEqualTo(channel.getUniqueId().getLongValue());
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
    void verify_whenVerificationWasRequestedInsideCooldown_doesNotCreateVerificationAndDoesNotEnqueueEmail() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);

        service.verify(channel);
        service.verify(channel);

        assertThat(channelVerificationRepository.findAll()).hasSize(1);
        assertThat(commandRepository.findAll()).hasSize(1);
    }

    @Test
    void verifyById_whenVerificationExistsAndIsNotExpired_setsChannelVerifiedAtAndReturnsTrue() {
        var user = createIdentityUser("john" + nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel("john@example.com", userEntity);
        service.verify(channel);
        var verification = channelVerificationRepository.findAll().getFirst();

        assertThat(service.verify(verification.getId())).isTrue();
        assertThat(service.verify(UUID.randomUUID())).isFalse();

        var channelEntity = channelRepository.findById(channel.getUniqueId().getLongValue()).orElseThrow();
        assertThat(channelEntity.getVerifiedAt()).isNotNull();
    }
}

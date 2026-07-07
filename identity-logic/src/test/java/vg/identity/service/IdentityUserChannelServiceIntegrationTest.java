package vg.identity.service;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextLong;

class IdentityUserChannelServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdentityUserChannelService service;

    @Autowired
    private EncryptionService encryptionService;

    @Test
    void createEmailChannel_whenEmailIsValid_createsEmailChannel() {
        var email = "John@Example.com";
        var canonicalEmail = encryptionService.canonicalize(email);
        var user = userRepository.findById(createIdentityUser(canonicalEmail).getUniqueId()).orElseThrow();

        var channel = service.createEmailChannel(email, user);

        assertThat(channel.getUniqueId()).isNotNull();
        assertThat(channel).isInstanceOf(IdentityUserChannelEmail.class);
        assertThat(channel.getChannelType()).isEqualTo(IdentityChannelType.EMAIL);
        assertThat(channel.getChannelUserId()).isEqualTo(canonicalEmail);
        assertThat(channel.getEmail()).isEqualTo(canonicalEmail);
        assertThat(channel.getIdentityUserUniqueId()).isEqualTo(new UniqueId(user.getUniqueId()));
        assertThat(channelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.EMAIL,
                encryptionService.hashCaseSensitive(canonicalEmail)
        )).hasValueSatisfying(entity -> assertThat(entity.getIdentityUser()).isEqualTo(user));
    }

    @Test
    void createEmailChannel_whenEmailDiffersByCase_throwsException() {
        var user = userRepository.findById(createIdentityUser("john@example.com").getUniqueId()).orElseThrow();

        var first = service.createEmailChannel("John@Example.com", user);

        assertThatThrownBy(() -> service.createEmailChannel("john@example.com", user))
                .isInstanceOf(Exception.class);
        assertThat(first.getUniqueId()).isNotNull();
        assertThat(channelRepository.findAll()).hasSize(1);
    }

    @Test
    void createEmailChannel_whenChannelIsAttachedToUser_returnsUserUniqueId() {
        var email = "user" + nextLong() + "@example.com";
        var user = userRepository.findById(createIdentityUser(email).getUniqueId()).orElseThrow();

        var result = service.createEmailChannel(email, user);

        assertThat(result.getIdentityUserUniqueId()).isEqualTo(new UniqueId(user.getUniqueId()));
    }

    @Test
    void findEmailChannel_whenChannelExists_returnsEmailChannel() {
        var email = "user" + nextLong() + "@example.com";
        var user = userRepository.findById(createIdentityUser(email).getUniqueId()).orElseThrow();
        var created = service.createEmailChannel(email, user);

        var result = service.findEmailChannel(email.toUpperCase());

        assertThat(result).isNotNull();
        assertThat(result.getUniqueId()).isEqualTo(created.getUniqueId());
        assertThat(result.getIdentityUserUniqueId()).isEqualTo(new UniqueId(user.getUniqueId()));
        assertThat(channelRepository.findAll()).hasSize(1);
    }

    @Test
    void findEmailChannel_whenChannelDoesNotExist_returnsNull() {
        var email = "missing" + nextLong() + "@example.com";

        assertThat(service.findEmailChannel(email)).isNull();
        assertThat(channelRepository.findAll()).isEmpty();
    }

    @Test
    void createEmailChannel_whenEmailIsInvalid_throwsConstraintViolationException() {
        var email = "not-an-email";
        var user = userRepository.findById(createIdentityUser("john@example.com").getUniqueId()).orElseThrow();

        assertThatThrownBy(() -> service.createEmailChannel(email, user))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(channelRepository.findAll()).isEmpty();
    }

    @Test
    void findEmailChannel_whenEmailIsInvalid_throwsConstraintViolationException() {
        var email = "not-an-email";

        assertThatThrownBy(() -> service.findEmailChannel(email))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(channelRepository.findAll()).isEmpty();
    }
}

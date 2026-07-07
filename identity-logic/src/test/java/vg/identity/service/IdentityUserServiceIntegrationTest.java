package vg.identity.service;

import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "test-user", authorities = "ROLE_OWNER")
class IdentityUserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    IdentityUserService service;

    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    EncryptionService encryptionService;

    private String name;
    private String password;


    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
    }

    @Test
    void create_whenValidInput_returnsCreatedUser() {
        var savedModel = service.create(buildModel());

        assertThat(
                savedModel.getUsername()
        ).isEqualTo(
                name
        );

        assertThat(
                passwordEncoder.matches(
                        password,
                        savedModel.getPassword()
                )
        ).isTrue();

        assertThat(
                savedModel.getCreatedAt()
        ).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );

        assertThat(
                savedModel.getUniqueId()
        ).isNotNull();


        assertThat(
                savedModel.getVersion()
        ).isEqualTo(
                0
        );

        assertThat(principalRepository.findById(savedModel.getUniqueId())).isPresent();
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedUser() {
        var savedModel = service.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newPassword = nextString();

        savedModel.setUsername(newName);
        savedModel.setPassword(newPassword);


        var updatedModel = service.update(savedModel);

        assertThat(updatedModel).isSameAs(savedModel);

        assertThat(
                updatedModel.getUsername()
        ).isEqualTo(
                newName
        );

        assertThat(
                passwordEncoder.matches(
                        newPassword,
                        updatedModel.getPassword()
                )
        ).isTrue();

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );

    }

    @Test
    void findByUsername_whenUserExists_returnsUser() {
        service.create(buildModel());

        var found = service.findByUsername(name);

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(name);
    }

    @Test
    void findByUsername_whenUsernameCaseDiffers_returnsUser() {
        service.create(buildModel());

        var found = service.findByUsername(name.toUpperCase());

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(name);
    }

    @Test
    void create_whenUsernameAlreadyExists_throwsDataIntegrityViolationException() {
        service.create(buildModel());

        var duplicate = buildModel();
        duplicate.setUsername(name.toUpperCase());

        // Should throw due to DB unique constraint on usernameHash
        assertThatThrownBy(
                () -> service.create(duplicate)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void create_whenUsernameIsEmail_createsAttachedEmailChannel() {
        var email = "user" + nextLong() + "@example.com";
        var user = service.create(IdentityUser.builder()
                .username(email)
                .password(password)
                .build());

        var channel = channelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.EMAIL,
                encryptionService.hashCaseSensitive(encryptionService.canonicalize(email))
        ).orElseThrow();

        assertThat(channel.getIdentityUser()).isNotNull();
        assertThat(channel.getIdentityUser().getUniqueId()).isEqualTo(user.getUniqueId().getLongValue());
    }

    @Test
    void getOrCreateEntityByUsername_whenUserDoesNotExistAndUsernameIsEmail_createsUserWithAttachedEmailChannel() {
        var email = "user" + nextLong() + "@example.com";

        var entity = service.getOrCreateEntityByUsername(email);

        var channel = channelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.EMAIL,
                encryptionService.hashCaseSensitive(encryptionService.canonicalize(email))
        ).orElseThrow();
        assertThat(channel.getIdentityUser()).isNotNull();
        assertThat(channel.getIdentityUser().getUniqueId()).isEqualTo(entity.getUniqueId());
    }

    private IdentityUser buildModel() {
        return IdentityUser.builder()
                .uniqueId(null)
                .username(name)
                .password(password)
                .build();
    }
}

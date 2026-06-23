package vg.identity.service;

import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;


class IdentityPrincipalServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    IdentityUserRepository repository;

    @Autowired
    IdentityPrincipalRepository principalRepository;

    @Autowired
    IdentityUserChannelRepository channelRepository;

    @Autowired
    IdentityPrincipalService service;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EncryptionService encryptionService;

    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;

    private String name;
    private String password;


    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
    }

    @AfterEach
    void cleanUp() {
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        repository.deleteAll();
        principalRepository.deleteAll();
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
    void getByChannel_whenUserDoesNotExist_returnsNewUser() {
        var channelType = IdentityChannelType.TELEGRAM_USER;
        var channelUserId = nextString();

        var user = service.get(channelType, channelUserId);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).startsWith("user_");
        assertThat(user.getUniqueId()).isNotNull();

        // Check if channel was created
        var channel = channelRepository.findByChannelTypeAndChannelUserIdHash(
                channelType, encryptionService.hashCaseSensitive(channelUserId)
        ).orElse(null);
        assertThat(channel).isNotNull();
        assertThat(channel.getIdentityUser().getUniqueId()).isEqualTo(user.getUniqueId().getLongValue());
    }

    @Test
    void getByChannel_whenUserExists_returnsUser() {
        var channelType = IdentityChannelType.TELEGRAM_USER;
        var channelUserId = nextString();

        var firstUser = service.get(channelType, channelUserId);
        var secondUser = service.get(channelType, channelUserId);

        assertThat(secondUser.getUniqueId()).isEqualTo(firstUser.getUniqueId());
        assertThat(secondUser.getUsername()).isEqualTo(firstUser.getUsername());
    }

    @Test
    void getByChannel_whenChannelUserIdCaseDiffers_returnsDifferentUsers() {
        var channelType = IdentityChannelType.TELEGRAM_USER;
        var channelUserId = "SomeUser";

        var firstUser = service.get(channelType, channelUserId);
        var secondUser = service.get(channelType, channelUserId.toLowerCase());

        assertThat(secondUser.getUniqueId()).isNotEqualTo(firstUser.getUniqueId());
    }

    private IdentityUser buildModel() {
        return IdentityUser.builder()
                .uniqueId(null)
                .username(name)
                .password(password)
                .build();
    }
}

package vg.identity.service;

import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.CommunicationChannelType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityUserCommunicationChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.utils.HashUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;


class UserServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    IdentityUserRepository repository;

    @Autowired
    IdentityUserCommunicationChannelRepository channelRepository;

    @Autowired
    IdentityUserServiceImpl service;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String name;
    private String password;


    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
    }

    @AfterEach
    void cleanUp() {
        channelRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void create() {
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
    }

    @Test
    void update() {
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
    void findByUsername() {
        service.create(buildModel());

        var found = service.findByUsername(name);

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(name);
    }

    @Test
    void findByUsername_caseInsensitive() {
        service.create(buildModel());

        var found = service.findByUsername(name.toUpperCase());

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(name);
    }

    @Test
    void create_duplicateUsername_throwsException() {
        service.create(buildModel());

        var duplicate = buildModel();
        duplicate.setUsername(name.toUpperCase());

        // Should throw due to DB unique constraint on usernameHash
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            service.create(duplicate);
        });
    }

    @Test
    void getByChannel_createsNewUser() {
        var channelType = CommunicationChannelType.TELEGRAM;
        var channelUserId = nextString();

        var user = service.get(channelType, channelUserId);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).startsWith("user_");
        assertThat(user.getUniqueId()).isNotNull();

        // Check if channel was created
        var channel = channelRepository.findByChannelTypeAndChannelUserIdHash(
                channelType, HashUtils.hashCaseSensitive(channelUserId)
        ).orElse(null);
        assertThat(channel).isNotNull();
        assertThat(channel.getIdentityUser().getUniqueId()).isEqualTo(user.getUniqueId().value());
    }

    @Test
    void getByChannel_returnsExistingUser() {
        var channelType = CommunicationChannelType.TELEGRAM;
        var channelUserId = nextString();

        var firstUser = service.get(channelType, channelUserId);
        var secondUser = service.get(channelType, channelUserId);

        assertThat(secondUser.getUniqueId()).isEqualTo(firstUser.getUniqueId());
        assertThat(secondUser.getUsername()).isEqualTo(firstUser.getUsername());
    }

    @Test
    void getByChannel_isCaseSensitive() {
        var channelType = CommunicationChannelType.TELEGRAM;
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
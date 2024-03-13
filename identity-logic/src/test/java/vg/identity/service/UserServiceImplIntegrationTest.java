package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.User;
import vg.identity.repository.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;


class UserServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    UserRepository repository;

    @Autowired
    UserServiceImpl service;

    private String name;
    private String password;

    private Instant creationTime;

    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
        creationTime = clock.instant();
    }

    @AfterEach
    void cleanUp() {
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
                savedModel.getPassword()
        ).isEqualTo(
                password
        );

        assertThat(
                savedModel.getCreatedAtTime()
        ).isEqualTo(
                creationTime
        );

        assertThat(
                savedModel.getUniqueId()
        ).isNotNull();


        assertThat(
                savedModel.getVersion()
        ).isEqualTo(
                0
        );

        assertThat(service.getAll()).contains(savedModel);
    }

    @Test
    void update() {
        var savedModel = service.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newPassword = nextString();
        var newCreationTime = creationTime.plusSeconds(10 + nextLong());

        savedModel.setUsername(newName);
        savedModel.setPassword(newPassword);
        savedModel.setCreatedAtTime(newCreationTime);

        var updatedModel = service.update(savedModel);

        assertThat(updatedModel).isSameAs(savedModel);

        assertThat(
                updatedModel.getUsername()
        ).isEqualTo(
                newName
        );

        assertThat(
                updatedModel.getPassword()
        ).isEqualTo(
                newPassword
        );

        assertThat(
                updatedModel.getCreatedAtTime()
        ).isEqualTo(
                newCreationTime
        );

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );

        assertThat(service.getAll()).contains(updatedModel);
    }

    private User buildModel() {
        return User.builder()
                .uniqueId(null)
                .username(name)
                .password(password)
                .createdAtTime(creationTime)
                .build();
    }
}
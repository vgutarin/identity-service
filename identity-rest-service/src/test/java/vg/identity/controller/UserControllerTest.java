package vg.identity.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import vg.identity.BaseRestControllerTest;
import vg.identity.model.User;
import vg.identity.rest.v1.UserServiceApiRestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

class UserControllerTest extends BaseRestControllerTest {

    @Autowired
    UserServiceApiRestClient restClient;

    @Autowired
    ApplicationContext applicationContext;

    private String name;
    private String password;

    private Instant creationTime;

    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
        creationTime = clock.instant();
    }

    @Test
    void create() {
        var savedModel = restClient.create(buildModel());

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

        assertThat(restClient.getAll()).contains(savedModel);
    }

    @Test
    void update() {
        var savedModel = restClient.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newPassword = nextString();
        var newCreationTime = creationTime.plusSeconds(10 + nextLong());

        savedModel.setUsername(newName);
        savedModel.setPassword(newPassword);
        savedModel.setCreatedAtTime(newCreationTime);

        var updatedModel = restClient.update(savedModel);

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

        assertThat(restClient.getAll()).contains(updatedModel);
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
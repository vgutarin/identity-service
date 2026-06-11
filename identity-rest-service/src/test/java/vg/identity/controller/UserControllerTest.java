package vg.identity.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import vg.identity.BaseRestControllerTest;
import vg.identity.model.IdentityUser;
import vg.identity.rest.v1.IdentityUserServiceApiRestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

@Disabled
class UserControllerTest extends BaseRestControllerTest {

    @Autowired
    IdentityUserServiceApiRestClient restClient;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String name;
    private String password;

    @BeforeEach
    void setUp() {
        name = nextString();
        password = nextString();
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
                passwordEncoder.matches(
                        password,
                        savedModel.getPassword()
                )
        ).isTrue();

        assertThat(
                savedModel.getCreatedAt()
        ).isNotNull();

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
        var savedModel = restClient.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newPassword = nextString();

        savedModel.setUsername(newName);
        savedModel.setPassword(newPassword);

        var updatedModel = restClient.update(savedModel);

        assertThat(
                updatedModel.getUsername()
        ).isEqualTo(
                newName
        );

        passwordEncoder.matches(
                newPassword,
                updatedModel.getPassword()
        );

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );
    }

    private IdentityUser buildModel() {
        return IdentityUser.builder()
                .uniqueId(null)
                .username(name)
                .password(password)
                .build();
    }

}
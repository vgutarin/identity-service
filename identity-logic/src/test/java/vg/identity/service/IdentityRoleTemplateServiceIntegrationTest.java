package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityRoleTemplateServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityRoleTemplateService service;
    @Autowired
    IdentityRoleTemplateRepository roleTemplateRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;

    private String name;

    @BeforeEach
    void setUp() {
        name = nextString();
    }

    @AfterEach
    void cleanUp() {
        roleTemplateRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void create() {
        var description = nextString();
        var saved = service.create(IdentityRoleTemplate.builder()
                .name(name)
                .description(description)
                .permissions(Set.of(" Workspace.READ ", "app.update"))
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getPermissions()).containsExactlyInAnyOrder("workspace.read", "app.update");
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(saved.getVersion()).isEqualTo(0);

        assertThat(permissionRepository.findByName("workspace.read")).isPresent();
        assertThat(permissionRepository.findByName("app.update")).isPresent();
    }

    @Test
    void getById() {
        var saved = service.create(buildRoleTemplate());

        var found = service.getById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(name);
        assertThat(found.getPermissions()).containsExactly("workspace.read");
    }

    @Test
    void getAll() {
        var first = service.create(buildRoleTemplate());
        var second = service.create(IdentityRoleTemplate.builder()
                .name(nextString())
                .permissions(Set.of("app.read"))
                .build());

        assertThat(service.getAll())
                .extracting(IdentityRoleTemplate::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void update() {
        var saved = service.create(buildRoleTemplate());
        var newDescription = nextString();

        var updated = service.update(
                IdentityRoleTemplate.builder()
                        .id(saved.getId())
                        .version(saved.getVersion())
                        .name(nextString())
                        .description(newDescription)
                        .permissions(Set.of("app.create", "workspace.delete"))
                        .build()
        );

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getName()).isEqualTo(name);
        assertThat(updated.getDescription()).isEqualTo(newDescription);
        assertThat(updated.getPermissions()).containsExactlyInAnyOrder("app.create", "workspace.delete");
        assertThat(updated.getVersion()).isEqualTo(1);

        assertThat(service.getById(saved.getId()).getPermissions())
                .containsExactlyInAnyOrder("app.create", "workspace.delete");
    }

    @Test
    void updateThrows_WhenVersionIsStale() {
        var saved = service.create(buildRoleTemplate());
        var stale = IdentityRoleTemplate.builder()
                .id(saved.getId())
                .version(saved.getVersion())
                .description(nextString())
                .build();
        var currentDescription = nextString();

        service.update(
                IdentityRoleTemplate.builder()
                        .id(saved.getId())
                        .version(saved.getVersion())
                        .description(currentDescription)
                        .permissions(Set.of("app.delete"))
                        .build()
        );

        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(roleTemplateRepository.findById(saved.getId()))
                .hasValueSatisfying(template -> {
                    assertThat(template.getDescription()).isEqualTo(currentDescription);
                    assertThat(template.getVersion()).isEqualTo(1);
                });
    }

    @Test
    void delete() {
        var saved = service.create(buildRoleTemplate());

        service.delete(saved.getId());

        assertThat(roleTemplateRepository.findById(saved.getId())).isEmpty();
        assertThat(permissionRepository.findByName("workspace.read")).isPresent();
    }

    @Test
    void addPermission() {
        var saved = service.create(buildRoleTemplate());

        var updated = service.addPermission(saved.getId(), " Workspace.WRITE ");

        assertThat(updated.getPermissions()).containsExactlyInAnyOrder("workspace.read", "workspace.write");
        assertThat(permissionRepository.findByName("workspace.write")).isPresent();
    }

    @Test
    void removePermission() {
        var saved = service.create(IdentityRoleTemplate.builder()
                .name(name)
                .permissions(Set.of("workspace.read", "workspace.write"))
                .build());

        var updated = service.removePermission(saved.getId(), " Workspace.READ ");

        assertThat(updated.getPermissions()).containsExactly("workspace.write");
        assertThat(permissionRepository.findByName("workspace.read")).isPresent();
    }

    @Test
    void getByIdThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private IdentityRoleTemplate buildRoleTemplate() {
        return IdentityRoleTemplate.builder()
                .name(name)
                .permissions(Set.of("workspace.read"))
                .build();
    }
}

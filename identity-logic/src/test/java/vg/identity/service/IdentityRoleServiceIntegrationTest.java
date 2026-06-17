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
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityWorkspaceRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityRoleServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityRoleService service;
    @Autowired
    IdentityWorkspaceService workspaceService;
    @Autowired
    IdentityRoleRepository roleRepository;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;

    private String name;

    @BeforeEach
    void setUp() {
        name = nextString();
    }

    @AfterEach
    void cleanUp() {
        roleRepository.deleteAll();
        workspaceRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void create() {
        var workspace = createWorkspace();
        var description = nextString();
        var saved = service.create(IdentityRole.builder()
                .name(name)
                .description(description)
                .workspaceUniqueId(workspace.getUniqueId().value())
                .permissions(Set.of(" Workspace.READ ", "app.update"))
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getWorkspaceUniqueId()).isEqualTo(workspace.getUniqueId().value());
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
    void create_AllowsGlobalRole() {
        var saved = service.create(buildRole());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkspaceUniqueId()).isNull();
    }

    @Test
    void getById() {
        var saved = service.create(buildRole());

        var found = service.getById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(name);
        assertThat(found.getPermissions()).containsExactly("workspace.read");
    }

    @Test
    void getAll() {
        var first = service.create(buildRole());
        var second = service.create(IdentityRole.builder()
                .name(nextString())
                .permissions(Set.of("app.read"))
                .build());

        assertThat(service.getAll())
                .extracting(IdentityRole::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void update() {
        var saved = service.create(buildRole());
        var newDescription = nextString();

        var updated = service.update(
                IdentityRole.builder()
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
        var saved = service.create(buildRole());
        var stale = IdentityRole.builder()
                .id(saved.getId())
                .version(saved.getVersion())
                .description(nextString())
                .build();
        var currentDescription = nextString();

        service.update(
                IdentityRole.builder()
                        .id(saved.getId())
                        .version(saved.getVersion())
                        .description(currentDescription)
                        .permissions(Set.of("app.delete"))
                        .build()
        );

        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(roleRepository.findById(saved.getId()))
                .hasValueSatisfying(role -> {
                    assertThat(role.getDescription()).isEqualTo(currentDescription);
                    assertThat(role.getVersion()).isEqualTo(1);
                });
    }

    @Test
    void addPermission() {
        var saved = service.create(buildRole());

        var updated = service.addPermission(saved.getId(), " app.create ");

        assertThat(updated.getPermissions()).containsExactlyInAnyOrder("workspace.read", "app.create");
    }

    @Test
    void removePermission() {
        var saved = service.create(IdentityRole.builder()
                .name(name)
                .permissions(Set.of("workspace.read", "app.create"))
                .build());

        var updated = service.removePermission(saved.getId(), " App.CREATE ");

        assertThat(updated.getPermissions()).containsExactly("workspace.read");
    }

    @Test
    void delete() {
        var saved = service.create(buildRole());

        service.delete(saved.getId());

        assertThat(roleRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void getByIdThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private IdentityRole buildRole() {
        return IdentityRole.builder()
                .name(name)
                .permissions(Set.of("workspace.read"))
                .build();
    }

    private IdentityWorkspace createWorkspace() {
        return workspaceService.create(IdentityWorkspace.builder()
                .name(nextString())
                .build());
    }
}

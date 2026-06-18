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
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.model.IdentityWorkspace;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityRoleServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityRoleService service;
    @Autowired
    IdentityRoleTemplateService roleTemplateService;
    @Autowired
    IdentityWorkspaceService workspaceService;
    @Autowired
    IdentityRoleRepository roleRepository;
    @Autowired
    IdentityRoleTemplateRepository roleTemplateRepository;
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
        roleTemplateRepository.deleteAll();
        workspaceRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void create_whenValidInput_returnsCreatedRole() {
        var description = nextString();
        var workspace = createWorkspaceEntity();
        var saved = service.create(name, description, workspace);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getWorkspaceUniqueId()).isEqualTo(workspace.getUniqueId());
        assertThat(saved.getPermissions()).isEmpty();
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(saved.getVersion()).isEqualTo(0);
    }

    @Test
    void create_whenWorkspaceIsProvided_returnsCreatedWorkspaceRole() {
        var workspace = createWorkspaceEntity();
        var saved = service.create(name, null, workspace);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkspaceUniqueId()).isEqualTo(workspace.getUniqueId());
    }

    @Test
    void createFromTemplate_whenTemplatesAreProvided_returnsCreatedRoles() {
        var workspace = createWorkspace();
        var description = nextString();
        var template = roleTemplateService.create(IdentityRoleTemplate.builder()
                .name(name)
                .description(description)
                .permissions(Set.of("workspace.read", "app.update"))
                .build());
        var templateEntity = IdentityRoleTemplateEntity.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .permissions(Set.of(
                        permissionRepository.findByName("workspace.read").orElseThrow(),
                        permissionRepository.findByName("app.update").orElseThrow()
                ))
                .build();
        var workspaceEntity = workspaceService.getEntity(workspace.getUniqueId().value());

        var saved = service.createFromTemplate(List.of(templateEntity), workspaceEntity).getFirst();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getWorkspaceUniqueId()).isEqualTo(workspace.getUniqueId().value());
        assertThat(saved.getPermissions()).containsExactlyInAnyOrder("workspace.read", "app.update");
        assertThat(roleRepository.findById(saved.getId()))
                .hasValueSatisfying(role -> {
                    assertThat(role.getWorkspace()).isNotNull();
                    assertThat(role.getWorkspace().getUniqueId()).isEqualTo(workspace.getUniqueId().value());
                });
        assertThat(service.getById(saved.getId()).getPermissions())
                .containsExactlyInAnyOrder("workspace.read", "app.update");
    }

    @Test
    void getById_whenEntityExists_returnsRole() {
        var saved = service.create(name, null, createWorkspaceEntity());

        var found = service.getById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(name);
        assertThat(found.getPermissions()).isEmpty();
    }

    @Test
    void getAll_whenEntitiesExist_returnsRoles() {
        var workspace = createWorkspaceEntity();
        var first = service.create(name, null, workspace);
        var second = service.create(nextString(), null, workspace);

        assertThat(service.getAll())
                .extracting(IdentityRole::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedRole() {
        var saved = service.create(name, null, createWorkspaceEntity());
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
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var saved = service.create(name, null, createWorkspaceEntity());
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
    void addPermission_whenEntityExists_addsPermission() {
        var saved = service.create(name, null, createWorkspaceEntity());

        var updated = service.addPermission(saved.getId(), " app.create ");

        assertThat(updated.getPermissions()).containsExactly("app.create");
    }

    @Test
    void removePermission_whenEntityExists_removesPermission() {
        var saved = service.create(name, null, createWorkspaceEntity());
        service.addPermission(saved.getId(), "workspace.read");
        service.addPermission(saved.getId(), "app.create");

        var updated = service.removePermission(saved.getId(), " App.CREATE ");

        assertThat(updated.getPermissions()).containsExactly("workspace.read");
    }

    @Test
    void delete_whenEntityExists_deleteRole() {
        var saved = service.create(name, null, createWorkspaceEntity());

        service.delete(saved.getId());

        assertThat(roleRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private IdentityWorkspace createWorkspace() {
        return workspaceService.create(IdentityWorkspace.builder()
                .name(nextString())
                .build());
    }

    private IdentityWorkspaceEntity createWorkspaceEntity() {
        var workspace = createWorkspace();
        return workspaceService.getEntity(workspace.getUniqueId().value());
    }
}

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
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.model.IdentityWorkspace;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityWorkspaceServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityWorkspaceService service;
    @Autowired
    IdentityRoleService roleService;
    @Autowired
    IdentityRoleTemplateService roleTemplateService;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    IdentityRoleRepository roleRepository;
    @Autowired
    IdentityRoleTemplateRepository roleTemplateRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;
    @Autowired
    IdentityUserRepository userRepository;
    @Autowired
    IdentityUserChannelRepository channelRepository;
    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    IdentityPrincipalRepository principalRepository;

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
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    @Test
    void create_whenValidInput_returnsCreatedWorkspace() {
        var saved = service.create(buildWorkspace());

        assertThat(saved.getUniqueId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(saved.getVersion()).isEqualTo(0);
    }

    @Test
    void create_whenRoleTemplatesExist_copiesRoleTemplatesToWorkspaceRoles() {
        var firstName = nextString();
        var firstDescription = nextString();
        var secondName = nextString();
        roleTemplateService.create(IdentityRoleTemplate.builder()
                .name(firstName)
                .description(firstDescription)
                .permissions(Set.of("workspace.read", "workspace.write"))
                .build());
        roleTemplateService.create(IdentityRoleTemplate.builder()
                .name(secondName)
                .permissions(Set.of("app.read"))
                .build());

        var saved = service.create(buildWorkspace());
        var workspace = workspaceRepository.findById(saved.getUniqueId().value()).orElseThrow();
        var adminRole = roleRepository.findByNameAndWorkspace(firstName, workspace).orElseThrow();
        var secondRole = roleRepository.findByNameAndWorkspace(secondName, workspace).orElseThrow();

        assertThat(roleService.getById(adminRole.getId()))
                .satisfies(role -> {
                    assertThat(role.getDescription()).isEqualTo(firstDescription);
                    assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().value());
                    assertThat(role.getPermissions()).containsExactlyInAnyOrder("workspace.read", "workspace.write");
                });
        assertThat(roleService.getById(secondRole.getId()))
                .satisfies(role -> {
                    assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().value());
                    assertThat(role.getPermissions()).containsExactly("app.read");
                });
    }

    @Test
    void getById_whenEntityExists_returnsWorkspace() {
        var saved = service.create(buildWorkspace());

        var found = service.getById(saved.getUniqueId().value());

        assertThat(found.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(found.getName()).isEqualTo(name);
    }

    @Test
    void getAll_whenEntitiesExist_returnsWorkspaces() {
        var first = service.create(buildWorkspace());
        var second = service.create(IdentityWorkspace.builder().name(nextString()).build());

        assertThat(service.getAll())
                .extracting(workspace -> workspace.getUniqueId().value())
                .contains(first.getUniqueId().value(), second.getUniqueId().value());
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedWorkspace() {
        var saved = service.create(buildWorkspace());
        var newName = nextString();

        var updated = service.update(
                IdentityWorkspace.builder()
                        .uniqueId(saved.getUniqueId())
                        .version(saved.getVersion())
                        .name(newName)
                        .build()
        );

        assertThat(updated.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var saved = service.create(buildWorkspace());
        var stale = IdentityWorkspace.builder()
                .uniqueId(saved.getUniqueId())
                .version(saved.getVersion())
                .name(nextString())
                .build();
        var currentName = nextString();

        service.update(
                IdentityWorkspace.builder()
                        .uniqueId(saved.getUniqueId())
                        .version(saved.getVersion())
                        .name(currentName)
                        .build()
        );

        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(workspaceRepository.findById(saved.getUniqueId().value()))
                .hasValueSatisfying(workspace -> {
                    assertThat(workspace.getName()).isEqualTo(currentName);
                    assertThat(workspace.getVersion()).isEqualTo(1);
                });
    }

    @Test
    void delete_whenEntityExists_deleteWorkspace() {
        var saved = service.create(buildWorkspace());

        service.delete(saved.getUniqueId().value());

        assertThat(workspaceRepository.findById(saved.getUniqueId().value())).isEmpty();
    }

    @Test
    void delete_whenEntityIsNotFound_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> service.delete(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void addRole_whenWorkspaceExists_createRoleInWorkspace() {
        var saved = service.create(buildWorkspace());
        var roleName = nextString();
        var roleDescription = nextString();

        var role = service.addRole(saved.getUniqueId().value(), IdentityRole.builder()
                .name(roleName)
                .description(roleDescription)
                .permissions(Set.of("workspace.read"))
                .build());

        assertThat(role.getId()).isNotNull();
        assertThat(role.getName()).isEqualTo(roleName);
        assertThat(role.getDescription()).isEqualTo(roleDescription);
        assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().value());
        assertThat(role.getPermissions()).isEmpty();
        assertThat(roleRepository.findById(role.getId()))
                .hasValueSatisfying(entity -> {
                    assertThat(entity.getWorkspace()).isNotNull();
                    assertThat(entity.getWorkspace().getUniqueId()).isEqualTo(saved.getUniqueId().value());
                });
    }

    private IdentityWorkspace buildWorkspace() {
        return IdentityWorkspace.builder()
                .name(name)
                .build();
    }
}

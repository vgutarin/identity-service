package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.model.IdentityWorkspace;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextLong;
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
    JdbcTemplate jdbcTemplate;

    private String name;

    @BeforeEach
    void setUp() {
        name = nextString();
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
        var workspace = workspaceRepository.findById(saved.getUniqueId().getLongValue()).orElseThrow();
        var adminRole = roleRepository.findByNameAndWorkspace(firstName, workspace).orElseThrow();
        var secondRole = roleRepository.findByNameAndWorkspace(secondName, workspace).orElseThrow();

        assertThat(roleService.getById(adminRole.getId()))
                .satisfies(role -> {
                    assertThat(role.getDescription()).isEqualTo(firstDescription);
                    assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
                    assertThat(role.getPermissions()).containsExactlyInAnyOrder("workspace.read", "workspace.write");
                });
        assertThat(roleService.getById(secondRole.getId()))
                .satisfies(role -> {
                    assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
                    assertThat(role.getPermissions()).containsExactly("app.read");
                });
    }

    @Test
    void getById_whenEntityExists_returnsWorkspace() {
        var saved = service.create(buildWorkspace());

        var found = service.getById(saved.getUniqueId());

        assertThat(found.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(found.getName()).isEqualTo(name);
    }

    @Test
    void getAll_whenEntitiesExist_returnsWorkspaces() {
        var first = service.create(buildWorkspace());
        var second = service.create(IdentityWorkspace.builder().name(nextString()).build());

        assertThat(service.getAll())
                .extracting(workspace -> workspace.getUniqueId().getLongValue())
                .contains(first.getUniqueId().getLongValue(), second.getUniqueId().getLongValue());
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
        assertThat(workspaceRepository.findById(saved.getUniqueId().getLongValue()))
                .hasValueSatisfying(workspace -> {
                    assertThat(workspace.getName()).isEqualTo(currentName);
                    assertThat(workspace.getVersion()).isEqualTo(1);
                });
    }

    @Test
    void delete_whenEntityExists_deleteWorkspace() {
        var saved = service.create(buildWorkspace());

        service.delete(saved.getUniqueId());

        assertThat(workspaceRepository.findById(saved.getUniqueId().getLongValue())).isEmpty();
    }

    @Test
    void delete_whenEntityIsNotFound_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> service.delete(new UniqueId(Long.MAX_VALUE)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createRole_whenWorkspaceExists_createRoleInWorkspace() {
        var saved = service.create(buildWorkspace());
        var roleName = nextString();
        var roleDescription = nextString();

        var role = service.createRole(saved.getUniqueId(), IdentityRole.builder()
                .name(roleName)
                .description(roleDescription)
                .permissions(Set.of("workspace.read"))
                .build());

        assertThat(role.getId()).isNotNull();
        assertThat(role.getName()).isEqualTo(roleName);
        assertThat(role.getDescription()).isEqualTo(roleDescription);
        assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
        assertThat(role.getPermissions()).isEmpty();
        assertThat(roleRepository.findById(role.getId()))
                .hasValueSatisfying(entity -> {
                    assertThat(entity.getWorkspace()).isNotNull();
                    assertThat(entity.getWorkspace().getUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
                });
    }

    @Test
    void workspaceUsers_whenUserIsAdded_persistsManyToManyRelation() {
        var workspace = service.create(buildWorkspace());
        var user = createIdentityUser(nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var workspaceEntity = workspaceRepository.findById(workspace.getUniqueId().getLongValue()).orElseThrow();
        workspaceEntity.setUsers(new HashSet<>(Set.of(userEntity)));

        workspaceRepository.saveAndFlush(workspaceEntity);

        var relationCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM identity_workspace_user
                        WHERE workspace_unique_id = ? AND user_unique_id = ?
                        """,
                Long.class,
                workspace.getUniqueId().getLongValue(),
                user.getUniqueId().getLongValue()
        );
        assertThat(relationCount).isEqualTo(1);
    }

    @Test
    void addUser_whenUserExists_attachesUserToWorkspace() {
        var workspace = service.create(buildWorkspace());
        var email = "user" + nextLong() + "@example.com";
        var user = createIdentityUser(email);

        var updated = service.addUser(workspace.getUniqueId(), email);

        assertThat(updated.getUniqueId()).isEqualTo(workspace.getUniqueId());
        assertThat(workspaceUserRelationCount(workspace.getUniqueId().getLongValue(), user.getUniqueId().getLongValue()))
                .isEqualTo(1);
    }

    @Test
    void addUser_whenUserDoesNotExist_createsUserAndAttachesToWorkspace() {
        var workspace = service.create(buildWorkspace());
        var email = "user" + nextLong() + "@example.com";

        service.addUser(workspace.getUniqueId(), email);

        var user = userRepository.findAll().stream()
                .filter(entity -> email.equals(entity.getUsername()))
                .findFirst()
                .orElseThrow();
        assertThat(workspaceUserRelationCount(workspace.getUniqueId().getLongValue(), user.getUniqueId()))
                .isEqualTo(1);
    }

    @Test
    void addUser_whenEmailIsInvalid_throwsConstraintViolationException() {
        var workspace = service.create(buildWorkspace());

        assertThatThrownBy(() -> service.addUser(workspace.getUniqueId(), "not-an-email"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private IdentityWorkspace buildWorkspace() {
        return IdentityWorkspace.builder()
                .name(name)
                .build();
    }

    private Long workspaceUserRelationCount(long workspaceUniqueId, long userUniqueId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM identity_workspace_user
                        WHERE workspace_unique_id = ? AND user_unique_id = ?
                        """,
                Long.class,
                workspaceUniqueId,
                userUniqueId
        );
    }
}

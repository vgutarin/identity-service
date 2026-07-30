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
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.model.IdentityWorkspace;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    @Autowired
    IdentityUserChannelService channelService;

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

        assertThat(roleService.getById(new UniqueId(adminRole.getUniqueId())))
                .satisfies(role -> {
                    assertThat(role.getDescription()).isEqualTo(firstDescription);
                    assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
                    assertThat(role.getPermissions()).containsExactlyInAnyOrder("workspace.read", "workspace.write");
                });
        assertThat(roleService.getById(new UniqueId(secondRole.getUniqueId())))
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

        assertThat(role.getUniqueId()).isNotNull();
        assertThat(role.getName()).isEqualTo(roleName);
        assertThat(role.getDescription()).isEqualTo(roleDescription);
        assertThat(role.getWorkspaceUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
        assertThat(role.getPermissions()).isEmpty();
        assertThat(roleRepository.findById(role.getUniqueId()))
                .hasValueSatisfying(entity -> {
                    assertThat(entity.getWorkspace()).isNotNull();
                    assertThat(entity.getWorkspace().getUniqueId()).isEqualTo(saved.getUniqueId().getLongValue());
                });
    }

    @Test
    void workspaceUserChannels_whenChannelIsAdded_persistsManyToManyRelation() {
        var workspace = service.create(buildWorkspace());
        var email = "user" + nextLong() + "@example.com";
        var user = createIdentityUser(email);
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var userChannel = channelService.createEmailChannel(email, userEntity);
        var channelEntity = channelRepository.findById(userChannel.getUniqueId().getLongValue()).orElseThrow();
        var workspaceEntity = workspaceRepository.findById(workspace.getUniqueId().getLongValue()).orElseThrow();
        workspaceEntity.setUserChannels(Set.of(channelEntity));

        workspaceRepository.saveAndFlush(workspaceEntity);

        var relationCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM identity_workspace_user_channel
                        WHERE workspace_unique_id = ? AND user_channel_unique_id = ?
                        """,
                Long.class,
                workspace.getUniqueId().getLongValue(),
                userChannel.getUniqueId().getLongValue()
        );
        assertThat(relationCount).isEqualTo(1);
    }

    @Test
    void addUser_whenUserExists_attachesEmailChannelToWorkspace() {
        var workspace = service.create(buildWorkspace());
        var email = "user" + nextLong() + "@example.com";
        var user = createIdentityUser(email);
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelService.createEmailChannel(email, userEntity);

        var updated = service.addUser(workspace.getUniqueId(), email);

        assertThat(updated.getUniqueId()).isEqualTo(workspace.getUniqueId());
        assertThat(workspaceChannelRelationCount(workspace.getUniqueId().getLongValue(), channel.getUniqueId().getLongValue()))
                .isEqualTo(1);
    }

    @Test
    void addUser_whenUserDoesNotExist_createsPendingChannelWithoutCreatingAUser() {
        var workspace = service.create(buildWorkspace());
        var email = "user" + nextLong() + "@example.com";

        service.addUser(workspace.getUniqueId(), email);

        var channel = channelRepository.findById(channelService.findEmailChannel(email).getUniqueId()).orElseThrow();
        assertThat(channel.getIdentityUser()).isNull();
        assertThat(userRepository.findAll())
                .noneMatch(entity -> email.equals(entity.getPrincipal().getName()));
        assertThat(workspaceChannelRelationCount(workspace.getUniqueId().getLongValue(), channel.getUniqueId()))
                .isEqualTo(1);
        assertThat(service.getUserChannels(workspace.getUniqueId()))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getIdentityUserUniqueId()).isNull();
                    assertThat(member.isVerified()).isFalse();
                });
    }

    @Test
    void addChannel_whenVerifiedTelegramChannelExists_attachesItToWorkspace() {
        var workspace = service.create(buildWorkspace());
        var user = createIdentityUser(nextString());
        var userEntity = userRepository.findById(user.getUniqueId().getLongValue()).orElseThrow();
        var channel = channelRepository.saveWithNewUniqueId(
                IdentityUserChannelEntity.builder()
                        .channelType(IdentityChannelType.TELEGRAM_USER)
                        .channelUserId(String.valueOf(nextLong()))
                        .channelUserIdHash(new byte[32])
                        .identityUser(userEntity)
                        .verifiedAt(Instant.now())
                        .build(),
                uniqueIdService
        );
        channelRepository.flush();

        service.addChannel(workspace.getUniqueId(), new UniqueId(channel.getUniqueId()));

        assertThat(workspaceChannelRelationCount(workspace.getUniqueId().getLongValue(), channel.getUniqueId()))
                .isEqualTo(1);
        assertThat(service.getUserChannels(workspace.getUniqueId()))
                .singleElement()
                .satisfies(member -> assertThat(member.getChannelType()).isEqualTo(IdentityChannelType.TELEGRAM_USER));
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

    private Long workspaceChannelRelationCount(long workspaceUniqueId, long channelUniqueId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM identity_workspace_user_channel
                        WHERE workspace_unique_id = ? AND user_channel_unique_id = ?
                        """,
                Long.class,
                workspaceUniqueId,
                channelUniqueId
        );
    }
}

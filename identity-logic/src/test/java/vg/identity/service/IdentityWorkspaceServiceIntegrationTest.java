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
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "IDENTITY_ADMIN")
class IdentityWorkspaceServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityWorkspaceService service;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
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
        workspaceRepository.deleteAll();
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    @Test
    void create() {
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
    void get() {
        var saved = service.create(buildWorkspace());

        var found = service.get(saved.getUniqueId());

        assertThat(found.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(found.getName()).isEqualTo(name);
    }

    @Test
    void findAll() {
        var first = service.create(buildWorkspace());
        var second = service.create(IdentityWorkspaceEntity.builder().name(nextString()).build());

        assertThat(service.findAll())
                .extracting(IdentityWorkspaceEntity::getUniqueId)
                .contains(first.getUniqueId(), second.getUniqueId());
    }

    @Test
    void update() {
        var saved = service.create(buildWorkspace());
        var newName = nextString();

        var updated = service.update(
                IdentityWorkspaceEntity.builder()
                        .uniqueId(saved.getUniqueId())
                        .name(newName)
                        .build()
        );

        assertThat(updated.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void updateThrows_WhenVersionIsStale() {
        var saved = service.create(buildWorkspace());
        var stale = IdentityWorkspaceEntity.builder()
                .uniqueId(saved.getUniqueId())
                .version(saved.getVersion())
                .name(nextString())
                .build();
        var currentName = nextString();

        service.update(
                IdentityWorkspaceEntity.builder()
                        .uniqueId(saved.getUniqueId())
                        .version(saved.getVersion())
                        .name(currentName)
                        .build()
        );

        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(workspaceRepository.findById(saved.getUniqueId()))
                .hasValueSatisfying(workspace -> {
                    assertThat(workspace.getName()).isEqualTo(currentName);
                    assertThat(workspace.getVersion()).isEqualTo(1);
                });
    }

    @Test
    void delete() {
        var saved = service.create(buildWorkspace());

        service.delete(saved.getUniqueId());

        assertThat(workspaceRepository.findById(saved.getUniqueId())).isEmpty();
    }

    @Test
    void deleteThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.delete(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private IdentityWorkspaceEntity buildWorkspace() {
        return IdentityWorkspaceEntity.builder()
                .name(name)
                .build();
    }
}

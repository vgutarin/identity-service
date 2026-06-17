package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityPermission;
import vg.identity.repository.IdentityPermissionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityPermissionServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityPermissionService service;
    @Autowired
    IdentityPermissionRepository permissionRepository;

    @AfterEach
    void cleanUp() {
        permissionRepository.deleteAll();
    }

    @Test
    void create() {
        var saved = service.create(IdentityPermission.builder()
                .name(" Workspace.READ ")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("workspace.read");
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(permissionRepository.findByName("workspace.read")).isPresent();
    }

    @Test
    void getById() {
        var saved = service.create(IdentityPermission.builder()
                .name("workspace.read")
                .build());

        var found = service.getById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("workspace.read");
    }

    @Test
    void getAll() {
        var first = service.create(IdentityPermission.builder()
                .name("workspace.read")
                .build());
        var second = service.create(IdentityPermission.builder()
                .name("app.read")
                .build());

        assertThat(service.getAll())
                .extracting(IdentityPermission::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void getOrCreateEntityReturnsExistingPermission() {
        var saved = service.create(IdentityPermission.builder()
                .name("workspace.read")
                .build());

        var entity = service.getOrCreateEntity(" Workspace.READ ");

        assertThat(entity.getId()).isEqualTo(saved.getId());
        assertThat(permissionRepository.findAll()).hasSize(1);
    }

    @Test
    void getOrCreateEntityCreatesMissingPermission() {
        var entity = service.getOrCreateEntity(" Workspace.READ ");

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getName()).isEqualTo("workspace.read");
        assertThat(permissionRepository.findByName("workspace.read")).isPresent();
    }

    @Test
    void getByIdThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }
}

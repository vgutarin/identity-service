package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityPermission;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityPermissionServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityPermissionService service;

    @Test
    void create_whenValidInput_returnsCreatedPermission() {
        var permissionName = permissionName();
        var saved = service.create(IdentityPermission.builder()
                .name(" " + permissionName.toUpperCase(Locale.ROOT) + " ")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(permissionName);
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(permissionRepository.findByName(permissionName)).isPresent();
    }

    @Test
    void getById_whenEntityExists_returnsPermission() {
        var permissionName = permissionName();
        var saved = service.create(IdentityPermission.builder()
                .name(permissionName)
                .build());

        var found = service.getById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(permissionName);
    }

    @Test
    void getAll_whenEntitiesExist_returnsPermissions() {
        var first = service.create(IdentityPermission.builder()
                .name(permissionName())
                .build());
        var second = service.create(IdentityPermission.builder()
                .name(permissionName())
                .build());

        assertThat(service.getAll())
                .extracting(IdentityPermission::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void getOrCreateEntity_whenPermissionExists_returnsExistingPermission() {
        var permissionName = permissionName();
        var saved = service.create(IdentityPermission.builder()
                .name(permissionName)
                .build());

        var entity = service.getOrCreateEntity(" " + permissionName.toUpperCase(Locale.ROOT) + " ");

        assertThat(entity.getId()).isEqualTo(saved.getId());
    }

    @Test
    void getOrCreateEntity_whenPermissionIsMissing_returnsCreatedPermission() {
        var permissionName = permissionName();

        var entity = service.getOrCreateEntity(" " + permissionName.toUpperCase(Locale.ROOT) + " ");

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getName()).isEqualTo(permissionName);
        assertThat(permissionRepository.findByName(permissionName)).isPresent();
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static String permissionName() {
        return "permission." + nextString().toLowerCase(Locale.ROOT);
    }
}

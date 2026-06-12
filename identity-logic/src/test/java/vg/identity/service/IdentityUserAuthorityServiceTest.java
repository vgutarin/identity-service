package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntityId;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserResourcePermission;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;
import vg.unique.id.jpa.UniqueIdEntity;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityUserAuthorityServiceTest {

    @Mock
    private IdentityUserSystemRoleRepository systemRoleRepository;

    @Mock
    private IdentityPermissionRepository permissionRepository;

    @Mock
    private IdentityUserResourcePermissionRepository resourcePermissionRepository;

    @Mock
    private UniqueIdEntity resource;

    @InjectMocks
    private IdentityUserAuthorityService service;

    @Test
    void normalizeAuthorityNameTrimsAndLowercases() {
        assertThat(IdentityUserAuthorityService.normalizeAuthorityName(" Read "))
                .isEqualTo("read");
        assertThat(IdentityUserAuthorityService.normalizeAuthorityName("ACCOUNT:WRITE"))
                .isEqualTo("account:write");
    }

    @Test
    void resourceAuthorityNameIncludesResourceIdAndNormalizedAuthorityName() {
        assertThat(IdentityUserAuthorityService.resourceAuthorityName(123L, " Read "))
                .isEqualTo("123:read");
        assertThat(IdentityUserAuthorityService.resourceAuthorityName(987L, "ACCOUNT:WRITE"))
                .isEqualTo("987:account:write");
    }

    @Test
    void assignResourceAuthority_whenPermissionIsNotRegisteredYet_createsPermissionAndRelation() {
        var user = user(11L);
        var savedPermission = IdentityPermissionEntity.builder()
                .id(31L)
                .name("read")
                .build();
        var expectedId = IdentityUserResourcePermissionEntityId.builder()
                .userUniqueId(11L)
                .resourceUniqueId(21L)
                .permissionId(31L)
                .build();

        when(resource.getUniqueId()).thenReturn(21L);
        when(permissionRepository.findByName("read")).thenReturn(Optional.empty());
        when(permissionRepository.save(any(IdentityPermissionEntity.class))).thenReturn(savedPermission);
        when(resourcePermissionRepository.existsById(expectedId)).thenReturn(false);

        service.assignResourceAuthority(resource, user, " Read ");

        var permissionCaptor = ArgumentCaptor.forClass(IdentityPermissionEntity.class);
        verify(permissionRepository).save(permissionCaptor.capture());
        assertThat(permissionCaptor.getValue().getName()).isEqualTo("read");

        var relationCaptor = ArgumentCaptor.forClass(IdentityUserResourcePermissionEntity.class);
        verify(resourcePermissionRepository).save(relationCaptor.capture());
        assertThat(relationCaptor.getValue())
                .extracting(
                        IdentityUserResourcePermissionEntity::getUserUniqueId,
                        IdentityUserResourcePermissionEntity::getResourceUniqueId,
                        IdentityUserResourcePermissionEntity::getPermissionId
                )
                .containsExactly(11L, 21L, 31L);
    }

    @Test
    void assignResourceAuthorityUses_whenPermissionExists_doesNotDuplicateRelation() {
        var user = user(11L);
        var permission = IdentityPermissionEntity.builder()
                .id(31L)
                .name("read")
                .build();
        var expectedId = IdentityUserResourcePermissionEntityId.builder()
                .userUniqueId(11L)
                .resourceUniqueId(21L)
                .permissionId(31L)
                .build();

        when(resource.getUniqueId()).thenReturn(21L);
        when(permissionRepository.findByName("read")).thenReturn(Optional.of(permission));
        when(resourcePermissionRepository.existsById(expectedId)).thenReturn(true);

        service.assignResourceAuthority(resource, user, " Read ");

        verify(permissionRepository, never()).save(any());
        verify(resourcePermissionRepository, never()).save(any());
    }

    @Test
    void revokeResourceAuthority_whenNoPermissionRegistered_doesNothing() {
        var user = user(11L);

        when(resource.getUniqueId()).thenReturn(21L);
        when(permissionRepository.findByName("read")).thenReturn(Optional.empty());

        service.revokeResourceAuthority(resource, user, " Read ");

        verifyNoInteractions(resourcePermissionRepository);
    }

    @Test
    void revokeResourceAuthority_whenPermissionRegistered_deletesExistingRelation() {
        var user = user(11L);
        var permission = IdentityPermissionEntity.builder()
                .id(31L)
                .name("read")
                .build();
        var expectedId = IdentityUserResourcePermissionEntityId.builder()
                .userUniqueId(11L)
                .resourceUniqueId(21L)
                .permissionId(31L)
                .build();

        when(resource.getUniqueId()).thenReturn(21L);
        when(permissionRepository.findByName("read")).thenReturn(Optional.of(permission));
        when(resourcePermissionRepository.existsById(expectedId)).thenReturn(true);

        service.revokeResourceAuthority(resource, user, " Read ");

        verify(resourcePermissionRepository).deleteById(expectedId);
    }

    @Test
    void findByUserAndResourceType_whenAccountResourceType_usesAccountPermissionQuery() {
        var user = user(11L);
        List<IdentityUserResourcePermission> permissions = List.of();

        when(resourcePermissionRepository.findAccountPermissionsByUserUniqueId(11L)).thenReturn(permissions);

        assertThat(service.findByUserAndResourceType(user, IdentityResourceType.ACCOUNT)).isSameAs(permissions);
    }

    private static IdentityUser user(Long uniqueId) {
        return IdentityUser.builder()
                .uniqueId(new UniqueId(uniqueId))
                .build();
    }
}

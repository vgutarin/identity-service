package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntityId;
import vg.identity.entity.IdentityUserSystemRoleEntity;
import vg.identity.entity.IdentityUserSystemRoleEntityId;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserResourcePermission;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;
import vg.unique.id.jpa.UniqueIdEntity;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityUserAuthorityService {
    private final IdentityUserSystemRoleRepository systemRoleRepository;
    private final IdentityPermissionRepository permissionRepository;
    private final IdentityUserResourcePermissionRepository resourcePermissionRepository;

    public void loadAuthorities(IdentityUser user) {
        user.setAuthorities(
                systemRoleRepository.getAllByIdentityPrincipalUniqueId(
                                user.getUniqueId().getLongValue()
                        ).stream()
                        .map(
                                e -> toRoleAuthority(e.getRole().name())
                        )
                        .toList()
        );
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")//TODO vg go over this an use per
    public void assignAuthority(IdentityUser user, IdentityUserSystemRole role) {
        var id = IdentityUserSystemRoleEntityId.builder()
                .identityPrincipalUniqueId(user.getUniqueId().getLongValue())
                .role(role)
                .build();
        if (systemRoleRepository.existsById(id)) {
            return;
        }

        systemRoleRepository.save(
                IdentityUserSystemRoleEntity.builder()
                        .identityPrincipalUniqueId(id.getIdentityPrincipalUniqueId())
                        .role(id.getRole())
                        .build()
        );
    }

    @Transactional
    public void assignAuthorityTmpInsecure(IdentityUser user, IdentityUserSystemRole role) {
        // is created to temporary bypass the security
        assignAuthority(user, role);
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void assignResourceAuthority(UniqueIdEntity resource, IdentityUser user, String permission) {
        var permissionName = IdentityPermissionService.normalize(permission);
        var permissionId = permissionRepository.findByName(permissionName)
                .orElseGet(() -> permissionRepository.save(
                        IdentityPermissionEntity.builder()
                                .name(permissionName)
                                .build()
                ))
                .getId();

        var id = IdentityUserResourcePermissionEntityId.builder()
                .principalUniqueId(user.getUniqueId().getLongValue())
                .resourceUniqueId(resource.getUniqueId())
                .permissionId(permissionId)
                .build();
        if (!resourcePermissionRepository.existsById(id)) {
            resourcePermissionRepository.save(
                    IdentityUserResourcePermissionEntity.builder()
                            .principalUniqueId(id.getPrincipalUniqueId())
                            .resourceUniqueId(id.getResourceUniqueId())
                            .permissionId(id.getPermissionId())
                            .build()
            );
        }

        log.info(
                "Assigned resource authority: principalUniqueId={}, resourceUniqueId={}, permission={}",
                id.getPrincipalUniqueId(),
                id.getResourceUniqueId(),
                permissionName
        );
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void revokeResourceAuthority(UniqueIdEntity resource, IdentityUser user, String permission) {
        var permissionName = IdentityPermissionService.normalize(permission);
        var permissionEntity = permissionRepository.findByName(permissionName);
        if (permissionEntity.isEmpty()) {
            log.warn(
                    "Cannot revoke resource authority because permission does not exist: principalUniqueId={}, resourceUniqueId={}, permission={}",
                    user.getUniqueId().getLongValue(),
                    resource.getUniqueId(),
                    permissionName
            );
            return;
        }

        var id = IdentityUserResourcePermissionEntityId.builder()
                .principalUniqueId(user.getUniqueId().getLongValue())
                .resourceUniqueId(resource.getUniqueId())
                .permissionId(permissionEntity.get().getId())
                .build();
        if (resourcePermissionRepository.existsById(id)) {
            resourcePermissionRepository.deleteById(id);
        }

        log.info(
                "Revoked resource authority: principalUniqueId={}, resourceUniqueId={}, permission={}",
                id.getPrincipalUniqueId(),
                id.getResourceUniqueId(),
                permissionName
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<IdentityUserResourcePermission> findByUserAndResourceType(IdentityUser user, IdentityResourceType resourceType) {
        return switch (resourceType) {
            case WORKSPACE -> resourcePermissionRepository.findWorkspacePermissionsByPrincipalUniqueId(user.getUniqueId().getLongValue());
        };
    }

    private GrantedAuthority toRoleAuthority(String roleName) {
        return new SimpleGrantedAuthority(
                normalizeRoleName(roleName)
        );
    }

    static String normalizeRoleName(String roleName) {
        roleName = roleName.trim().toUpperCase();
        if (roleName.startsWith("ROLE_")) {
            return roleName;
        }
        return "ROLE_" + roleName;
    }

    //TODO delete
    static String resourceAuthorityName(long uniqueId, String name) {
        return uniqueId + ":" + IdentityPermissionService.normalize(name);
    }
}

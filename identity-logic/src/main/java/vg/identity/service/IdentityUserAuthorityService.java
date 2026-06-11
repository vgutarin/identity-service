package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityUserSystemRoleEntity;
import vg.identity.entity.IdentityUserSystemRoleEntityId;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.repository.IdentityUserSystemRoleRepository;

@Service
@RequiredArgsConstructor
public class IdentityUserAuthorityService {
    private final IdentityUserSystemRoleRepository systemRoleRepository;

    public void loadAuthorities(IdentityUser user) {
        user.setAuthorities(
                systemRoleRepository.getAllByIdentityUserUniqueId(
                                user.getUniqueId().value()
                        ).stream()
                        .map(
                                e -> toRoleAuthority(e.getRole().name())
                        )
                        .toList()
        );
    }

    @Transactional
    @PreAuthorize("hasRole('IDENTITY_ADMIN')")//TODO vg check it works
    public void assignAuthority(IdentityUser user, IdentityUserSystemRole role) {
        var id = IdentityUserSystemRoleEntityId.builder()
                .identityUserUniqueId(user.getUniqueId().value())
                .role(role)
                .build();
        if (systemRoleRepository.existsById(id)) {
            return;
        }

        systemRoleRepository.save(
                IdentityUserSystemRoleEntity.builder()
                        .identityUserUniqueId(id.getIdentityUserUniqueId())
                        .role(id.getRole())
                        .build()
        );
    }

    @Transactional
    public void assignAuthorityTmpInsecure(IdentityUser user, IdentityUserSystemRole role) {
        // is created to temporary bypass the security
        assignAuthority(user, role);
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

    static String normalizeAuthorityName(String authorityName) {
        return authorityName.trim().toLowerCase();
    }

    static String resourceAuthorityName(long uniqueId, String name) {
        return uniqueId + ":" + normalizeAuthorityName(name);
    }
}

package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityRoleMapper;
import vg.identity.model.IdentityRole;
import vg.identity.repository.IdentityRoleRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class IdentityRoleService {
    private final IdentityRoleRepository roleRepository;
    private final IdentityPermissionService permissionService;
    private final IdentityRoleMapper roleMapper;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    IdentityRole create(String name, String description, IdentityWorkspaceEntity workspace) {
        var entity = IdentityRoleEntity.builder()
                .name(name)
                .description(description)
                .build();
        entity.setWorkspace(workspace);

        var saved = roleRepository.save(entity);
        roleRepository.flush();
        return roleMapper.toModel(saved);
    }

    @Transactional
    List<IdentityRole> createFromTemplate(
            Collection<IdentityRoleTemplateEntity> templates,
            IdentityWorkspaceEntity workspace
    ) {
        var roles = templates.stream()
                .map(template -> {
                    var entity = IdentityRoleEntity.builder()
                            .name(template.getName())
                            .description(template.getDescription())
                            .workspace(workspace)
                            .permissions(new HashSet<>(template.getPermissions()))
                            .build();

                    var saved = roleRepository.save(entity);
                    return roleMapper.toModel(saved);
                })
                .toList();

        roleRepository.flush();
        return roles;
    }
    @Transactional(readOnly = true)
    public IdentityRole getById(long id) {
        return roleMapper.toModel(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<IdentityRole> getAll() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toModel)
                .toList();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRole update(IdentityRole role) {
        var id = role.getId();
        var existing = roleRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != role.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityRoleEntity.class, id);
        }

        roleMapper.updateEntity(existing, role);
        existing.getPermissions().clear();
        existing.getPermissions().addAll(resolvePermissions(role.getPermissions()));

        var saved = roleRepository.save(existing);
        roleRepository.flush();
        return roleMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void delete(Long id) {
        var existing = roleRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        roleRepository.delete(existing);
        roleRepository.flush();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRole addPermission(Long id, String permissionName) {
        var existing = roleRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        existing.getPermissions().add(permissionService.getOrCreateEntity(permissionName));
        var saved = roleRepository.save(existing);
        roleRepository.flush();
        return roleMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRole removePermission(Long id, String permissionName) {
        var existing = roleRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        var normalizedPermissionName = IdentityPermissionService.normalize(permissionName);

        existing.getPermissions().removeIf(permission -> normalizedPermissionName.equals(permission.getName()));
        var saved = roleRepository.save(existing);
        roleRepository.flush();
        return roleMapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public IdentityRoleEntity getEntity(long id) {
        return roleRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }

    private Set<IdentityPermissionEntity> resolvePermissions(Set<String> permissions) {
        if (permissions == null) {
            return new HashSet<>();
        }

        return permissions.stream()
                .map(permissionService::getOrCreateEntity)
                .collect(java.util.stream.Collectors.toSet());
    }
}

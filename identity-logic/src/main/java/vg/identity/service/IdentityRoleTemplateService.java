package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.mapper.IdentityRoleTemplateMapper;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.repository.IdentityRoleTemplateRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class IdentityRoleTemplateService {
    private final IdentityRoleTemplateRepository roleTemplateRepository;
    private final IdentityPermissionService permissionService;
    private final IdentityRoleTemplateMapper roleTemplateMapper;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRoleTemplate create(IdentityRoleTemplate template) {
        var entity = roleTemplateMapper.toEntity(template);
        entity.setPermissions(resolvePermissions(template.getPermissions()));

        var saved = roleTemplateRepository.save(entity);
        roleTemplateRepository.flush();
        return roleTemplateMapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public IdentityRoleTemplate getById(long id) {
        return roleTemplateMapper.toModel(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<IdentityRoleTemplate> getAll() {
        return roleTemplateRepository.findAll().stream()
                .map(roleTemplateMapper::toModel)
                .toList();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRoleTemplate update(IdentityRoleTemplate template) {
        var id = template.getId();
        var existing = roleTemplateRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != template.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityRoleTemplateEntity.class, id);
        }

        roleTemplateMapper.updateEntity(existing, template);
        existing.getPermissions().clear();
        existing.getPermissions().addAll(resolvePermissions(template.getPermissions()));

        var saved = roleTemplateRepository.save(existing);
        roleTemplateRepository.flush();
        return roleTemplateMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void delete(Long id) {
        var existing = roleTemplateRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        roleTemplateRepository.delete(existing);
        roleTemplateRepository.flush();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRoleTemplate addPermission(Long id, String permissionName) {
        var existing = roleTemplateRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        existing.getPermissions().add(permissionService.getOrCreateEntity(permissionName));
        var saved = roleTemplateRepository.save(existing);
        roleTemplateRepository.flush();
        return roleTemplateMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityRoleTemplate removePermission(Long id, String permissionName) {
        var existing = roleTemplateRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        var normalizedPermissionName = IdentityPermissionService.normalize(permissionName);

        existing.getPermissions().removeIf(permission -> normalizedPermissionName.equals(permission.getName()));
        var saved = roleTemplateRepository.save(existing);
        roleTemplateRepository.flush();
        return roleTemplateMapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public IdentityRoleTemplateEntity getEntity(long id) {
        return roleTemplateRepository.findById(id)
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

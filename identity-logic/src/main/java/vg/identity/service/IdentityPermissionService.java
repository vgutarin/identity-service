package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.mapper.IdentityPermissionMapper;
import vg.identity.model.IdentityPermission;
import vg.identity.repository.IdentityPermissionRepository;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityPermissionService {
    private final IdentityPermissionRepository permissionRepository;
    private final IdentityPermissionMapper permissionMapper;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityPermission create(IdentityPermission permission) {
        var entity = permissionMapper.toEntity(permission);
        entity.setName(normalize(permission.getName()));

        var saved = permissionRepository.save(entity);
        permissionRepository.flush();
        return permissionMapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public IdentityPermission getById(long id) {
        return permissionMapper.toModel(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<IdentityPermission> getAll() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toModel)
                .toList();
    }

    @Transactional
    IdentityPermissionEntity getOrCreateEntity(String name) {
        var permissionName = normalize(name);
        return permissionRepository.findByName(permissionName)
                .orElseGet(() -> permissionRepository.save(
                        IdentityPermissionEntity.builder()
                                .name(permissionName)
                                .build()
                ));
    }

    @Transactional(readOnly = true)
    IdentityPermissionEntity getEntity(long id) {
        return permissionRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }

    static String normalize(String permissionName) {
        return permissionName.trim().toLowerCase();
    }
}

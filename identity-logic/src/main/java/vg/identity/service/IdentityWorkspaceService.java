package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityWorkspaceMapper;
import vg.identity.model.IdentityWorkspace;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityWorkspaceService {
    private final UniqueIdService uniqueIdService;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityRoleTemplateRepository roleTemplateRepository;
    private final IdentityRoleService roleService;
    private final IdentityWorkspaceMapper workspaceMapper;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityWorkspace create(IdentityWorkspace workspace) {
        var saved = workspaceRepository.saveWithNewUniqueId(
                workspaceMapper.toEntity(workspace),
                uniqueIdService
        );
        workspaceRepository.flush();
        roleService.createFromTemplate(roleTemplateRepository.findAll(), saved);
        return workspaceMapper.toModel(saved);
    }

    @PreAuthorize("@authorityChecker.hasResourceAuthority(#uniqueId, 'read')")
    @Transactional(readOnly = true)
    public IdentityWorkspace getById(long uniqueId) {
        return workspaceMapper.toModel(getEntity(uniqueId));
    }

    @Transactional(readOnly = true)
    public List<IdentityWorkspace> getAll() {
        return workspaceRepository.findAll().stream()
                .map(workspaceMapper::toModel)
                .toList();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityWorkspace update(IdentityWorkspace workspace) {
        var uniqueId = workspace.getUniqueId().value();
        var existing = workspaceRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != workspace.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityWorkspaceEntity.class, uniqueId);
        }

        workspaceMapper.updateEntity(existing, workspace);

        var saved = workspaceRepository.save(existing);
        workspaceRepository.flush();
        return workspaceMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void delete(Long uniqueId) {
        var existing = workspaceRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        workspaceRepository.delete(existing);
        workspaceRepository.flush();
    }

    @Transactional(readOnly = true)
    public IdentityWorkspaceEntity getEntity(long uniqueId) {
        return workspaceRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);
    }

    /**
     * Legacy entity adapter for callers that still require JPA resources.
     */
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityWorkspaceEntity create(IdentityWorkspaceEntity workspace) {
        var saved = create(workspaceMapper.toModel(workspace));
        return getEntity(saved.getUniqueId().value());
    }

    /**
     * Legacy entity adapter for callers that still require JPA resources.
     */
    @PreAuthorize("@authorityChecker.hasResourceAuthority(#uniqueId, 'read')")
    @Transactional(readOnly = true)
    public IdentityWorkspaceEntity get(long uniqueId) {
        return getEntity(uniqueId);
    }

    /**
     * Legacy entity adapter for callers that still require JPA resources.
     */
    @Transactional(readOnly = true)
    public List<IdentityWorkspaceEntity> findAll() {
        return workspaceRepository.findAll();
    }

    /**
     * Legacy entity adapter for callers that still require JPA resources.
     */
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityWorkspaceEntity update(IdentityWorkspaceEntity workspace) {
        var saved = update(workspaceMapper.toModel(workspace));
        return getEntity(saved.getUniqueId().value());
    }
}

package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityWorkspaceService {
    private final UniqueIdService uniqueIdService;
    private final IdentityWorkspaceRepository workspaceRepository;

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public IdentityWorkspaceEntity create(IdentityWorkspaceEntity workspace) {
        var saved = workspaceRepository.saveWithNewUniqueId(workspace, uniqueIdService);
        workspaceRepository.flush();
        return saved;
    }

    @PreAuthorize("@authorityChecker.hasResourceAuthority(#uniqueId, 'read')")
    @Transactional(readOnly = true)
    public IdentityWorkspaceEntity get(long uniqueId) {
        return workspaceRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<IdentityWorkspaceEntity> findAll() {
        return workspaceRepository.findAll();
    }

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public IdentityWorkspaceEntity update(IdentityWorkspaceEntity workspace) {
        var existing = workspaceRepository.findById(workspace.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != workspace.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityWorkspaceEntity.class, workspace.getUniqueId());
        }

        existing.setName(workspace.getName());

        var saved = workspaceRepository.save(existing);
        workspaceRepository.flush();
        return saved;
    }

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public void delete(Long uniqueId) {
        var existing = workspaceRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        workspaceRepository.delete(existing);
        workspaceRepository.flush();
    }
}

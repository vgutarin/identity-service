package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityApplicationMapper;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityApplicationService {
    private final UniqueIdService uniqueIdService;
    private final IdentityApplicationRepository applicationRepository;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityApplicationMapper applicationMapper;
    private final EncryptionService encryptionService;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    IdentityApplication create(String name, String data, IdentityWorkspaceEntity workspace) {
        var principal = createPrincipal(name);
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(principal.getUniqueId())
                .workspace(workspace)
                .name(name)
                .nameHash(encryptionService.canonicalizeAndHash(name))
                .data(data)
                .build();

        var saved = applicationRepository.save(entity);
        applicationRepository.flush();
        return applicationMapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public IdentityApplication getById(long uniqueId) {
        return applicationRepository.findById(uniqueId)
                .map(applicationMapper::toModel)
                .orElseThrow(EntityNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public IdentityApplication findById(long uniqueId) {
        return applicationRepository.findById(uniqueId)
                .map(applicationMapper::toModel)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<IdentityApplication> getAll() {
        return applicationRepository.findAll().stream()
                .map(applicationMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IdentityApplication> findByWorkspaceUniqueId(long workspaceUniqueId) {
        return applicationRepository.findByWorkspaceUniqueId(workspaceUniqueId).stream()
                .map(applicationMapper::toModel)
                .toList();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public IdentityApplication update(IdentityApplication application) {
        var uniqueId = application.getUniqueId().value();
        var existing = applicationRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != application.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityApplicationEntity.class, uniqueId);
        }

        applicationMapper.updateEntity(existing, application);
        existing.setNameHash(encryptionService.canonicalizeAndHash(application.getName()));

        var saved = applicationRepository.save(existing);
        applicationRepository.flush();
        return applicationMapper.toModel(saved);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void delete(Long uniqueId) {
        var existing = applicationRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        applicationRepository.delete(existing);
        applicationRepository.flush();
    }

    private IdentityPrincipalEntity createPrincipal(String name) {
        var principal = IdentityPrincipalEntity.builder()
                .displayName(name)
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.APPLICATION)
                .build();
        return principalRepository.saveWithNewUniqueId(principal, uniqueIdService);
    }

}

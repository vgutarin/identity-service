package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityWorkspaceMapper;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

@RequiredArgsConstructor
@Service
@Validated
public class IdentityWorkspaceService {
    private final UniqueIdService uniqueIdService;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityRoleTemplateRepository roleTemplateRepository;
    private final IdentityRoleService roleService;
    private final IdentityUserService userService;
    private final IdentityWorkspaceMapper workspaceMapper;

    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.Workspace.CREATE + "')")
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

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Workspace.READ + "')")
    @Transactional(readOnly = true)
    public IdentityWorkspace getById(UniqueId uniqueId) {
        return workspaceRepository.findById(uniqueId.getLongValue())
                .map(workspaceMapper::toModel)
                .orElseThrow(EntityNotFoundException::new);
    }


    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.Workspace.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityWorkspace> getAll() {
        return workspaceRepository.findAll().stream()
                .map(workspaceMapper::toModel)
                .toList();
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#workspace.getUniqueId(), '" + Permission.Workspace.UPDATE + "')")
    @Transactional
    public IdentityWorkspace update(IdentityWorkspace workspace) {
        var uniqueId = workspace.getUniqueId().getLongValue();
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

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Workspace.DELETE + "')")
    @Transactional
    public void delete(UniqueId uniqueId) {
        var existing = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        workspaceRepository.delete(existing);
        workspaceRepository.flush();
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Role.CREATE + "')")
    @Transactional
    public IdentityRole createRole(UniqueId uniqueId, IdentityRole role) {
        var workspace = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        return roleService.create(role.getName(), role.getDescription(), workspace);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.CREATE + "')")
    @Transactional
    public IdentityWorkspace addUser(UniqueId uniqueId, @Email String email) {
        var workspace = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        var user = userService.getOrCreateEntityByUsername(email);
        workspace.getUsers().add(user);
        user.getWorkspaces().add(workspace);

        var saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();
        return workspaceMapper.toModel(saved);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityUser> getUsers(UniqueId uniqueId) {
        var workspace = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        return workspace.getUsers().stream()
                .map(userService::toModel)
                .toList();
    }

}

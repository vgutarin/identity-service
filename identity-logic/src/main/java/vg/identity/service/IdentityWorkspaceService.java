package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityWorkspaceMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserChannel;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Validated
public class IdentityWorkspaceService {
    private final UniqueIdService uniqueIdService;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityRoleTemplateRepository roleTemplateRepository;
    private final IdentityRoleService roleService;
    private final IdentityUserService userService;
    private final IdentityUserChannelService channelService;
    private final IdentityActionTokenService actionTokenService;
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

        var channel = channelService.getOrCreatePendingEmailChannel(email);
        if (channel.getIdentityUserUniqueId() == null) {
            userService.findEntityByUsername(email)
                    .ifPresent(user -> channelService.attachUser(channel, user));
        }

        var channelEntity = channelService.getEntityById(channel.getUniqueId());
        var saved = attachChannel(workspace, channelEntity);
        if (!channel.isVerified()) {
            actionTokenService.confirm(channel);
        }
        return workspaceMapper.toModel(saved);
    }

    /**
     * Attaches an existing verified channel to a workspace. Email channels may still be pending; in that case
     * this method requests their confirmation email using the normal cooldown-protected action flow.
     */
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.User.CREATE + "')")
    @Transactional
    public IdentityWorkspace addChannel(UniqueId workspaceUniqueId, UniqueId channelUniqueId) {
        var workspace = workspaceRepository.findById(workspaceUniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);
        var channel = channelService.getEntityById(channelUniqueId);

        if (channel.getChannelType() != IdentityChannelType.EMAIL && channel.getVerifiedAt() == null) {
            throw new IllegalArgumentException("A non-email channel must be verified before it can be attached to a workspace");
        }

        var saved = attachChannel(workspace, channel);
        if (channel.getChannelType() == IdentityChannelType.EMAIL && channel.getVerifiedAt() == null) {
            actionTokenService.confirm(channelService.toEmailModel(channel));
        }
        return workspaceMapper.toModel(saved);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityUser> getUsers(UniqueId uniqueId) {
        var workspace = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        return workspace.getUserChannels().stream()
                .map(IdentityUserChannelEntity::getIdentityUser)
                .filter(Objects::nonNull)
                .distinct()
                .map(userService::toModel)
                .toList();
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityUserChannel> getUserChannels(UniqueId uniqueId) {
        var workspace = workspaceRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        return workspace.getUserChannels().stream()
                .map(channelService::toModel)
                .toList();
    }

    private IdentityWorkspaceEntity attachChannel(
            IdentityWorkspaceEntity workspace,
            IdentityUserChannelEntity channel
    ) {
        workspace.getUserChannels().add(channel);
        var saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();
        return saved;
    }

}

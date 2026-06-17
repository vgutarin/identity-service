package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.UUID;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityUserServiceImpl implements IdentityUserService {
    private static final String GUEST = "guest";

    private final UniqueIdService uniqueIdService;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityUserRepository repository;
    private final IdentityUserChannelRepository identityChannelRepository;
    private final IdentityUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    private IdentityUser guest;

    public IdentityUser findByUsername(String username) {
        return repository.findByUsernameHash(encryptionService.canonicalizeAndHash(username))
                .map(mapper::toModel)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<IdentityUser> findAll() {
        return repository.findAll().stream()
                .map(mapper::toModel)
                .toList();
    }

    @Transactional
    public synchronized IdentityUser getGuest() {
        if (null == guest) {
            guest = create(IdentityUser.builder().username(GUEST).password(GUEST).build());
        }
        return guest;
    }

    @Transactional
    @Override
    //TODO check permissions
    public IdentityUser create(IdentityUser user) {
        if (null != user.getPassword()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        var entity = newEntity(user);
        repository.flush();
        return mapper.toModel(entity);
    }

    @Transactional
    @Override
    //TODO check permissions
    public IdentityUser update(IdentityUser user) {
        var entity = repository.findById(user.getUniqueId()).orElse(null);

        if (null == entity) {
            log.error("Entity was not found by id: {}", user.getUniqueId());
            throw new EntityNotFoundException();
        }

        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }

        mapper.updateEntity(entity, user);
        entity.setUsernameHash(encryptionService.canonicalizeAndHash(user.getUsername()));
        entity = repository.save(entity);
        repository.flush();
        mapper.updateModel(user, entity);
        return user;
    }

    @Transactional
    public IdentityUser get(IdentityChannelType channelType, String channelUserId) {
        var channelUserIdHash = encryptionService.hashCaseSensitive(channelUserId);
        var channelEntity = identityChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash)
                .orElse(null);

        if (channelEntity != null) {
            return mapper.toModel(channelEntity.getIdentityUser());
        }

        var newChannelEntity = IdentityUserChannelEntity.builder()
                .identityUser(
                        newEntity(
                                IdentityUser.builder()
                                        .username("user_" + UUID.randomUUID())
                                        .build()
                        )
                )
                .channelType(channelType)
                .channelUserId(channelUserId)
                .channelUserIdHash(channelUserIdHash)
                .data("{}")
                .build();

        identityChannelRepository.saveWithNewUniqueId(newChannelEntity, uniqueIdService);
        return mapper.toModel(newChannelEntity.getIdentityUser());
    }

    private IdentityUserEntity newEntity(IdentityUser user) {
        var principal = createPrincipal(user);
        var userEntity = mapper.toEntity(user);
        userEntity.setUniqueId(principal.getUniqueId());
        userEntity.setUsernameHash(encryptionService.canonicalizeAndHash(user.getUsername()));
        return repository.save(userEntity);
    }

    private IdentityPrincipalEntity createPrincipal(IdentityUser user) {
        var principal = IdentityPrincipalEntity.builder()
                .displayName(user.getUsername())
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.USER)
                .build();
        return principalRepository.saveWithNewUniqueId(principal, uniqueIdService);
    }
}

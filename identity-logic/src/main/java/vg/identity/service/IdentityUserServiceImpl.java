package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityUserCommunicationChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.CommunicationChannelType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityUserCommunicationChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityUserServiceImpl implements IdentityUserService {
    private static final String GUEST = "guest";

    private final UniqueIdService uniqueIdService;
    private final IdentityUserRepository repository;
    private final IdentityUserCommunicationChannelRepository communicationChannelRepository;
    private final IdentityUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    private IdentityUser guest;

    public IdentityUser findByUsername(String username) {
        return repository.findByUsernameHash(encryptionService.canonicalizeAndHash(username))
                .map(mapper::toModel)
                .orElse(null);
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
    public IdentityUser get(CommunicationChannelType channelType, String channelUserId) {
        var channelUserIdHash = encryptionService.hashCaseSensitive(channelUserId);
        var channelEntity = communicationChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash)
                .orElse(null);

        if (channelEntity != null) {
            return mapper.toModel(channelEntity.getIdentityUser());
        }

        var newChannelEntity = IdentityUserCommunicationChannelEntity.builder()
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

        communicationChannelRepository.saveWithNewUniqueId(newChannelEntity, uniqueIdService);
        return mapper.toModel(newChannelEntity.getIdentityUser());
    }

    private IdentityUserEntity newEntity(IdentityUser user) {
        var userEntity = mapper.toEntity(user);
        userEntity.setUsernameHash(encryptionService.canonicalizeAndHash(user.getUsername()));
        return repository.saveWithNewUniqueId(userEntity, uniqueIdService);
    }
}

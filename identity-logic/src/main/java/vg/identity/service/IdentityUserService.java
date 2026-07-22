package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityUserService implements UserDetailsService {

    private final UniqueIdService uniqueIdService;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityUserRepository repository;
    private final IdentityUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final IdentityUserChannelService channelService;
    private final EmailService emailService;
    private final IdentityUserAuthorityService authorityService;

    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.User.READ + "')")
    public IdentityUser findByUsername(String username) {
        return findEntityByUsername(username)
                .map(mapper::toModel)
                .orElse(null);
    }

    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.User.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityUser> findAll() {
        return repository.findAll().stream()
                .map(mapper::toModel)
                .toList();
    }

    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.User.CREATE + "')")
    @Transactional
    public IdentityUser create(IdentityUser user) {
        if (null != user.getPassword()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return mapper.toModel(newEntity(user));
    }

    @PreAuthorize("@authorityChecker.hasAuthority('" + Permission.User.UPDATE + "')")
    @Transactional
    public IdentityUser update(IdentityUser user) {
        requireColonFreeUsername(user.getUsername());
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
        updatePrincipalName(entity.getPrincipal(), user.getUsername());
        entity = repository.save(entity);
        repository.flush();
        mapper.updateModel(user, entity);
        return user;
    }


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return findEntityByUsername(username)
                .map(mapper::toModel)
                .map(user -> {
                    authorityService.loadAuthorities(user);
                    return user;
                })
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    UniqueId findUniqueIdByUsername(String username) {
        return findEntityByUsername(username)
                .map(mapper::toModel)
                .map(IdentityUser::getUniqueId)
                .orElse(null);
    }

    IdentityUserEntity getOrCreateEntityByUsername(String username) {
        return findEntityByUsername(
                username
        ).orElseGet(() ->
                newEntity(
                        IdentityUser.builder()
                                .username(username)
                                .build()
                )
        );
    }

    IdentityUser toModel(IdentityUserEntity entity) {
        return mapper.toModel(entity);
    }

    private IdentityUserEntity newEntity(IdentityUser user) {
        requireColonFreeUsername(user.getUsername());
        var principal = createPrincipal(user);
        var userEntity = mapper.toEntity(user);
        userEntity.setUniqueId(principal.getUniqueId());
        userEntity = repository.save(userEntity);
        repository.flush();
        // Attach the principal to the persisted instance only for mapping. The association is a
        // read-only shared-PK join (insertable/updatable false); setting it before save would drag it
        // into the merge and trip Hibernate's null-id assertion on the still-transient child.
        userEntity.setPrincipal(principal);
        if (emailService.validateEmail(user.getUsername())) {
            channelService.createEmailChannel(user.getUsername(), userEntity);
        }
        return userEntity;

    }

    private IdentityPrincipalEntity createPrincipal(IdentityUser user) {
        var principal = IdentityPrincipalEntity.builder()
                .name(user.getUsername())
                .nameHash(hashUsername(user.getUsername()))
                .displayName(user.getUsername())
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.USER)
                .build();
        return principalRepository.saveWithNewUniqueId(principal, uniqueIdService);
    }

    private void updatePrincipalName(IdentityPrincipalEntity principal, String username) {
        principal.setName(username);
        principal.setDisplayName(username);
        principal.setNameHash(hashUsername(username));
        principalRepository.save(principal);
    }

    private Optional<IdentityUserEntity> findEntityByUsername(String username) {
        return repository.findByPrincipal_NameHash(hashUsername(username));
    }

    /**
     * Blind-index hash for a user's principal name. The username is always canonicalized (lower-cased +
     * trimmed) before hashing, so user login and uniqueness are case-insensitive. The stored {@code name}
     * keeps its original casing — only the hash key is canonicalized. The same transformation runs on
     * create, update and lookup, so the stored hash and the lookup key always agree.
     */
    private byte[] hashUsername(String username) {
        return encryptionService.hashPrincipalName(encryptionService.canonicalize(username));
    }

    /**
     * Usernames may not contain a colon. Every URI carries a scheme delimiter ({@code :}), and application
     * principals are always stored under a URI name, so a colon-free username can never collide with an
     * application in the shared principal-name index. Enforced on every write; lookups are unaffected.
     */
    private void requireColonFreeUsername(String username) {
        if (username != null && username.indexOf(':') >= 0) {
            throw new IllegalArgumentException("exception.user.username.invalid");
        }
    }

}

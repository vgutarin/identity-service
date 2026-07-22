package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityApplicationMapper;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.access.Permission;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotWithUri;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Service
public class IdentityApplicationService {
    private static final String TELEGRAM_BOT_BASE_URI = "https://t.me/";

    private final UniqueIdService uniqueIdService;
    private final IdentityApplicationRepository applicationRepository;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityApplicationMapper applicationMapper;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;

    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.CREATE + "')")
    @Transactional
    public IdentityApplication createTelegramBotApplication(UniqueId workspaceUniqueId, String name, TelegramBot telegramBot) {
        var workspace = getWorkspaceEntity(workspaceUniqueId);
        var botUsername = telegramService.getUsername(telegramBot);

        return create(name, URI.create(telegramBotUri(botUsername)), toJson(telegramBot), workspace);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')")
    @Transactional
    public IdentityApplication updateTelegramBotApplication(UniqueId applicationUniqueId, int version, String name, TelegramBot telegramBot) {
        var existing = applicationRepository.findById(applicationUniqueId)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != version) {
            throw new ObjectOptimisticLockingFailureException(IdentityApplicationEntity.class, applicationUniqueId);
        }

        var botUsername = telegramService.getUsername(telegramBot);
        var uri = telegramBotUri(botUsername);

        updatePrincipalName(existing.getPrincipal(), name, uri);
        existing.setPayload(toJson(telegramBot));

        var saved = applicationRepository.save(existing);
        applicationRepository.flush();
        return applicationMapper.toModel(saved);
    }


    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')")
    @Transactional(readOnly = true)
    public IdentityApplication getById(UniqueId applicationUniqueId) {
        return applicationRepository.findById(applicationUniqueId.getLongValue())
                .map(applicationMapper::toModel)
                .orElseThrow(EntityNotFoundException::new);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')")
    @Transactional(readOnly = true)
    public IdentityApplication findById(UniqueId applicationUniqueId) {
        return applicationRepository.findById(applicationUniqueId.getLongValue())
                .map(applicationMapper::toModel)
                .orElse(null);
    }

    /**
     * Resolves a Telegram bot application by its bot username, returning both the public URL used to open
     * the bot and the {@link TelegramBot} needed to validate its callback, or {@code null} if no matching
     * application exists.
     * <p>
     * Intentionally unsecured: it runs in the pre-authentication login flow, before any principal exists.
     * Callers rendering the bot link for an authenticated user must enforce their own authorization.
     */
    @Transactional(readOnly = true)
    TelegramBotWithUri findTelegramBotByUsername(String botUsername) {
        var uri = telegramBotUri(botUsername);
        return applicationRepository.findByPrincipal_NameHash(encryptionService.hashPrincipalName(uri))
                .map(entity ->
                        new TelegramBotWithUri(
                                URI.create(entity.getPrincipal().getName()),
                                fromJson(entity.getPayload())
                        )
                )
                .orElse(null);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.READ + "')")
    @Transactional(readOnly = true)
    public List<IdentityApplication> findByWorkspaceUniqueId(UniqueId workspaceUniqueId) {
        return applicationRepository.findByWorkspaceUniqueId(workspaceUniqueId.getLongValue()).stream()
                .map(applicationMapper::toModel)
                .toList();
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#application.getUniqueId(), '" + Permission.App.UPDATE + "')")
    @Transactional
    public IdentityApplication update(IdentityApplication application) {
        var uniqueId = application.getUniqueId();
        var existing = applicationRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != application.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(IdentityApplicationEntity.class, uniqueId);
        }

        applicationMapper.updateEntity(existing, application);
        updatePrincipalName(existing.getPrincipal(), application.getName(), application.getUri());

        var saved = applicationRepository.save(existing);
        applicationRepository.flush();
        return applicationMapper.toModel(saved);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#uniqueId, '" + Permission.App.DELETE + "')")
    @Transactional
    public void delete(UniqueId uniqueId) {
        var existing = applicationRepository.findById(uniqueId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);

        applicationRepository.delete(existing);
        applicationRepository.flush();
    }


    @Transactional
    IdentityApplication create(String name, URI uri, String payload, IdentityWorkspaceEntity workspace) {
        var principal = createPrincipal(name, uri.toString());
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(principal.getUniqueId())
                .workspace(workspace)
                .payload(payload)
                .build();

        var saved = applicationRepository.save(entity);
        applicationRepository.flush();
        // Attach the principal to the persisted instance only for mapping — the shared-PK association is
        // read-only, and setting it before save would pull it into the merge and trip Hibernate's null-id
        // assertion on the still-transient application.
        saved.setPrincipal(principal);
        return applicationMapper.toModel(saved);
    }

    private String telegramBotUri(String botUsername) {
        // Telegram bot usernames are case-insensitive (t.me/MyBot == t.me/mybot), so normalize to
        // lower case before building/hashing the URI. This keeps the stored value and the lookup key
        // consistent and prevents the same bot from being registered twice under different casing.
        return TELEGRAM_BOT_BASE_URI + botUsername.toLowerCase(Locale.ROOT);
    }

    private IdentityWorkspaceEntity getWorkspaceEntity(UniqueId workspaceUniqueId) {
        return workspaceRepository.findById(workspaceUniqueId)
                .orElseThrow(() -> new EntityNotFoundException("exception.workspace.notFound"));
    }

    private String toJson(TelegramBot telegramBot) {
        try {
            return objectMapper.writeValueAsString(telegramBot);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize Telegram bot", e);
        }
    }

    private TelegramBot fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, TelegramBot.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot deserialize Telegram bot", e);
        }
    }

    private IdentityPrincipalEntity createPrincipal(String displayName, String uri) {
        requireUriName(uri);
        var principal = IdentityPrincipalEntity.builder()
                .displayName(displayName)
                .name(uri)
                .nameHash(encryptionService.hashPrincipalName(uri))
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.APPLICATION)
                .build();
        return principalRepository.saveWithNewUniqueId(principal, uniqueIdService);
    }

    private void updatePrincipalName(IdentityPrincipalEntity principal, String displayName, String uri) {
        requireUriName(uri);
        principal.setDisplayName(displayName);
        principal.setName(uri);
        principal.setNameHash(encryptionService.hashPrincipalName(uri));
        principalRepository.save(principal);
    }

    /**
     * An application principal is always stored under an absolute URI name (it carries a scheme, hence a
     * colon). Usernames are forbidden from containing a colon, so this keeps the two principal types in
     * disjoint regions of the shared principal-name index and guarantees they can never collide.
     */
    private void requireUriName(String uri) {
        if (uri == null || !URI.create(uri).isAbsolute()) {
            throw new IllegalArgumentException("exception.application.name.mustBeUri");
        }
    }

}

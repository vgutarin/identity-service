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
import vg.identity.model.principal.IdentityInternalPrincipalStatus;
import vg.identity.model.principal.IdentityInternalPrincipalType;
import vg.identity.model.access.Permission;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityApplicationService {
    private final UniqueIdService uniqueIdService;
    private final IdentityApplicationRepository applicationRepository;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityApplicationMapper applicationMapper;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TelegramWebhookRegistrationService telegramWebhookRegistrationService;
    private final TelegramBotLookupService telegramBotLookupService;
    private final AuthorityChecker authorityChecker;

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')")
    public TelegramWebhookStatus getTelegramWebhookStatus(UniqueId applicationUniqueId) {
        var status = telegramWebhookRegistrationService.inspect(applicationUniqueId);
        return status.toBuilder().toggleAllowed(status.toggleAllowed()
                && authorityChecker.hasAuthority(applicationUniqueId, Permission.App.UPDATE)).build();
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')")
    public TelegramWebhookStatus setTelegramWebhookEnabled(UniqueId applicationUniqueId, int expectedVersion, boolean enabled) {
        return telegramWebhookRegistrationService.setEnabled(applicationUniqueId, expectedVersion, enabled);
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.CREATE + "')")
    @Transactional
    public IdentityApplication createTelegramBotApplication(UniqueId workspaceUniqueId, String name, TelegramBot telegramBot) {
        var workspace = getWorkspaceEntity(workspaceUniqueId);
        var botUser = telegramService.getBotUser(telegramBot);
        var validatedBot = telegramBot.withTelegramBotId(botUser.id());

        var application = create(name, TelegramUriBuilder.botUri(botUser.username()), toJson(validatedBot), workspace);
        telegramWebhookRegistrationService.scheduleRegistration(application.getUniqueId());
        return application;
    }

    @PreAuthorize("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')")
    @Transactional
    public IdentityApplication updateTelegramBotApplication(UniqueId applicationUniqueId, int version, String name, TelegramBot telegramBot) {
        var existing = applicationRepository.findById(applicationUniqueId)
                .orElseThrow(EntityNotFoundException::new);

        if (existing.getVersion() != version) {
            throw new ObjectOptimisticLockingFailureException(IdentityApplicationEntity.class, applicationUniqueId);
        }

        var botUser = telegramService.getBotUser(telegramBot);
        var validatedBot = telegramBot.withTelegramBotId(botUser.id());
        var previousBot = telegramBotLookupService.readBot(existing);
        var uri = TelegramUriBuilder.botUri(botUser.username()).toString();
        var payload = toJson(validatedBot);

        telegramWebhookRegistrationService.removePreviousBot(previousBot, validatedBot);

        updatePrincipalName(existing.getPrincipal(), name, uri);
        existing.setPayload(payload);

        var saved = applicationRepository.save(existing);
        applicationRepository.flush();
        var application = applicationMapper.toModel(saved);
        telegramWebhookRegistrationService.scheduleRegistration(applicationUniqueId);
        return application;
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
     * Returns the application represented by the authenticated application principal.
     * This does not require a separately assigned {@code app.read} permission.
     */
    @PreAuthorize("@authorityChecker.isAuthenticatedApplication(#applicationUniqueId)")
    @Transactional(readOnly = true)
    public IdentityApplication getAuthenticatedApplication(UniqueId applicationUniqueId) {
        return applicationRepository.findById(applicationUniqueId.getLongValue())
                .map(applicationMapper::toModel)
                .orElseThrow(EntityNotFoundException::new);
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

        telegramWebhookRegistrationService.remove(telegramBotLookupService.readBot(existing));
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

    private IdentityPrincipalEntity createPrincipal(String displayName, String uri) {
        requireUriName(uri);
        var principal = IdentityPrincipalEntity.builder()
                .displayName(displayName)
                .name(uri)
                .nameHash(encryptionService.hashPrincipalName(uri))
                .status(IdentityInternalPrincipalStatus.ACTIVE)
                .type(IdentityInternalPrincipalType.APPLICATION)
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

package vg.identity.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.IdentityActionTokenProperties;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.model.IdentityAction;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.application.TelegramBotToConfirm;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityActionTokenRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class IdentityActionTokenService {

    private final IdentityActionTokenRepository actionTokenRepository;
    private final IdentityPrincipalRepository principalRepository;
    private final IdentityUserChannelRepository channelRepository;
    private final IdentityCommandService commandService;
    private final IdentityActionTokenProperties properties;
    private final IdentityActionLinkBuilder actionLinkBuilder;
    private final IdentityApplicationService applicationService;
    private final ConfirmEmailMailFactory confirmEmailMailFactory;
    private final ObjectMapper objectMapper;
    private final String telegramBotName;
    private final Clock clock;

    public IdentityActionTokenService(
            IdentityActionTokenRepository actionTokenRepository,
            IdentityPrincipalRepository principalRepository,
            IdentityUserChannelRepository channelRepository,
            IdentityCommandService commandService,
            IdentityActionTokenProperties properties,
            IdentityActionLinkBuilder actionLinkBuilder,
            IdentityApplicationService applicationService,
            ConfirmEmailMailFactory confirmEmailMailFactory,
            ObjectMapper objectMapper,
            @Value("${identity.telegram.bot.name:}") String telegramBotName,
            Clock clock
    ) {
        this.actionTokenRepository = actionTokenRepository;
        this.principalRepository = principalRepository;
        this.channelRepository = channelRepository;
        this.commandService = commandService;
        this.properties = properties;
        this.actionLinkBuilder = actionLinkBuilder;
        this.applicationService = applicationService;
        this.confirmEmailMailFactory = confirmEmailMailFactory;
        this.objectMapper = objectMapper;
        this.telegramBotName = telegramBotName;
        this.clock = clock;
    }

    @Transactional
    public void confirm(@NotNull IdentityUserChannelEmail channel) {
        Objects.requireNonNull(channel.getUniqueId(), "channel uniqueId is required");
        Objects.requireNonNull(channel.getIdentityUserUniqueId(), "channel identityUserUniqueId is required");
        Objects.requireNonNull(channel.getEmail(), "channel email is required");

        var channelUniqueId = channel.getUniqueId().getLongValue();
        var principalUniqueId = channel.getIdentityUserUniqueId().getLongValue();
        var createdAt = clock.instant();
        if (actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL,
                channelUniqueId,
                createdAt.minus(properties.getRequestCooldown())
        )) {
            return;
        }

        var id = UUID.randomUUID();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .principalType(IdentityPrincipalType.USER)
                .principal(principalRepository.getReferenceById(principalUniqueId))
                .identityUserChannel(channelRepository.getReferenceById(channelUniqueId))
                .createdAt(createdAt)
                .expireAt(createdAt.plus(properties.getExpiresIn()))
                .build();

        actionTokenRepository.save(verification);
        commandService.enqueue(
                confirmEmailMailFactory.create(
                        channel.getEmail(),
                        actionLinkBuilder.confirmationEmailUri(id),
                        telegramConfirmUri(id)
                )
        );
    }

    /**
     * Builds the preferred Telegram confirmation link for a {@code CONFIRM_EMAIL} action: the configured bot's
     * URL with the action id as the {@code startapp} parameter, so opening it runs the Telegram login flow.
     *
     * @return the link, or {@code null} when no bot is configured or the configured bot is not registered.
     */
    private URI telegramConfirmUri(UUID actionId) {
        if (!StringUtils.hasText(telegramBotName)) {
            return null;
        }

        var telegramBot = applicationService.findTelegramBotByUsername(telegramBotName);
        if (telegramBot == null) {
            return null;
        }

        return telegramDeepLink(telegramBot.uri(), actionId);
    }

    /**
     * Builds the Telegram deep link for an action: the bot URL carrying the action id in the
     * {@code startapp} parameter, e.g. {@code https://t.me/<bot>?startapp=<actionId>}. Kept internal to the
     * logic module, as it does not depend on the service's external public origin.
     */
    private URI telegramDeepLink(URI telegramBotUri, UUID actionId) {
        return UriComponentsBuilder.fromUri(telegramBotUri)
                .queryParam(properties.getTelegramStartAppParam(), actionId)
                .build()
                .toUri();
    }

    public IdentityAction.ConfirmEmailInfo findConfirmEmailActionInfo(@NotNull UUID id) {
        return findConfirmEmailActionTokenEntity(id)
                .map(e ->
                        IdentityAction.ConfirmEmailInfo.builder()
                                .id(e.getId())
                                .userUniqueId(identityUserUniqueId(e))
                                .personalInformationConsentGiven(
                                        isPersonalInformationConsentGiven(e)
                                )
                                .build()
                ).orElse(null);
    }

    public IdentityAction.BindTelegramInfo findBindTelegramActionInfo(@NotNull UUID id) {
        return findActionTokenEntity(id)
                .filter(e -> e.getActionType() == IdentityActionType.BIND_TELEGRAM)
                .filter(e -> e.getPrincipalType() == IdentityPrincipalType.USER)
                .filter(e -> e.getPrincipal() != null)
                .map(e -> {
                    var payload = fromPayload(e.getPayload());
                    if (payload == null) {
                        return null;
                    }
                    var telegramBot = applicationService.findTelegramBotByUsername(payload.botUsername());
                    if (telegramBot == null) {
                        return null;
                    }
                    return new IdentityAction.BindTelegramInfo(e.getId(), telegramBot.bot(), e.getPrincipal());
                })
                .orElse(null);
    }

    public void consumeBindTelegramAction(@NotNull UUID id) {
        actionTokenRepository.deleteById(id);
    }

    @Transactional
    public ConfirmationResult confirmEmail(@NotNull UUID id) {//TODO VG may be deleted. Telegram binding could be done using personal page
        return findConfirmEmailActionTokenEntity(id)
                .map(verification -> {
                    var channel = verification.getIdentityUserChannel();
                    var now = clock.instant();
                    channel.setVerifiedAt(now);
                    var principal = channel.getIdentityUser();
                    if (null != principal && null == principal.getConsentToKeepPersonalDataAt()) {
                        principal.setConsentToKeepPersonalDataAt(now);
                    }
                    channelRepository.save(channel);
                    channelRepository.flush();
                    actionTokenRepository.deleteById(id);
                    return new ConfirmationResult(true, createBindTelegramUrlIfTelegramIsMissing(verification));
                }).orElseGet(() -> new ConfirmationResult(false, null));
    }

    public record ConfirmationResult(boolean success, URI bindTelegramUrl) {
    }

    /**
     * Confirms the email channel behind a {@code CONFIRM_EMAIL} action within the Telegram login flow:
     * marks the channel verified, grants personal-data consent to the owning user if it is still missing and
     * consumes the action. Unlike {@link #confirmEmail(UUID)} it does not create a follow-up
     * {@code BIND_TELEGRAM} action, because the Telegram channel is bound directly by the caller.
     *
     * @return the unique id of the user the confirmed channel belongs to, or {@code null} when the action is
     * missing, expired, of another type or not attached to a user.
     */
    @Transactional
    public UniqueId confirmEmailChannel(@NotNull UUID id) {
        return findConfirmEmailActionTokenEntity(id)
                .map(verification -> {
                    var channel = verification.getIdentityUserChannel();
                    var now = clock.instant();
                    channel.setVerifiedAt(now);
                    var user = channel.getIdentityUser();
                    if (user == null) {
                        return null;
                    }
                    if (null == user.getConsentToKeepPersonalDataAt()) {
                        user.setConsentToKeepPersonalDataAt(now);
                    }
                    channelRepository.save(channel);
                    channelRepository.flush();
                    actionTokenRepository.deleteById(id);
                    return new UniqueId(user.getUniqueId());
                })
                .orElse(null);
    }

    private Optional<IdentityActionTokenEntity> findActionTokenEntity(@NotNull UUID id) {
        return actionTokenRepository.findById(id)
                .filter(e -> e.getExpireAt().isAfter(clock.instant()));
    }

    private Optional<IdentityActionTokenEntity> findConfirmEmailActionTokenEntity(@NotNull UUID id) {
        return findActionTokenEntity(id)
                .filter(e -> e.getActionType() == IdentityActionType.CONFIRM_EMAIL);
    }

    private UniqueId identityUserUniqueId(IdentityActionTokenEntity entity) {
        if (null == entity.getIdentityUserChannel() || null == entity.getIdentityUserChannel().getIdentityUser()) {
            return null;
        }
        return new UniqueId(entity.getIdentityUserChannel().getIdentityUser().getUniqueId());
    }

    private boolean isPersonalInformationConsentGiven(IdentityActionTokenEntity entity) {
        return null != entity.getIdentityUserChannel()
                && null != entity.getIdentityUserChannel().getIdentityUser()
                && null != entity.getIdentityUserChannel().getIdentityUser().getConsentToKeepPersonalDataAt();
    }

    private URI createBindTelegramUrlIfTelegramIsMissing(IdentityActionTokenEntity entity) {
        var principal = entity.getPrincipal();
        if (principal == null || !StringUtils.hasText(telegramBotName)) {
            return null;
        }

        var telegramBot = applicationService.findTelegramBotByUsername(telegramBotName);
        if (telegramBot == null) {
            return null;
        }

        var telegramUserChannelExists = channelRepository.existsByIdentityUserUniqueIdAndChannelType(
                principal.getUniqueId(),
                IdentityChannelType.TELEGRAM_USER
        );
        if (telegramUserChannelExists) {
            return null;
        }

        var actionId = UUID.randomUUID();
        var createdAt = clock.instant();
        actionTokenRepository.save(
                IdentityActionTokenEntity.builder()
                        .id(actionId)
                        .actionType(IdentityActionType.BIND_TELEGRAM)
                        .principalType(entity.getPrincipalType())
                        .principal(principal)
                        .payload(toPayload(new TelegramBotToConfirm(telegramBotName)))
                        .createdAt(createdAt)
                        .expireAt(createdAt.plus(properties.getExpiresIn()))
                        .build()
        );
        return telegramDeepLink(telegramBot.uri(), actionId);
    }

    private String toPayload(TelegramBotToConfirm payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize Telegram bot confirmation payload", e);
        }
    }

    private TelegramBotToConfirm fromPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }

        try {
            return objectMapper.readValue(payload, TelegramBotToConfirm.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}

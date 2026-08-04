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
import vg.identity.entity.IdentityUserEntity;
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

/**
 * Issues and inspects short-lived identity action tokens.
 * <p>
 * An action token represents a requested user interaction, such as confirming an email address or binding a
 * Telegram account. This service owns the token lifecycle: it persists tokens, applies expiration and request
 * cooldown rules, builds the user-facing links, and queues the corresponding notification.
 * <p>
 * {@link IdentityActionTokenProcessorService} is this service's execution counterpart. It obtains locked,
 * eligible tokens and applies their domain effects, while this service remains the single owner of token lookup
 * and consumption persistence.
 */
@Service
@Validated
public class IdentityActionTokenService {

    private static final char ACTION_KEY_SEPARATOR = '_';

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
        Objects.requireNonNull(channel.getEmail(), "channel email is required");

        var channelUniqueId = channel.getUniqueId().getLongValue();
        var createdAt = clock.instant();
        if (actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL,
                channelUniqueId,
                createdAt.minus(properties.getRequestCooldown())
        )) {
            return;
        }

        var rawSecret = OpaqueKey.newSecret();
        var verification = IdentityActionTokenEntity.builder()
                .secretHash(OpaqueKey.sha256(rawSecret))
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelRepository.getReferenceById(channelUniqueId))
                .createdAt(createdAt)
                .expireAt(createdAt.plus(properties.getExpiresIn()))
                .build();
        if (channel.getIdentityUserUniqueId() != null) {
            verification.setPrincipalType(IdentityPrincipalType.USER);
            verification.setPrincipal(
                    principalRepository.getReferenceById(channel.getIdentityUserUniqueId().getLongValue())
            );
        }

        var savedVerification = actionTokenRepository.save(verification);
        var actionKey = formatActionKey(savedVerification.getId(), rawSecret);
        commandService.enqueue(
                confirmEmailMailFactory.create(
                        channel.getEmail(),
                        actionLinkBuilder.confirmationEmailUri(actionKey),
                        telegramConfirmUri(actionKey)
                )
        );
    }

    /**
     * Builds the preferred Telegram confirmation link for a {@code CONFIRM_EMAIL} action: the configured bot's
     * URL with the action key as the {@code startapp} parameter, so opening it runs the Telegram login flow.
     *
     * @return the link, or {@code null} when no bot is configured or the configured bot is not registered.
     */
    private URI telegramConfirmUri(String actionKey) {
        if (!StringUtils.hasText(telegramBotName)) {
            return null;
        }

        var telegramBot = applicationService.findTelegramBotByUsername(telegramBotName);
        if (telegramBot == null) {
            return null;
        }

        return telegramDeepLink(telegramBot.uri(), actionKey);
    }

    /**
     * Builds the Telegram deep link for an action: the bot URL carrying the action key in the
     * {@code startapp} parameter, e.g. {@code https://t.me/<bot>?startapp=<actionKey>}. Kept internal to the
     * logic module, as it does not depend on the service's external public origin.
     */
    private URI telegramDeepLink(URI telegramBotUri, String actionKey) {
        return UriComponentsBuilder.fromUri(telegramBotUri)
                .queryParam(properties.getTelegramStartAppParam(), actionKey)
                .build()
                .toUri();
    }

    public IdentityAction.ConfirmEmailInfo findConfirmEmailActionInfo(@NotNull String actionKey) {
        return findConfirmEmailActionTokenEntity(actionKey)
                .map(e ->
                        IdentityAction.ConfirmEmailInfo.builder()
                                .actionKey(actionKey)
                                .userUniqueId(identityUserUniqueId(e))
                                .suggestedDisplayName(channelUserId(e))
                                .personalInformationConsentGiven(
                                        isPersonalInformationConsentGiven(e)
                                )
                                .build()
                ).orElse(null);
    }

    public IdentityAction.BindTelegramInfo findBindTelegramActionInfo(@NotNull String actionKey) {
        return findActionTokenEntity(actionKey)
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

    private Optional<IdentityActionTokenEntity> findActionTokenEntity(@NotNull String actionKey) {
        return parseActionKey(actionKey)
                .flatMap(candidate -> actionTokenRepository.findById(candidate.id())
                        .filter(e -> OpaqueKey.secretMatches(e.getSecretHash(), candidate.secret()))
                        .filter(e -> e.getExpireAt().isAfter(clock.instant())));
    }

    private Optional<IdentityActionTokenEntity> findConfirmEmailActionTokenEntity(@NotNull String actionKey) {
        return findActionTokenEntity(actionKey)
                .filter(e -> e.getActionType() == IdentityActionType.CONFIRM_EMAIL);
    }

    IdentityActionTokenEntity findConfirmEmailActionForUpdate(@NotNull String actionKey) {
        return parseActionKey(actionKey)
                .flatMap(candidate -> actionTokenRepository.findByIdForUpdate(candidate.id())
                        .filter(action -> OpaqueKey.secretMatches(action.getSecretHash(), candidate.secret()))
                        .filter(action -> action.getActionType() == IdentityActionType.CONFIRM_EMAIL)
                        .filter(action -> action.getExpireAt().isAfter(clock.instant()))
                        .filter(action -> action.getIdentityUserChannel() != null)
                        .filter(action -> action.getIdentityUserChannel().getChannelType() == IdentityChannelType.EMAIL))
                .orElse(null);
    }

    void consumeAction(@NotNull Long id) {
        actionTokenRepository.deleteById(id);
    }

    private UniqueId identityUserUniqueId(IdentityActionTokenEntity entity) {
        if (null == entity.getIdentityUserChannel() || null == entity.getIdentityUserChannel().getIdentityUser()) {
            return null;
        }
        return new UniqueId(entity.getIdentityUserChannel().getIdentityUser().getUniqueId());
    }

    private String channelUserId(IdentityActionTokenEntity entity) {
        return null == entity.getIdentityUserChannel() ? null : entity.getIdentityUserChannel().getChannelUserId();
    }

    private boolean isPersonalInformationConsentGiven(IdentityActionTokenEntity entity) {
        return null != entity.getIdentityUserChannel()
                && null != entity.getIdentityUserChannel().getIdentityUser()
                && null != entity.getIdentityUserChannel().getIdentityUser().getConsentToKeepPersonalDataAt();
    }

    URI createBindTelegramUrlIfTelegramIsMissing(IdentityUserEntity user) {
        if (!StringUtils.hasText(telegramBotName)) {
            return null;
        }

        var telegramBot = applicationService.findTelegramBotByUsername(telegramBotName);
        if (telegramBot == null) {
            return null;
        }

        var telegramUserChannelExists = channelRepository.existsByIdentityUserUniqueIdAndChannelType(
                user.getUniqueId(),
                IdentityChannelType.TELEGRAM_USER
        );
        if (telegramUserChannelExists) {
            return null;
        }

        var principal = user.getPrincipal();
        if (principal == null) {
            principal = principalRepository.getReferenceById(user.getUniqueId());
        }
        var rawSecret = OpaqueKey.newSecret();
        var createdAt = clock.instant();
        var action = actionTokenRepository.save(
                IdentityActionTokenEntity.builder()
                        .secretHash(OpaqueKey.sha256(rawSecret))
                        .actionType(IdentityActionType.BIND_TELEGRAM)
                        .principalType(IdentityPrincipalType.USER)
                        .principal(principal)
                        .payload(toPayload(new TelegramBotToConfirm(telegramBotName)))
                        .createdAt(createdAt)
                        .expireAt(createdAt.plus(properties.getExpiresIn()))
                        .build()
        );
        return telegramDeepLink(telegramBot.uri(), formatActionKey(action.getId(), rawSecret));
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

    private String formatActionKey(Long id, byte[] rawSecret) {
        return OpaqueKey.format(String.valueOf(id), ACTION_KEY_SEPARATOR, rawSecret);
    }

    private Optional<ActionKey> parseActionKey(String value) {
        return OpaqueKey.parse(value, ACTION_KEY_SEPARATOR).flatMap(parsed -> {
            try {
                var id = Long.parseLong(parsed.id());
                return id > 0 ? Optional.of(new ActionKey(id, parsed.secret())) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private record ActionKey(long id, byte[] secret) {
    }
}

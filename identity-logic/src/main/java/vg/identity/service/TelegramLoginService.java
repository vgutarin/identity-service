package vg.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityAction;
import vg.identity.model.IdentityUser;
import vg.identity.model.application.TelegramBot;
import vg.identity.repository.IdentityUserRepository;

import java.time.Clock;
import java.util.UUID;

/**
 * Drives the preliminary Telegram authentication flow used by the {@code verify/telegram} mini-app view.
 * <p>
 * Two entry paths are supported, distinguished by the presence of a Telegram {@code start_param} that carries
 * an {@link IdentityAction} id:
 * <ul>
 *     <li><b>Action based</b> — the {@code start_param} references a {@code CONFIRM_EMAIL} or
 *     {@code BIND_TELEGRAM} action. Personal-data consent is enforced first; a {@code CONFIRM_EMAIL} action
 *     both verifies the email and binds the Telegram channel, a {@code BIND_TELEGRAM} action only binds it.
 *     On success the action is consumed and the owning user is returned for authentication.</li>
 *     <li><b>Plain login</b> — no {@code start_param}. The Telegram identity is validated with the bot
 *     configured via {@code identity.telegram.bot.name}; if it is already bound to a user that user is
 *     returned for authentication, otherwise a greeting is returned.</li>
 * </ul>
 * All error paths return {@link Result#failed()} so the caller can render a single neutral message.
 */
@Slf4j
@Service
public class TelegramLoginService {

    private final IdentityActionTokenService actionTokenService;
    private final TelegramAuthenticationService telegramAuthenticationService;
    private final IdentityApplicationService applicationService;
    private final IdentityUserChannelService channelService;
    private final IdentityUserService userService;
    private final IdentityUserAuthorityService authorityService;
    private final IdentityUserRepository userRepository;
    private final String telegramBotName;
    private final Clock clock;

    public TelegramLoginService(
            IdentityActionTokenService actionTokenService,
            TelegramAuthenticationService telegramAuthenticationService,
            IdentityApplicationService applicationService,
            IdentityUserChannelService channelService,
            IdentityUserService userService,
            IdentityUserAuthorityService authorityService,
            IdentityUserRepository userRepository,
            @Value("${identity.telegram.bot.name:}") String telegramBotName,
            Clock clock
    ) {
        this.actionTokenService = actionTokenService;
        this.telegramAuthenticationService = telegramAuthenticationService;
        this.applicationService = applicationService;
        this.channelService = channelService;
        this.userService = userService;
        this.authorityService = authorityService;
        this.userRepository = userRepository;
        this.telegramBotName = telegramBotName;
        this.clock = clock;
    }

    /**
     * Runs the Telegram login flow for the given Telegram Mini App {@code initData}.
     *
     * @param initData        the raw {@code Telegram.WebApp.initData} string
     * @param consentGranted  {@code true} when the user has just accepted the personal-data consent in the UI;
     *                        it lets a flow that previously returned {@link Result.Outcome#CONSENT_REQUIRED}
     *                        proceed
     */
    @Transactional
    public Result login(String initData, boolean consentGranted) {
        var actionId = findActionId(initData);
        if (actionId != null) {
            return handleAction(actionId, initData, consentGranted);
        }
        return handleGreetingOrLogin(initData);
    }

    private Result handleAction(UUID actionId, String initData, boolean consentGranted) {
        var confirmEmailInfo = actionTokenService.findConfirmEmailActionInfo(actionId);
        if (confirmEmailInfo != null) {
            return handleConfirmEmail(confirmEmailInfo, initData, consentGranted);
        }

        var bindInfo = actionTokenService.findBindTelegramActionInfo(actionId);
        if (bindInfo != null) {
            return handleBindTelegram(bindInfo, initData, consentGranted);
        }

        log.warn("No Telegram action found by id {}", actionId);
        return Result.failed();
    }

    private Result handleConfirmEmail(IdentityAction.ConfirmEmailInfo info, String initData, boolean consentGranted) {
        if (!info.personalInformationConsentGiven() && !consentGranted) {
            return Result.consentRequired();
        }

        var bot = configuredBot();
        if (bot == null) {
            return Result.failed();
        }

        var telegramUser = telegramAuthenticationService.parseUser(bot, initData).orElse(null);
        if (telegramUser == null) {
            log.warn("Telegram init data is invalid for confirm-email action {}", info.id());
            return Result.failed();
        }

        if (info.userUniqueId() == null) {
            log.warn("Confirm-email action {} is not attached to a user", info.id());
            return Result.failed();
        }

        var user = userRepository.findById(info.userUniqueId()).orElse(null);
        if (user == null) {
            log.warn("User {} is not found for confirm-email action {}", info.userUniqueId(), info.id());
            return Result.failed();
        }

        // Bind first: on conflict bindTelegramUser makes no changes, so the email stays unconfirmed and the
        // action reusable. Only once the channel is safely bound do we verify the email and consume the action.
        var bindResult = channelService.bindTelegramUser(telegramUser, user);
        if (bindResult != IdentityUserChannelService.TelegramBindResult.SUCCESS) {
            log.warn("Telegram bind failed with {} for confirm-email action {}", bindResult, info.id());
            return Result.failed();
        }

        if (actionTokenService.confirmEmailChannel(info.id()) == null) {
            throw new IllegalStateException("Cannot confirm email for action " + info.id() + " after Telegram bind");
        }

        return Result.authenticated(toAuthenticatedUser(user));
    }

    private Result handleBindTelegram(IdentityAction.BindTelegramInfo info, String initData, boolean consentGranted) {
        var user = userRepository.findById(info.principal().getUniqueId()).orElse(null);
        if (user == null) {
            log.warn("User {} is not found for bind-telegram action {}", info.principal().getUniqueId(), info.id());
            return Result.failed();
        }

        if (user.getConsentToKeepPersonalDataAt() == null && !consentGranted) {
            return Result.consentRequired();
        }

        var telegramUser = telegramAuthenticationService.parseUser(info.telegramBot(), initData).orElse(null);
        if (telegramUser == null) {
            log.warn("Telegram init data is invalid for bind-telegram action {}", info.id());
            return Result.failed();
        }

        var bindResult = channelService.bindTelegramUser(telegramUser, user);
        if (bindResult != IdentityUserChannelService.TelegramBindResult.SUCCESS) {
            log.warn("Telegram bind failed with {} for bind-telegram action {}", bindResult, info.id());
            return Result.failed();
        }

        if (user.getConsentToKeepPersonalDataAt() == null) {
            user.setConsentToKeepPersonalDataAt(clock.instant());
            userRepository.save(user);
        }
        actionTokenService.consumeBindTelegramAction(info.id());

        return Result.authenticated(toAuthenticatedUser(user));
    }

    private Result handleGreetingOrLogin(String initData) {
        var bot = configuredBot();
        if (bot == null) {
            return Result.failed();
        }

        var telegramUser = telegramAuthenticationService.parseUser(bot, initData).orElse(null);
        if (telegramUser == null) {
            log.warn("Telegram init data is invalid for the configured bot");
            return Result.failed();
        }

        var user = channelService.findUserByTelegramId(telegramUser.id());
        if (user != null) {
            return Result.authenticated(toAuthenticatedUser(user));
        }

        return Result.greeting(telegramUser.displayName());
    }

    private TelegramBot configuredBot() {
        if (!StringUtils.hasText(telegramBotName)) {
            log.warn("Telegram bot name is not configured (identity.telegram.bot.name)");
            return null;
        }

        var bot = applicationService.findTelegramBotByUsername(telegramBotName);
        if (bot == null) {
            log.warn("Telegram bot application is not found by configured name {}", telegramBotName);
            return null;
        }
        return bot.bot();
    }

    private IdentityUser toAuthenticatedUser(IdentityUserEntity entity) {
        var user = userService.toModel(entity);
        authorityService.loadAuthorities(user);
        return user;
    }

    private UUID findActionId(String initData) {
        try {
            var startParam = telegramAuthenticationService.findStartParam(initData);
            return startParam == null ? null : UUID.fromString(startParam);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record Result(Outcome outcome, IdentityUser user, String greetingName) {

        public enum Outcome {
            AUTHENTICATED,
            GREETING,
            CONSENT_REQUIRED,
            FAILED
        }

        static Result authenticated(IdentityUser user) {
            return new Result(Outcome.AUTHENTICATED, user, null);
        }

        static Result greeting(String greetingName) {
            return new Result(Outcome.GREETING, null, greetingName);
        }

        static Result consentRequired() {
            return new Result(Outcome.CONSENT_REQUIRED, null, null);
        }

        static Result failed() {
            return new Result(Outcome.FAILED, null, null);
        }
    }
}

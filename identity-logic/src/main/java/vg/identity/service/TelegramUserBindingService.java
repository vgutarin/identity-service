package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.repository.IdentityUserRepository;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class TelegramUserBindingService {

    private final IdentityActionTokenService actionTokenService;
    private final TelegramAuthenticationService telegramAuthenticationService;
    private final IdentityUserChannelService channelService;
    private final IdentityUserRepository userRepository;

    @Transactional
    public Result bind(String initData) {
        var actionId = findActionId(initData);
        if (actionId == null) {
            log.error("Action id is not found");
            return failed();
        }

        var actionInfo = actionTokenService.findBindTelegramActionInfo(actionId);
        if (actionInfo == null) {
            log.error("Action is not found by id {}", actionId);
            return failed();
        }

        var telegramUser = telegramAuthenticationService.parseUser(actionInfo.telegramBot(), initData)
                .orElse(null);
        if (telegramUser == null) {
            log.error("Telegram user is not found");
            return failed();
        }

        var user = userRepository.findById(actionInfo.principal().getUniqueId()).orElse(null);
        if (user == null) {
            log.error("User is not found by principal id {}", actionInfo.principal().getUniqueId());
            return failed();
        }

        var result = channelService.bindTelegramUser(telegramUser, user);
        if (result == IdentityUserChannelService.TelegramBindResult.SUCCESS) {
            actionTokenService.consumeBindTelegramAction(actionInfo.id());
            return new Result(true, telegramUser);
        }
        return failed();
    }

    private UUID findActionId(String initData) {
        try {
            var startParam = telegramAuthenticationService.findStartParam(initData);
            return startParam == null ? null : UUID.fromString(startParam);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Result failed() {
        return new Result(false, null);
    }

    public record Result(boolean success, TelegramUserPrincipal telegramUser) {
    }
}

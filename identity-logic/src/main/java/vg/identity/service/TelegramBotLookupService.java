package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotWithUri;
import vg.identity.repository.IdentityApplicationRepository;
import vg.unique.id.model.UniqueId;

import java.net.URI;

/** Read-only bot configuration lookup shared by authentication, administration, and webhook services. */
@Service
@RequiredArgsConstructor
public class TelegramBotLookupService {
    private final IdentityApplicationRepository applicationRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    /** Messaging uses only applications configured as Telegram bots. */
    @Transactional(readOnly = true, noRollbackFor = IllegalArgumentException.class)
    TelegramBot findMessagingBot(UniqueId applicationUniqueId) {
        return applicationRepository.findById(applicationUniqueId.getLongValue())
                .filter(application -> TelegramUriBuilder.isBotUri(application.getPrincipal().getName()))
                .map(this::readBot).orElse(null);
    }

    /** Strict lookup used when the calling application must exist. */
    @Transactional(readOnly = true)
    TelegramBot getTelegramBot(UniqueId applicationUniqueId) {
        var bot = findTelegramBot(applicationUniqueId);
        if (bot == null) {
            throw new EntityNotFoundException();
        }
        return bot;
    }

    /**
     * Returns null for absence. Invalid payloads throw without forcing a caller's transaction to roll back,
     * so webhook processing can deliberately ignore them. Callers authenticate using the returned bot.
     */
    @Transactional(readOnly = true, noRollbackFor = IllegalArgumentException.class)
    TelegramBot findTelegramBot(UniqueId applicationUniqueId) {
        return applicationRepository.findById(applicationUniqueId).map(this::readBot).orElse(null);
    }

    /**
     * Resolves a case-insensitive bot username and its public URL, or null for absence. Intentionally
     * unsecured for pre-authentication flows; authenticated callers must enforce their own authorization.
     */
    @Transactional(readOnly = true)
    TelegramBotWithUri findTelegramBotByUsername(String botUsername) {
        var uri = TelegramUriBuilder.botUri(botUsername).toString();
        return applicationRepository.findByPrincipal_NameHash(encryptionService.hashPrincipalName(uri))
                .map(application -> TelegramBotWithUri.builder()
                        .uri(URI.create(application.getPrincipal().getName()))
                        .bot(readBot(application))
                        .build())
                .orElse(null);
    }

    /** Decodes an already loaded application without another database lookup or exposing an entity. */
    TelegramBot readBot(IdentityApplicationEntity application) {
        try {
            var bot = objectMapper.readValue(application.getPayload(), TelegramBot.class);
            if (bot == null) {
                throw new IllegalArgumentException("Cannot deserialize Telegram bot");
            }
            return bot.withApplicationId(new UniqueId(application.getUniqueId()));
        } catch (JacksonException e) {
            // Parsing exceptions can contain decrypted payload fragments, including the bot token.
            throw new IllegalArgumentException("Cannot deserialize Telegram bot");
        }
    }
}

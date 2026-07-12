package vg.identity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.application.TelegramBot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TelegramAuthenticationService {

    private final ObjectMapper objectMapper;
    private final Duration authDateTtl;
    private final Clock clock;

    @Autowired
    public TelegramAuthenticationService(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${identity.telegram.bot.auth-date-ttl:PT1H}") Duration authDateTtl
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.authDateTtl = authDateTtl;
    }

    public Optional<Authentication> authenticate(TelegramBot bot, String initData) {
        return parseUser(bot, initData)
                .map(user -> UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        initData,
                        List.of(
                                new SimpleGrantedAuthority(
                                        IdentityUserSystemRole.VERIFIED_USER.name()
                                )
                        )
                ));
    }

    public Optional<TelegramUserPrincipal> parseUser(TelegramBot bot, String initData) {
        var parameters = parseParameters(initData);

        if (parameters.isEmpty()) {
            return Optional.empty();
        }

        if (!isValidHash(bot, parameters)) {
            return Optional.empty();
        }

        return parseUser(parameters);
    }

    public String findStartParam(String initData) {
        return parseParameters(initData).get("start_param");
    }

    private Map<String, String> parseParameters(String initData) {
        if (initData == null || initData.isBlank()) {
            return Collections.emptyMap();
        }

        return Stream.of(initData.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> decode(parts[1]),
                        (first, second) -> first
                ));
    }


    private Optional<TelegramUserPrincipal> parseUser(Map<String, String> parameters) {
        var userJson = parameters.get("user");
        if (userJson == null || userJson.isBlank()) {
            return Optional.empty();
        }

        try {
            var user = objectMapper.readTree(userJson);
            var id = user.get("id");
            if (id == null || !id.canConvertToLong()) {
                return Optional.empty();
            }

            return Optional.of(
                    TelegramUserPrincipal.builder()
                            .id(id.longValue())
                            .bot(booleanOrNull(user.get("is_bot")))
                            .firstName(textOrNull(user.get("first_name")))
                            .lastName(textOrNull(user.get("last_name")))
                            .username(textOrNull(user.get("username")))
                            .languageCode(textOrNull(user.get("language_code")))
                            .premium(booleanOrNull(user.get("is_premium")))
                            .addedToAttachmentMenu(booleanOrNull(user.get("added_to_attachment_menu")))
                            .allowsWriteToPm(booleanOrNull(user.get("allows_write_to_pm")))
                            .photoUrl(textOrNull(user.get("photo_url")))
                            .build()
            );
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return Boolean.parseBoolean(node.asText());
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isValidHash(TelegramBot bot, Map<String, String> parameters) {
        if (!isValidAuthDate(parameters)) {
            return false;
        }

        var providedHash = parameters.get("hash");
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }

        var dataCheckString = parameters.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        var actualHash = HexFormat.of().formatHex(
                hmacSha256(
                        hmacSha256(getBytes("WebAppData"), getBytes(bot.token())),
                        getBytes(dataCheckString)
                )
        );
        return MessageDigest.isEqual(
                getBytes(actualHash),
                getBytes(providedHash)
        );
    }

    private boolean isValidAuthDate(Map<String, String> parameters) {
        var authDateValue = parameters.get("auth_date");
        if (authDateValue == null || authDateValue.isBlank()) {
            return false;
        }

        try {
            var authDate = Instant.ofEpochSecond(Long.parseLong(authDateValue));
            var now = clock.instant();
            return authDate.plus(authDateTtl).isAfter(now);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot calculate Telegram init data hash", e);
        }
    }

    private byte[] getBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

}

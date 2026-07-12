package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.application.TelegramBot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramAuthenticationServiceTest {

    private static final String BOT_TOKEN = "123456:token";
    private static final Instant NOW = Instant.parse("2026-07-17T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String USER_JSON = """
            {"id":42,"is_bot":false,"first_name":"John","last_name":"Doe","username":"jdoe","language_code":"en","is_premium":true,"added_to_attachment_menu":false,"allows_write_to_pm":true,"photo_url":"https://example.com/photo.jpg"}
            """;

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private JsonNode userNode;

    private TelegramAuthenticationService service;
    private TelegramBot bot;

    @BeforeEach
    void setUp() {
        service = new TelegramAuthenticationService(objectMapper, CLOCK, Duration.ofHours(1));
        bot = TelegramBot.builder()
                .token(BOT_TOKEN)
                .build();
    }

    @Test
    void authenticate_whenInitDataIsValid_returnsAuthenticatedTelegramPrincipal() throws Exception {
        stubValidUserJson();

        var authentication = service.authenticate(bot, initData(USER_JSON, NOW.minusSeconds(60)));

        assertThat(authentication).isPresent();
        assertThat(authentication.get().isAuthenticated()).isTrue();
        assertThat(authentication.get().getCredentials()).isInstanceOf(String.class);
        assertThat(authentication.get().getAuthorities())
                .extracting("authority")
                .containsExactly(IdentityUserSystemRole.VERIFIED_USER.name());

        assertThat(authentication.get().getPrincipal())
                .isInstanceOfSatisfying(TelegramUserPrincipal.class, this::assertValidPrincipal);
    }

    @Test
    void parseUser_whenInitDataIsValid_returnsTelegramPrincipal() throws Exception {
        stubValidUserJson();

        var principal = service.parseUser(bot, initData(USER_JSON, NOW.minusSeconds(60)));

        assertThat(principal).isPresent();
        assertValidPrincipal(principal.get());
    }

    @Test
    void authenticate_whenHashIsInvalid_returnsEmptyAndDoesNotParseUser() throws Exception {
        var initData = initData(USER_JSON, NOW.minusSeconds(60)) + "0";

        assertThat(service.authenticate(bot, initData)).isEmpty();

        verify(objectMapper, never()).readTree(USER_JSON);
    }

    @Test
    void authenticate_whenAuthDateIsExpired_returnsEmptyAndDoesNotParseUser() throws Exception {
        var initData = initData(USER_JSON, NOW.minus(Duration.ofHours(2)));

        assertThat(service.authenticate(bot, initData)).isEmpty();

        verify(objectMapper, never()).readTree(USER_JSON);
    }

    @Test
    void authenticate_whenUserIsMissing_returnsEmpty() {
        var authDate = String.valueOf(NOW.minusSeconds(60).getEpochSecond());
        var parameters = Map.of("auth_date", authDate, "query_id", "abc");

        assertThat(service.authenticate(bot, initData(parameters))).isEmpty();
    }

    @Test
    void authenticate_whenUserIdIsMissing_returnsEmpty() throws Exception {
        when(objectMapper.readTree(USER_JSON)).thenReturn(userNode);
        when(userNode.get("id")).thenReturn(null);

        assertThat(service.authenticate(bot, initData(USER_JSON, NOW.minusSeconds(60)))).isEmpty();
    }

    private void stubUserField(String field, JsonNode value) {
        when(userNode.get(field)).thenReturn(value);
    }

    private void stubValidUserJson() throws Exception {
        when(objectMapper.readTree(USER_JSON)).thenReturn(userNode);
        stubUserField("id", idNode(42L));
        stubUserField("is_bot", textNode("false"));
        stubUserField("first_name", textNode("John"));
        stubUserField("last_name", textNode("Doe"));
        stubUserField("username", textNode("jdoe"));
        stubUserField("language_code", textNode("en"));
        stubUserField("is_premium", textNode("true"));
        stubUserField("added_to_attachment_menu", textNode("false"));
        stubUserField("allows_write_to_pm", textNode("true"));
        stubUserField("photo_url", textNode("https://example.com/photo.jpg"));
    }

    private void assertValidPrincipal(TelegramUserPrincipal principal) {
        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.bot()).isFalse();
        assertThat(principal.firstName()).isEqualTo("John");
        assertThat(principal.lastName()).isEqualTo("Doe");
        assertThat(principal.username()).isEqualTo("jdoe");
        assertThat(principal.languageCode()).isEqualTo("en");
        assertThat(principal.premium()).isTrue();
        assertThat(principal.addedToAttachmentMenu()).isFalse();
        assertThat(principal.allowsWriteToPm()).isTrue();
        assertThat(principal.photoUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    private JsonNode idNode(long longValue) {
        var node = org.mockito.Mockito.mock(JsonNode.class);
        when(node.canConvertToLong()).thenReturn(true);
        when(node.longValue()).thenReturn(longValue);
        return node;
    }

    private JsonNode textNode(String text) {
        var node = org.mockito.Mockito.mock(JsonNode.class);
        when(node.isNull()).thenReturn(false);
        when(node.asText()).thenReturn(text);
        return node;
    }

    private String initData(String userJson, Instant authDate) {
        return initData(Map.of(
                "auth_date", String.valueOf(authDate.getEpochSecond()),
                "query_id", "AAHdF6IQAAAAAN0XohDhrOrc",
                "user", userJson
        ));
    }

    private String initData(Map<String, String> parameters) {
        var dataCheckString = dataCheckString(parameters);
        var hash = HexFormat.of().formatHex(
                hmacSha256(
                        hmacSha256(bytes("WebAppData"), bytes(BOT_TOKEN)),
                        bytes(dataCheckString)
                )
        );

        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"))
                + "&hash=" + hash;
    }

    private String dataCheckString(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

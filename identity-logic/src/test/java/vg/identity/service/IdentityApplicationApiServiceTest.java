package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationApiServiceTest {
    @Mock
    private IdentityApplicationService applicationService;
    @Mock
    private CurrentIdentityApplicationService currentApplicationService;
    @Mock
    private TelegramAuthenticationService telegramAuthenticationService;
    @Mock
    private IdentityApplicationUserProvisioningService userProvisioningService;
    @Mock
    private IdentityApplicationClaimService claimService;

    private IdentityApplicationApiService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationApiService(
                applicationService,
                currentApplicationService,
                telegramAuthenticationService,
                userProvisioningService,
                claimService
        );
    }

    @Test
    void me_whenCurrentPrincipalIsApplication_returnsOnlySafeApplicationMetadata() {
        var uniqueId = new UniqueId(42L);
        var principal = mock(IdentityApplicationPrincipal.class);
        when(principal.getUniqueId()).thenReturn(uniqueId);
        when(currentApplicationService.requireApplicationPrincipal()).thenReturn(principal);
        when(applicationService.getAuthenticatedApplication(uniqueId)).thenReturn(
                IdentityApplication.builder()
                        .uniqueId(uniqueId)
                        .workspaceUniqueId(7L)
                        .name("Orders")
                        .uri("https://example.test/orders")
                        .payload("must-not-be-returned")
                        .build()
        );

        var response = service.me();

        assertThat(response.uniqueId()).isEqualTo(uniqueId.toString());
        assertThat(response.workspaceUniqueId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Orders");
        assertThat(response.uri()).isEqualTo("https://example.test/orders");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("uniqueId", "workspaceUniqueId", "name", "uri");
    }

    @Test
    void me_whenCurrentPrincipalIsNotApplication_throwsAccessDeniedException() {
        when(currentApplicationService.requireApplicationPrincipal())
                .thenThrow(new AccessDeniedException("Application authentication is required"));

        assertThatThrownBy(service::me)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");

        verifyNoInteractions(applicationService);
    }

    @Test
    void authenticateTelegram_whenProofIsValid_returnsApplicationScopedClaims() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        var telegramUser = TelegramUserPrincipal.builder().id(100L).build();
        var identityUser = vg.identity.entity.IdentityUserEntity.builder().uniqueId(84L).build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(applicationService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "init-data")).thenReturn(Optional.of(telegramUser));
        when(userProvisioningService.resolveTelegramUser(telegramUser)).thenReturn(identityUser);
        when(claimService.findClaimsByScope(applicationUniqueId, new UniqueId(84L)))
                .thenReturn(Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read", "orders.write")));

        var result = service.authenticateTelegram("init-data");

        assertThat(result).contains(new IdentityApplicationUserPrincipal(
                applicationUniqueId.toString(),
                new UniqueId(84L).toString(),
                Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read", "orders.write"))
        ));
    }

    @Test
    void authenticateTelegram_whenProofIsInvalid_returnsEmptyWithoutProvisioningUser() {
        var applicationUniqueId = new UniqueId(42L);
        var bot = TelegramBot.builder().token("test-token").build();
        when(currentApplicationService.requireApplicationUniqueId()).thenReturn(applicationUniqueId);
        when(applicationService.getTelegramBot(applicationUniqueId)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot, "invalid")).thenReturn(Optional.empty());

        assertThat(service.authenticateTelegram("invalid")).isEmpty();

        verifyNoInteractions(userProvisioningService, claimService);
    }
}

package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.entity.IdentityWorkspaceScopeClaimDictionaryEntity;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.access.Permission;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserClaimRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityWorkspaceScopeClaimDictionaryRepository;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationClaimServiceTest {
    @Mock
    private IdentityApplicationRepository applicationRepository;
    @Mock
    private IdentityApplicationUserClaimRepository claimRepository;
    @Mock
    private IdentityUserRepository userRepository;
    @Mock
    private IdentityWorkspaceScopeClaimDictionaryRepository dictionaryRepository;
    @Mock
    private EncryptionService encryptionService;

    private IdentityApplicationClaimService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationClaimService(
                applicationRepository,
                claimRepository,
                userRepository,
                dictionaryRepository,
                encryptionService
        );
    }

    /**
     * Authorization is enforced declaratively by {@code @PreAuthorize}, not in method bodies, so it cannot be
     * exercised on a plain (unproxied) instance. Assert the security contract on the annotations instead;
     * enforcement itself is covered by {@code IdentityApplicationClaimGrantIntegrationTest}.
     */
    @Test
    void grantAndRevoke_areSecuredWithExpectedPreAuthorizeExpressions() throws Exception {
        var grant = IdentityApplicationClaimService.class.getMethod(
                "grantClaim", UniqueId.class, UniqueId.class, String.class, String.class);
        var revoke = IdentityApplicationClaimService.class.getMethod(
                "revokeClaim", UniqueId.class, UniqueId.class, String.class, String.class);

        assertThat(grant.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(grant.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.CLAIM_CREATE + "')");
        assertThat(revoke.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(revoke.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.CLAIM_DELETE + "')");

        var read = IdentityApplicationClaimService.class.getMethod(
                "getUserClaims", UniqueId.class, UniqueId.class);
        assertThat(read.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(read.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')");
    }

    @Test
    void grantClaim_internsAndReferencesNormalizedScopeAndClaim() {
        var applicationUniqueId = new UniqueId(42L);
        var identityUserUniqueId = new UniqueId(84L);
        var workspace = IdentityWorkspaceEntity.builder().uniqueId(7L).build();
        var application = mock(IdentityApplicationEntity.class);
        when(application.getWorkspace()).thenReturn(workspace);
        when(applicationRepository.findById(42L)).thenReturn(Optional.of(application));
        when(encryptionService.hashCaseSensitive(any())).thenReturn(new byte[]{1});
        // Both scope and claim are absent, so each is interned as a new dictionary entry.
        when(dictionaryRepository.findByWorkspace_UniqueIdAndNameBlindIndex(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(dictionaryRepository.save(any(IdentityWorkspaceScopeClaimDictionaryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.existsById(any())).thenReturn(false);
        when(userRepository.findById(84L)).thenReturn(Optional.of(mock(IdentityUserEntity.class)));
        var savedClaim = new AtomicReference<IdentityApplicationUserClaimEntity>();
        when(claimRepository.save(any(IdentityApplicationUserClaimEntity.class))).thenAnswer(invocation -> {
            savedClaim.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.grantClaim(
                applicationUniqueId,
                identityUserUniqueId,
                IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE,
                " Orders.READ "
        );

        assertThat(savedClaim.get().getScope().getName()).isEqualTo(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE);
        assertThat(savedClaim.get().getScope().getWorkspace()).isSameAs(workspace);
        assertThat(savedClaim.get().getClaim().getName()).isEqualTo("orders.read");
        assertThat(savedClaim.get().getClaim().getWorkspace()).isSameAs(workspace);
    }

    @Test
    void grantClaim_internsArbitraryScopeNormalized() {
        var applicationUniqueId = new UniqueId(42L);
        var identityUserUniqueId = new UniqueId(84L);
        var workspace = IdentityWorkspaceEntity.builder().uniqueId(7L).build();
        var application = mock(IdentityApplicationEntity.class);
        when(application.getWorkspace()).thenReturn(workspace);
        when(applicationRepository.findById(42L)).thenReturn(Optional.of(application));
        when(encryptionService.hashCaseSensitive(any())).thenReturn(new byte[]{1});
        when(dictionaryRepository.findByWorkspace_UniqueIdAndNameBlindIndex(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(dictionaryRepository.save(any(IdentityWorkspaceScopeClaimDictionaryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.existsById(any())).thenReturn(false);
        when(userRepository.findById(84L)).thenReturn(Optional.of(mock(IdentityUserEntity.class)));
        var savedClaim = new AtomicReference<IdentityApplicationUserClaimEntity>();
        when(claimRepository.save(any(IdentityApplicationUserClaimEntity.class))).thenAnswer(invocation -> {
            savedClaim.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.grantClaim(applicationUniqueId, identityUserUniqueId, "  Features  ", "  Beta  ");

        // A scope other than the reserved "permissions" is accepted and interned trimmed + lower-cased.
        assertThat(savedClaim.get().getScope().getName()).isEqualTo("features");
        assertThat(savedClaim.get().getClaim().getName()).isEqualTo("beta");
    }

    @Test
    void grantClaim_whenScopeIsBlank_rejectsItBeforeTouchingAnyRepository() {
        assertThatThrownBy(() -> service.grantClaim(new UniqueId(42L), new UniqueId(84L), "  ", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application claim scope must not be blank");

        verifyNoInteractions(applicationRepository, claimRepository, userRepository, dictionaryRepository, encryptionService);
    }

    @Test
    void revokeClaim_whenScopeAndClaimAreInterned_deletesTheNormalizedGrant() {
        var applicationUniqueId = new UniqueId(42L);
        var identityUserUniqueId = new UniqueId(84L);
        var workspace = IdentityWorkspaceEntity.builder().uniqueId(7L).build();
        var application = mock(IdentityApplicationEntity.class);
        when(application.getWorkspace()).thenReturn(workspace);
        when(applicationRepository.findById(42L)).thenReturn(Optional.of(application));
        when(encryptionService.hashCaseSensitive(any())).thenReturn(new byte[]{1});
        when(dictionaryRepository.findByWorkspace_UniqueIdAndNameBlindIndex(anyLong(), any()))
                .thenReturn(Optional.of(IdentityWorkspaceScopeClaimDictionaryEntity.builder().id(100).build()));

        service.revokeClaim(
                applicationUniqueId,
                identityUserUniqueId,
                IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE,
                " Orders.READ "
        );

        verify(claimRepository).deleteById(any());
    }

    @Test
    void revokeClaim_whenClaimWasNeverInternedInWorkspace_isNoOp() {
        var applicationUniqueId = new UniqueId(42L);
        var workspace = IdentityWorkspaceEntity.builder().uniqueId(7L).build();
        var application = mock(IdentityApplicationEntity.class);
        when(application.getWorkspace()).thenReturn(workspace);
        when(applicationRepository.findById(42L)).thenReturn(Optional.of(application));
        when(encryptionService.hashCaseSensitive(any())).thenReturn(new byte[]{1});
        when(dictionaryRepository.findByWorkspace_UniqueIdAndNameBlindIndex(anyLong(), any()))
                .thenReturn(Optional.empty());

        service.revokeClaim(
                applicationUniqueId,
                new UniqueId(84L),
                IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE,
                "orders.read"
        );

        verify(claimRepository, never()).deleteById(any());
    }
}

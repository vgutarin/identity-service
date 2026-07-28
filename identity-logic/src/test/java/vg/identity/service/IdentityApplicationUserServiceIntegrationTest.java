package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.access.Permission;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

/**
 * Verifies application-user membership: recorded on authentication (idempotently) and readable by a workspace
 * administrator holding {@code app.read}.
 */
class IdentityApplicationUserServiceIntegrationTest extends BaseIntegrationTest {
    private static final String ADMIN = "workspace-admin";

    @Autowired
    private IdentityApplicationUserService applicationUserService;

    @Test
    void recordAuthentication_provisionsMembershipOnceAndIsIdempotent() {
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var applicationUniqueId = new UniqueId(application.getUniqueId());
        var user = createIdentityUser("subject-" + nextString());

        applicationUserService.recordAuthentication(applicationUniqueId, user.getUniqueId().getLongValue());
        applicationUserService.recordAuthentication(applicationUniqueId, user.getUniqueId().getLongValue());

        var memberships = applicationUserRepository.findAll();
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getApplication().getUniqueId()).isEqualTo(application.getUniqueId());
        assertThat(memberships.get(0).getIdentityUser().getUniqueId()).isEqualTo(user.getUniqueId().getLongValue());
        assertThat(memberships.get(0).getCreatedAt()).isNotNull();
        assertThat(memberships.get(0).getLastAuthenticatedAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "USER")
    void findUsers_returnsApplicationsUsersForAWorkspaceAdministrator() {
        var admin = createIdentityUser(ADMIN);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        grantWorkspacePermission(admin, workspace, Permission.App.READ);
        var applicationUniqueId = new UniqueId(application.getUniqueId());
        var user = createIdentityUser("subject-" + nextString());
        applicationUserService.recordAuthentication(applicationUniqueId, user.getUniqueId().getLongValue());

        var users = applicationUserService.findUsers(applicationUniqueId);

        assertThat(users)
                .extracting(applicationUser -> applicationUser.uniqueId().getLongValue())
                .containsExactly(user.getUniqueId().getLongValue());
    }
}

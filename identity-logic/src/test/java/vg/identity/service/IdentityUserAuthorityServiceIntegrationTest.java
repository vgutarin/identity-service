package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "IDENTITY_ADMIN")
class IdentityUserAuthorityServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityUserAuthorityService authorityService;
    @Autowired
    IdentityUserServiceImpl userService;
    @Autowired
    IdentityAccountService accountService;
    @Autowired
    IdentityUserResourcePermissionRepository resourcePermissionRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;
    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    IdentityUserChannelRepository channelRepository;
    @Autowired
    IdentityUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        resourcePermissionRepository.deleteAll();
        permissionRepository.deleteAll();
        accountService.findAll().forEach(account -> accountService.delete(account.getUniqueId()));
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void findByUserAndResourceType_returnsAccountPermissionsWithResourceAndPermissionNames() {
        var user = userService.create(IdentityUser.builder()
                .username(nextString())
                .password(nextString())
                .build());
        var accountName = nextString();
        var account = accountService.create(IdentityAccountEntity.builder()
                .name(accountName)
                .build());

        authorityService.assignResourceAuthority(account, user, "read");

        assertThat(authorityService.findByUserAndResourceType(user, IdentityResourceType.ACCOUNT))
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission.getUserUniqueId()).isEqualTo(user.getUniqueId().value());
                    assertThat(permission.getResource().getUniqueId()).isEqualTo(account.getUniqueId());
                    assertThat(permission.getPermissionName()).isEqualTo("read");
                    assertThat(permission.getResourceName()).isEqualTo(accountName);
                    assertThat(permission.getCreatedAt()).isNotNull();
                });
    }
}

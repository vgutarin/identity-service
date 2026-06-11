package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.repository.IdentityAccountRepository;
import vg.unique.id.service.UniqueIdService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "USER")
class IdentityAccountServicePermissionIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityAccountService service;
    @Autowired
    IdentityAccountRepository accountRepository;
    @Autowired
    UniqueIdService uniqueIdService;

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
    }

    @Test
    void createThrows_WhenUserIsNotAdmin() {
        assertThatThrownBy(() -> service.create(buildAccount()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(accountRepository.findAll()).isEmpty();
    }

    @Test
    void getThrows_WhenUserDoesNotHaveResourceAuthority() {
        var saved = saveAccount();

        assertThatThrownBy(() -> service.get(saved.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateThrows_WhenUserIsNotAdmin() {
        var saved = saveAccount();
        var newName = nextString();

        assertThatThrownBy(() -> service.update(
                IdentityAccountEntity.builder()
                        .uniqueId(saved.getUniqueId())
                        .name(newName)
                        .build()
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(accountRepository.findById(saved.getUniqueId()))
                .hasValueSatisfying(account -> assertThat(account.getName()).isEqualTo(saved.getName()));
    }

    @Test
    void deleteThrows_WhenUserIsNotAdmin() {
        var saved = saveAccount();

        assertThatThrownBy(() -> service.delete(saved.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(accountRepository.findById(saved.getUniqueId())).isPresent();
    }

    private IdentityAccountEntity saveAccount() {
        var saved = accountRepository.saveWithNewUniqueId(buildAccount(), uniqueIdService);
        accountRepository.flush();
        return saved;
    }

    private IdentityAccountEntity buildAccount() {
        return IdentityAccountEntity.builder()
                .name(nextString())
                .build();
    }
}

package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.repository.IdentityAccountRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityAccountServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityAccountRepository accountRepository;

    @InjectMocks
    IdentityAccountService service;

    @Test
    void create() {
        var account = IdentityAccountEntity.builder()
                .name(nextString())
                .build();
        var saved = account(1L);

        when(accountRepository.saveWithNewUniqueId(account, uniqueIdService)).thenReturn(saved);

        assertThat(service.create(account)).isSameAs(saved);
        verify(accountRepository).flush();
    }

    @Test
    void get() {
        var accountId = nextLong();
        var account = account(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        assertThat(service.get(accountId)).isSameAs(account);
    }

    @Test
    void getThrows_WhenEntityIsNotFound() {
        var accountId = nextLong();

        assertThatThrownBy(() -> service.get(accountId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findAll() {
        var accounts = List.of(account(1L), account(2L));

        when(accountRepository.findAll()).thenReturn(accounts);

        assertThat(service.findAll()).isSameAs(accounts);
    }

    @Test
    void update() {
        var accountId = nextLong();
        var newName = nextString();
        var model = IdentityAccountEntity.builder()
                .uniqueId(accountId)
                .name(newName)
                .build();
        var existing = account(accountId);
        var saved = account(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(saved);

        assertThat(service.update(model)).isSameAs(saved);
        assertThat(existing.getName()).isEqualTo(newName);
        verify(accountRepository).flush();
    }

    @Test
    void updateThrows_WhenEntityIsNotFound() {
        var model = account(nextLong());

        when(accountRepository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete() {
        var accountId = nextLong();
        var account = account(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service.delete(accountId);

        verify(accountRepository).delete(account);
        verify(accountRepository).flush();
    }

    private static IdentityAccountEntity account(long id) {
        return IdentityAccountEntity.builder()
                .uniqueId(id)
                .name(nextString())
                .build();
    }
}

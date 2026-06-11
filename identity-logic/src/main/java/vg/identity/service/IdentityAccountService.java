package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.repository.IdentityAccountRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdentityAccountService {
    private final UniqueIdService uniqueIdService;
    private final IdentityAccountRepository accountRepository;

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public IdentityAccountEntity create(IdentityAccountEntity account) {
        var saved = accountRepository.saveWithNewUniqueId(account, uniqueIdService);
        accountRepository.flush();
        return saved;
    }

    @PreAuthorize("@authorityChecker.hasResourceAuthority(#uniqueId, 'read')")
    @Transactional(readOnly = true)
    public IdentityAccountEntity get(long uniqueId) {
        return accountRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<IdentityAccountEntity> findAll() {
        return accountRepository.findAll();
    }

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public IdentityAccountEntity update(IdentityAccountEntity account) {
        var existing = accountRepository.findById(account.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);

        existing.setName(account.getName());

        var saved = accountRepository.save(existing);
        accountRepository.flush();
        return saved;
    }

    @PreAuthorize("hasRole('IDENTITY_ADMIN')")
    @Transactional
    public void delete(Long uniqueId) {
        var existing = accountRepository.findById(uniqueId)
                .orElseThrow(EntityNotFoundException::new);

        accountRepository.delete(existing);
        accountRepository.flush();
    }
}

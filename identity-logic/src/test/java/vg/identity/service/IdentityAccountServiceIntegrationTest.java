package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.repository.IdentityAccountRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "IDENTITY_ADMIN")
class IdentityAccountServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityAccountService service;
    @Autowired
    IdentityAccountRepository accountRepository;
    @Autowired
    IdentityUserRepository userRepository;
    @Autowired
    IdentityUserChannelRepository channelRepository;
    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;

    private String name;

    @BeforeEach
    void setUp() {
        name = nextString();
    }

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void create() {
        var saved = service.create(buildAccount());

        assertThat(saved.getUniqueId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getCreatedAt()).isCloseTo(
                Instant.now(),
                new TemporalUnitWithinOffset(10, ChronoUnit.SECONDS)
        );
        assertThat(saved.getVersion()).isEqualTo(0);
    }

    @Test
    void get() {
        var saved = service.create(buildAccount());

        var found = service.get(saved.getUniqueId());

        assertThat(found.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(found.getName()).isEqualTo(name);
    }

    @Test
    void findAll() {
        var first = service.create(buildAccount());
        var second = service.create(IdentityAccountEntity.builder().name(nextString()).build());

        assertThat(service.findAll())
                .extracting(IdentityAccountEntity::getUniqueId)
                .contains(first.getUniqueId(), second.getUniqueId());
    }

    @Test
    void update() {
        var saved = service.create(buildAccount());
        var newName = nextString();

        var updated = service.update(
                IdentityAccountEntity.builder()
                        .uniqueId(saved.getUniqueId())
                        .name(newName)
                        .build()
        );

        assertThat(updated.getUniqueId()).isEqualTo(saved.getUniqueId());
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void delete() {
        var saved = service.create(buildAccount());

        service.delete(saved.getUniqueId());

        assertThat(accountRepository.findById(saved.getUniqueId())).isEmpty();
    }

    @Test
    void deleteThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.delete(Long.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private IdentityAccountEntity buildAccount() {
        return IdentityAccountEntity.builder()
                .name(name)
                .build();
    }
}

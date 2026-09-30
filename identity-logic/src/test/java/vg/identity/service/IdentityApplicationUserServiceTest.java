package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import vg.identity.entity.IdentityApplicationUserEntity;
import vg.identity.model.IdentityApplicationUserMetadata;
import vg.identity.model.IdentityApplicationUserMetadataFlag;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationUserServiceTest {
    private static final UniqueId APPLICATION_UNIQUE_ID = new UniqueId(42L);
    private static final UniqueId USER_UNIQUE_ID = new UniqueId(84L);

    @Mock
    private IdentityApplicationUserRepository membershipRepository;
    @Mock
    private IdentityApplicationRepository applicationRepository;
    @Mock
    private IdentityUserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private IdentityApplicationUserService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationUserService(
                membershipRepository,
                applicationRepository,
                userRepository,
                transactionManager,
                Clock.systemUTC()
        );
    }

    @Test
    void telegramPhoneDisclosure_setsAndClearsTheCompactMetadataFlag() throws Exception {
        var membership = IdentityApplicationUserEntity.builder()
                .metadata(IdentityApplicationUserMetadata.empty())
                .build();
        when(membershipRepository.findById(any())).thenReturn(Optional.of(membership));

        service.enableTelegramPhoneDisclosure(APPLICATION_UNIQUE_ID, USER_UNIQUE_ID.getLongValue());

        assertThat(membership.getMetadata().hasFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE)).isTrue();
        assertThat(service.hasTelegramPhoneDisclosure(APPLICATION_UNIQUE_ID, USER_UNIQUE_ID)).isTrue();

        service.disableTelegramPhoneDisclosure(APPLICATION_UNIQUE_ID, USER_UNIQUE_ID);

        assertThat(membership.getMetadata().flags()).isNull();
        assertThat(service.hasTelegramPhoneDisclosure(APPLICATION_UNIQUE_ID, USER_UNIQUE_ID)).isFalse();
    }

    @Test
    void enableTelegramPhoneDisclosure_whenMembershipHasNoMetadata_createsTypedMetadata() {
        var membership = IdentityApplicationUserEntity.builder().build();
        when(membershipRepository.findById(any())).thenReturn(Optional.of(membership));

        service.enableTelegramPhoneDisclosure(APPLICATION_UNIQUE_ID, USER_UNIQUE_ID.getLongValue());

        assertThat(membership.getMetadata().hasFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE)).isTrue();
    }
}

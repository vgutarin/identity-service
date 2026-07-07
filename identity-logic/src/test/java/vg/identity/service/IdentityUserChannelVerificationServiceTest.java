package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.IdentityUserChannelVerificationProperties;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserChannelVerificationEntity;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserChannelVerificationRepository;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityUserChannelVerificationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IdentityUserChannelVerificationRepository verificationRepository;
    @Mock
    private IdentityUserChannelRepository channelRepository;
    @Mock
    private IdentityCommandService commandService;

    private IdentityUserChannelVerificationProperties properties;
    private IdentityUserChannelVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new IdentityUserChannelVerificationProperties();
        properties.setLinkPrefix("https://example.com/verify/");
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
        service = new IdentityUserChannelVerificationService(
                verificationRepository,
                channelRepository,
                commandService,
                properties,
                clock
        );
    }

    @Test
    void verify_whenEmailChannelProvided_savesVerificationAndEnqueuesEmail() {
        var channel = emailChannel(7L, "john@example.com");
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var savedVerification = new AtomicReference<IdentityUserChannelVerificationEntity>();
        when(verificationRepository.existsByIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                7L,
                clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(false);
        when(channelRepository.getReferenceById(7L)).thenReturn(channelEntity);
        when(verificationRepository.save(any(IdentityUserChannelVerificationEntity.class)))
                .thenAnswer(invocation -> {
                    var entity = invocation.getArgument(0, IdentityUserChannelVerificationEntity.class);
                    savedVerification.set(entity);
                    return entity;
                });

        service.verify(channel);

        var verification = savedVerification.get();
        assertThat(verification.getId()).isNotNull();
        assertThat(verification.getIdentityUserChannel()).isSameAs(channelEntity);
        assertThat(verification.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(verification.getExpireAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));

        var expectedEmail = EmailMessage.builder()
                .to(java.util.List.of("john@example.com"))
                .subject("Verify your email")
                .body("https://example.com/verify/" + verification.getId())
                .build();
        verify(commandService).enqueue(expectedEmail);
    }

    @Test
    void verify_whenVerificationWasRequestedInsideCooldown_doesNotSaveVerificationAndDoesNotEnqueueEmail() {
        var channel = emailChannel(7L, "john@example.com");
        when(verificationRepository.existsByIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                7L,
                clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(true);

        service.verify(channel);

        verify(channelRepository, never()).getReferenceById(7L);
        verify(verificationRepository, never()).save(any(IdentityUserChannelVerificationEntity.class));
        verify(commandService, never()).enqueue(any(EmailMessage.class));
    }

    @Test
    void verify_whenChannelUniqueIdIsMissing_throwsNullPointerException() {
        var channel = emailChannel(null, "john@example.com");

        assertThatThrownBy(() -> service.verify(channel))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel uniqueId is required");
    }

    @Test
    void verifyById_whenVerificationExistsAndIsNotExpired_setsChannelVerifiedAtAndReturnsTrue() {
        var id = UUID.randomUUID();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityUserChannelVerificationEntity.builder()
                .id(id)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(verificationRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);

        assertThat(service.verify(id)).isTrue();

        assertThat(channelEntity.getVerifiedAt()).isEqualTo(clock.instant());
        verify(channelRepository).save(channelEntity);
        verify(channelRepository).flush();
    }

    @Test
    void verifyById_whenVerificationDoesNotExist_returnsFalse() {
        var id = UUID.randomUUID();
        when(verificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.verify(id)).isFalse();

        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(channelRepository, never()).flush();
    }

    @Test
    void verifyById_whenVerificationIsExpired_returnsFalse() {
        var id = UUID.randomUUID();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityUserChannelVerificationEntity.builder()
                .id(id)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofHours(3)))
                .expireAt(clock.instant())
                .build();
        when(verificationRepository.findById(id)).thenReturn(Optional.of(verification));

        assertThat(service.verify(id)).isFalse();

        assertThat(channelEntity.getVerifiedAt()).isNull();
        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(channelRepository, never()).flush();
    }

    private static IdentityUserChannelEmail emailChannel(Long uniqueId, String email) {
        var channel = new IdentityUserChannelEmail();
        if (uniqueId != null) {
            channel.setUniqueId(new UniqueId(uniqueId));
        }
        channel.setChannelType(IdentityChannelType.EMAIL);
        channel.setChannelUserId(email);
        return channel;
    }
}

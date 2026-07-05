package vg.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.mapper.IdentityCommandMapper;
import vg.identity.model.IdentityCommand;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;
import vg.identity.model.EmailMessage;
import vg.identity.repository.IdentityCommandRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class IdentityCommandServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-05T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IdentityCommandRepository repository;
    @Mock
    private IdentityCommandMapper mapper;
    @Mock
    private ObjectMapper objectMapper;
    private IdentityCommandService service;

    @BeforeEach
    void setUp() {
        service = new IdentityCommandService(repository, mapper, objectMapper, clock);
    }

    @Test
    void enqueue_whenSendEmailCommandProvided_savesQueuedCommand() throws Exception {
        var savedEntity = new AtomicReference<IdentityCommandEntity>();
        var payload = "{\"subject\":\"Welcome\"}";
        var model = IdentityCommand.builder()
                .id(1L)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var command = EmailMessage.builder()
                .to(List.of("user@example.com"))
                .subject("Welcome")
                .body("Hello")
                .build();
        when(repository.save(any(IdentityCommandEntity.class)))
                .thenAnswer(invocation -> {
                    var entity = invocation.getArgument(0, IdentityCommandEntity.class);
                    entity.setId(1L);
                    savedEntity.set(entity);
                    return entity;
                });
        when(objectMapper.writeValueAsString(command)).thenReturn(payload);
        when(mapper.toModel(any(IdentityCommandEntity.class))).thenReturn(model);

        var result = service.enqueue(command);

        assertThat(result).isSameAs(model);

        verify(repository).flush();

        var saved = savedEntity.get();
        assertThat(saved.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(saved.getCommandType()).isEqualTo(IdentityCommandType.SEND_EMAIL);
        assertThat(saved.getPayload()).isEqualTo(payload);
    }

    @Test
    void startNextQueuedCommand_whenQueueHasCommand_marksRunningAndReturnsModel() {
        var statuses = new ArrayList<IdentityCommandStatus>();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        var model = IdentityCommand.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        when(repository.findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(0L, IdentityCommandStatus.QUEUED))
                .thenReturn(Optional.of(entity));
        doAnswer(invocation -> {
            statuses.add(entity.getCommandStatus());
            return null;
        }).when(repository).flush();
        when(mapper.toModel(entity)).thenReturn(model);

        var result = service.startNextQueuedCommand();

        assertThat(result).isSameAs(model);
        assertThat(statuses).containsExactly(IdentityCommandStatus.RUNNING);
        assertThat(entity.getStartedAt()).isEqualTo(clock.instant());
        assertThat(service.getMaxStartedCommandId()).isEqualTo(7L);
    }

    @Test
    void startNextQueuedCommand_whenQueueIsEmpty_keepsMaxStartedCommandId() {
        when(repository.findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(0L, IdentityCommandStatus.QUEUED))
                .thenReturn(Optional.empty());

        var result = service.startNextQueuedCommand();

        assertThat(result).isNull();
        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void startNextQueuedCommand_whenCommandWasAlreadyStarted_searchesAfterMaxStartedCommandId() {
        var firstEntity = IdentityCommandEntity.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        var secondEntity = IdentityCommandEntity.builder()
                .id(9L)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        var firstModel = IdentityCommand.builder()
                .id(7L)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var secondModel = IdentityCommand.builder()
                .id(9L)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        when(repository.findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(0L, IdentityCommandStatus.QUEUED))
                .thenReturn(Optional.of(firstEntity));
        when(repository.findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(7L, IdentityCommandStatus.QUEUED))
                .thenReturn(Optional.of(secondEntity));
        when(mapper.toModel(firstEntity)).thenReturn(firstModel);
        when(mapper.toModel(secondEntity)).thenReturn(secondModel);

        var firstResult = service.startNextQueuedCommand();
        var secondResult = service.startNextQueuedCommand();

        assertThat(firstResult).isSameAs(firstModel);
        assertThat(secondResult).isSameAs(secondModel);
        assertThat(service.getMaxStartedCommandId()).isEqualTo(9L);
    }

    @Test
    void complete_whenCommandExists_marksCompletedAndKeepsMaxStartedCommandId() {
        var statuses = new ArrayList<IdentityCommandStatus>();
        var command = IdentityCommand.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        doAnswer(invocation -> {
            statuses.add(entity.getCommandStatus());
            return null;
        }).when(repository).flush();

        service.complete(command);

        assertThat(statuses).containsExactly(IdentityCommandStatus.COMPLETED);
        assertThat(entity.getCompletedAt()).isEqualTo(clock.instant());
        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void complete_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .version(2)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.complete(command))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
        assertThat(entity.getCompletedAt()).isNull();
        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void complete_whenCommandModelIsNotRunning_throwsIllegalStateException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();

        assertThatThrownBy(() -> service.complete(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");

        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void complete_whenPersistedCommandIsNotRunning_throwsIllegalStateException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.complete(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(entity.getCompletedAt()).isNull();
        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void fail_whenCommandExists_marksFailedAndStoresErrorMessage() {
        var statuses = new ArrayList<IdentityCommandStatus>();
        var command = IdentityCommand.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        doAnswer(invocation -> {
            statuses.add(entity.getCommandStatus());
            return null;
        }).when(repository).flush();

        service.fail(command, new IllegalStateException("boom"));

        assertThat(statuses).containsExactly(IdentityCommandStatus.FAILED);
        assertThat(entity.getErrorMessage())
                .contains("boom")
                .contains("java.lang.IllegalStateException");
        assertThat(entity.getCompletedAt()).isEqualTo(clock.instant());
        assertThat(service.getMaxStartedCommandId()).isZero();
    }

    @Test
    void fail_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .version(2)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.fail(command, new IllegalStateException("boom")))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getCompletedAt()).isNull();
    }

    @Test
    void fail_whenPersistedCommandIsNotRunning_throwsIllegalStateException() {
        var command = IdentityCommand.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.RUNNING)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .build();
        var entity = IdentityCommandEntity.builder()
                .id(7L)
                .version(3)
                .commandStatus(IdentityCommandStatus.COMPLETED)
                .commandType(IdentityCommandType.SEND_EMAIL)
                .payload("{}")
                .build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.fail(command, new IllegalStateException("boom")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.COMPLETED);
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getCompletedAt()).isNull();
    }
}

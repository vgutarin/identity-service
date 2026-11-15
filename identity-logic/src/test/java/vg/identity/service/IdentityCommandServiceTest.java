package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.mapper.IdentityCommandMapper;
import vg.identity.model.EmailMessage;
import vg.identity.model.command.IdentityCommand;
import vg.identity.model.command.IdentityCommandStatus;
import vg.identity.model.command.IdentityCommandType;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.identity.model.command.SetTelegramWebhookCommand;
import vg.identity.repository.IdentityCommandRepository;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
    void enqueueApplicationMessage_whenMessageReferenceIsProvided_savesQueuedReferenceCommand() {
        var command = SendApplicationMessageCommand.builder().messageUniqueId(new UniqueId(30L)).build();
        var saved = new AtomicReference<IdentityCommandEntity>();
        when(objectMapper.writeValueAsString(command)).thenReturn("message-reference");
        when(repository.save(any(IdentityCommandEntity.class))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get();
        });
        service.enqueueApplicationMessage(command);
        assertThat(saved.get().getCommandType()).isEqualTo(IdentityCommandType.SEND_APPLICATION_MESSAGE);
        assertThat(saved.get().getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(saved.get().getPayload()).isEqualTo("message-reference");
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

        var saved = savedEntity.get();
        assertThat(saved.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(saved.getCommandType()).isEqualTo(IdentityCommandType.SEND_EMAIL);
        assertThat(saved.getPayload()).isEqualTo(payload);
    }

    @Test
    void enqueue_whenWebhookSetupProvided_savesAQueuedIdentifierOnlyCommand() throws Exception {
        var command = SetTelegramWebhookCommand.builder().applicationUniqueId(new UniqueId(42L)).build();
        var payload = "{\"applicationUniqueId\":\"g\"}";
        var savedEntity = new AtomicReference<IdentityCommandEntity>();
        when(objectMapper.writeValueAsString(command)).thenReturn(payload);
        when(repository.save(any(IdentityCommandEntity.class))).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, IdentityCommandEntity.class);
            savedEntity.set(entity);
            return entity;
        });
        service.enqueueTelegramWebhook(command);
        assertThat(savedEntity.get().getCommandType()).isEqualTo(IdentityCommandType.SET_TELEGRAM_WEBHOOK);
        assertThat(savedEntity.get().getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        assertThat(savedEntity.get().getPayload()).isEqualTo(payload);
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

    @ParameterizedTest
    @CsvSource({"1, true", "99, true", "100, false", "101, false"})
    void startNextQueuedCommand_whenLateCommandIsBehindCursor_appliesExclusiveLookbackLimit(
            long gap, boolean eligible) {
        var visible = new ArrayList<IdentityCommandEntity>();
        visible.add(queuedCommand(1000L));
        var searches = stubQueue(visible);
        service.startNextQueuedCommand();
        visible.add(queuedCommand(1000L - gap));
        searches.clear();

        var result = service.startNextQueuedCommand();

        assertThat(searches).containsExactly(1000L, 900L);
        if (eligible) {
            assertThat(result.id()).isEqualTo(1000L - gap);
            assertThat(result.commandStatus()).isEqualTo(IdentityCommandStatus.RUNNING);
            assertThat(service.getMaxStartedCommandId()).isEqualTo(result.id());
        } else {
            assertThat(result).isNull();
            assertThat(visible.getLast().getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
            assertThat(service.getMaxStartedCommandId()).isEqualTo(1000L);
        }
    }

    @Test
    void startNextQueuedCommand_whenNewerWorkExists_defersLookbackUntilForwardSearchIsEmpty() {
        var visible = new ArrayList<IdentityCommandEntity>();
        visible.add(queuedCommand(1000L));
        var searches = stubQueue(visible);
        service.startNextQueuedCommand();
        visible.add(queuedCommand(999L));
        visible.add(queuedCommand(1001L));
        searches.clear();

        var newer = service.startNextQueuedCommand();

        assertThat(newer.id()).isEqualTo(1001L);
        assertThat(searches).containsExactly(1000L);
        searches.clear();

        var late = service.startNextQueuedCommand();

        assertThat(late.id()).isEqualTo(999L);
        assertThat(searches).containsExactly(1001L, 901L);
    }

    @Test
    void startNextQueuedCommand_whenOnlyStartedAndTerminalCommandsRemain_doesNotRetryThemThroughLookback() {
        var visible = new ArrayList<IdentityCommandEntity>();
        visible.add(queuedCommand(1000L));
        var searches = stubQueue(visible);
        service.startNextQueuedCommand();
        visible.add(IdentityCommandEntity.builder().id(999L).commandStatus(IdentityCommandStatus.COMPLETED).build());
        visible.add(IdentityCommandEntity.builder().id(998L).commandStatus(IdentityCommandStatus.FAILED).build());
        searches.clear();

        var result = service.startNextQueuedCommand();

        assertThat(result).isNull();
        assertThat(searches).containsExactly(1000L, 900L);
        assertThat(visible).extracting(IdentityCommandEntity::getCommandStatus)
                .containsExactly(IdentityCommandStatus.RUNNING, IdentityCommandStatus.COMPLETED, IdentityCommandStatus.FAILED);
    }

    private List<Long> stubQueue(List<IdentityCommandEntity> visible) {
        var searches = new ArrayList<Long>();
        when(repository.findFirstByIdGreaterThanAndCommandStatusOrderByIdAsc(anyLong(), eq(IdentityCommandStatus.QUEUED)))
                .thenAnswer(call -> {
                    long lowerBound = call.getArgument(0);
                    searches.add(lowerBound);
                    return visible.stream().filter(command -> command.getId() > lowerBound)
                            .filter(command -> command.getCommandStatus() == IdentityCommandStatus.QUEUED)
                            .min(java.util.Comparator.comparingLong(IdentityCommandEntity::getId));
                });
        when(mapper.toModel(any(IdentityCommandEntity.class))).thenAnswer(call -> {
            var command = call.getArgument(0, IdentityCommandEntity.class);
            return IdentityCommand.builder().id(command.getId()).commandStatus(command.getCommandStatus()).build();
        });
        return searches;
    }

    private IdentityCommandEntity queuedCommand(long id) {
        return IdentityCommandEntity.builder().id(id).commandStatus(IdentityCommandStatus.QUEUED)
                .commandType(IdentityCommandType.SEND_EMAIL).payload("{}").build();
    }

    @Test
    void complete_whenCommandExists_marksCompletedAndKeepsMaxStartedCommandId() {
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

        service.complete(command);

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.COMPLETED);
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

        service.fail(command, new IllegalStateException("boom"));

        assertThat(entity.getCommandStatus()).isEqualTo(IdentityCommandStatus.FAILED);
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

package vg.identity.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vg.identity.entity.IdentityMessageEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageIdempotencyConflictException;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.IdentityMessageSubmission;
import vg.identity.model.PreparedIdentityMessage;
import vg.identity.model.command.SendApplicationMessageCommand;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityMessageRepository;
import vg.identity.repository.IdentityMessageSubmissionRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Objects;
import java.util.Optional;

/**
 * Internal durable-message operations. Entry services authorize submissions and lookups before delegation;
 * the trusted command processor prepares and records delivery without rechecking sender permissions.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
class IdentityMessageService {
    private final IdentityMessageRepository messages;
    private final IdentityMessageSubmissionRepository submissions;
    private final IdentityApplicationRepository applications;
    private final IdentityApplicationUserService memberships;
    private final IdentityCommandService commands;
    private final UniqueIdService uniqueIdService;
    private final TelegramBotLookupService bots;
    private final IdentityUserChannelRepository channels;

    @Transactional
    IdentityMessageSubmission submit(UniqueId applicationId, UniqueId userId,
                                     IdentityApplicationMessageRequest request) {
        if (userId == null || request == null) {
            log.warn("Message submission rejected: reason=INVALID_ARGUMENTS, applicationUniqueId={}, userUniqueId={}",
                    applicationId, userId);
            throw new IllegalArgumentException("User and message are required");
        }
        // Serialize submissions per application without creating an independent transaction.
        applications.lockForMessageSubmission(applicationId.getLongValue())
                .orElseThrow(() -> {
                    log.warn("Message submission rejected: reason=APPLICATION_MISSING, applicationUniqueId={}, userUniqueId={}",
                            applicationId, userId);
                    return new IdentityMessageDeliveryUnavailableException();
                });
        var candidateId = uniqueIdService.getNext();
        var stored = submissions.insertOrGet(candidateId, applicationId, userId, request);
        var message = stored.message();
        if (!candidateId.equals(message.messageUniqueId())) {
            if (!Objects.equals(message.userUniqueId(), userId)
                    || !Objects.equals(stored.content(), request.content())) {
                log.warn("Message submission rejected: reason=IDEMPOTENCY_CONFLICT, applicationUniqueId={}, userUniqueId={}, messageUniqueId={}",
                        applicationId, userId, message.messageUniqueId());
                throw new IdentityMessageIdempotencyConflictException();
            }
            log.debug("Message submission replayed: applicationUniqueId={}, userUniqueId={}, messageUniqueId={}, status={}",
                    applicationId, userId, message.messageUniqueId(), message.status());
            return IdentityMessageSubmission.builder().message(message).replayed(true).build();
        }
        if (!memberships.hasMembership(applicationId, userId)) {
            log.warn("Message submission rejected: reason=MEMBERSHIP_MISSING, applicationUniqueId={}, userUniqueId={}",
                    applicationId, userId);
            throw new IdentityMessageDeliveryUnavailableException();
        }
        commands.enqueueApplicationMessage(SendApplicationMessageCommand.builder()
                .messageUniqueId(message.messageUniqueId()).build());
        return IdentityMessageSubmission.builder().message(message).replayed(false).build();
    }

    @Transactional(readOnly = true)
    Optional<IdentityApplicationMessage> find(UniqueId applicationId, UniqueId messageId) {
        return messages.findByUniqueIdAndApplicationUniqueId(messageId.getLongValue(), applicationId.getLongValue())
                .map(this::toModel);
    }

    @Transactional(readOnly = true)
    Page<IdentityApplicationMessage> findPage(UniqueId applicationId, int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Page must be nonnegative and page size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "uniqueId"));
        return messages.findByApplicationUniqueId(applicationId.getLongValue(), pageable).map(this::toModel);
    }

    /** Commits SENDING before the first network call. A later command must not start another attempt. */
    @Transactional
    Optional<PreparedIdentityMessage> begin(UniqueId messageId) {
        var message = messages.findForUpdate(messageId.getLongValue()).orElseThrow();
        if (message.getStatus() != IdentityMessageStatus.QUEUED) {
            log.debug("Message delivery skipped: reason=ATTEMPT_ALREADY_STARTED, messageUniqueId={}, status={}",
                    messageId, message.getStatus());
            return Optional.empty();
        }
        message.setStatus(IdentityMessageStatus.SENDING);
        var applicationId = new UniqueId(message.getApplicationUniqueId());
        var userId = new UniqueId(message.getUserUniqueId());
        if (!memberships.hasMembership(applicationId, userId)) {
            return unavailable(message, "MEMBERSHIP_MISSING");
        }
        try {
            var bot = bots.findMessagingBot(applicationId);
            if (bot == null) {
                return unavailable(message, "BOT_MISSING");
            }
            if (!StringUtils.hasText(bot.token())) {
                return unavailable(message, "BOT_TOKEN_MISSING");
            }
            var channel = channels.findByIdentityUser_UniqueIdAndChannelTypeOrderByUpdatedAtDesc(
                            userId.getLongValue(), IdentityChannelType.TELEGRAM_USER)
                    .stream().filter(candidate -> isUsable(candidate, userId)).findFirst().orElse(null);
            if (channel == null) {
                return unavailable(message, "ELIGIBLE_CHANNEL_MISSING");
            }
            log.debug("Message route selected: messageUniqueId={}, applicationUniqueId={}, userUniqueId={}, channelUniqueId={}",
                    messageId, applicationId, userId, Optional.ofNullable(channel.getUniqueId()).map(UniqueId::new).orElse(null));
            return Optional.of(PreparedIdentityMessage.builder().messageUniqueId(messageId).token(bot.token())
                    .chatId(Long.parseLong(channel.getChannelUserId())).text(message.getContent().text()).build());
        } catch (IllegalArgumentException e) {
            // Bot decoding errors are sanitized and do not poison the transaction.
            log.warn("Message preparation failed: reason=INVALID_ROUTE_CONFIGURATION, messageUniqueId={}, applicationUniqueId={}, userUniqueId={}",
                    messageId, applicationId, userId);
            return Optional.of(PreparedIdentityMessage.builder().messageUniqueId(messageId)
                    .failureCode(IdentityMessageFailureCode.INTERNAL_ERROR).build());
        }
    }

    /** Records only the message outcome; the worker finalizes the command separately. */
    @Transactional
    void finish(UniqueId messageId, IdentityMessageStatus status,
                IdentityMessageFailureCode failureCode) {
        if (status != IdentityMessageStatus.SENT && status != IdentityMessageStatus.FAILED && status != IdentityMessageStatus.UNKNOWN) {
            log.warn("Message outcome rejected: reason=NONTERMINAL_OUTCOME, messageUniqueId={}, status={}", messageId, status);
            throw new IllegalArgumentException("A final message outcome is required");
        }
        var validOutcome = switch (status) {
            case SENT -> failureCode == null;
            case FAILED -> failureCode != null && failureCode != IdentityMessageFailureCode.DELIVERY_UNKNOWN;
            case UNKNOWN -> failureCode == IdentityMessageFailureCode.DELIVERY_UNKNOWN;
            default -> false;
        };
        if (!validOutcome) {
            log.warn("Message outcome rejected: reason=FAILURE_CODE_MISMATCH, messageUniqueId={}, status={}, failureCode={}",
                    messageId, status, failureCode);
            throw new IllegalArgumentException("Failure code must match the message outcome");
        }
        var message = messages.findForUpdate(messageId.getLongValue()).orElseThrow();
        if (message.getStatus() != IdentityMessageStatus.SENDING) {
            log.warn("Message outcome rejected: reason=MESSAGE_NOT_SENDING, messageUniqueId={}, currentStatus={}, requestedStatus={}",
                    messageId, message.getStatus(), status);
            throw new IllegalStateException("Message must be SENDING");
        }
        message.setStatus(status);
        message.setFailureCode(failureCode);
        log.debug("Message outcome applied: messageUniqueId={}, status={}, failureCode={}", messageId, status, failureCode);
    }

    private Optional<PreparedIdentityMessage> unavailable(IdentityMessageEntity message, String reason) {
        var messageId = new UniqueId(message.getUniqueId());
        log.warn("Message delivery unavailable: reason={}, messageUniqueId={}, applicationUniqueId={}, userUniqueId={}",
                reason, messageId, new UniqueId(message.getApplicationUniqueId()), new UniqueId(message.getUserUniqueId()));
        return Optional.of(PreparedIdentityMessage.builder().messageUniqueId(messageId)
                .failureCode(IdentityMessageFailureCode.DELIVERY_UNAVAILABLE).build());
    }

    private boolean isUsable(IdentityUserChannelEntity channel, UniqueId userId) {
        var user = channel.getIdentityUser();
        var channelId = Optional.ofNullable(channel.getUniqueId()).map(UniqueId::new).orElse(null);
        if (channel.getChannelType() != IdentityChannelType.TELEGRAM_USER) {
            log.warn("Message channel skipped: reason=CHANNEL_IDENTITY_MISMATCH, userUniqueId={}, channelUniqueId={}",
                    userId, channelId);
            return false;
        }
        if (channel.getVerifiedAt() == null) {
            log.warn("Message channel skipped: reason=CHANNEL_UNVERIFIED, userUniqueId={}, channelUniqueId={}",
                    userId, channelId);
            return false;
        }
        if (user == null || !Objects.equals(user.getUniqueId(), userId.getLongValue())) {
            log.warn("Message channel skipped: reason=CHANNEL_IDENTITY_MISMATCH, userUniqueId={}, channelUniqueId={}",
                    userId, channelId);
            return false;
        }
        if (user.getConsentToKeepPersonalDataAt() == null) {
            log.warn("Message channel skipped: reason=CONSENT_MISSING, userUniqueId={}, channelUniqueId={}",
                    userId, channelId);
            return false;
        }
        try {
            if (Long.parseLong(channel.getChannelUserId()) > 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Report only the reason; channelUserId and exception text can contain contact data.
        }
        log.warn("Message channel skipped: reason=INVALID_TELEGRAM_RECIPIENT, userUniqueId={}, channelUniqueId={}",
                userId, channelId);
        return false;
    }

    private IdentityApplicationMessage toModel(IdentityMessageEntity message) {
        return IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(message.getUniqueId()))
                .userUniqueId(new UniqueId(message.getUserUniqueId())).status(message.getStatus())
                .createdAt(message.getCreatedAt()).updatedAt(message.getUpdatedAt()).failureCode(message.getFailureCode()).build();
    }
}

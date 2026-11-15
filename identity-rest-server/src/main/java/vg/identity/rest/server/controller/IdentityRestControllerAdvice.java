package vg.identity.rest.server.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.IdentityMessageIdempotencyConflictException;

/** Shared error mapping for Identity REST controllers, scoped away from a host application's UI. */
@RestControllerAdvice(basePackageClasses = IdentityApplicationController.class)
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class IdentityRestControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidArgument() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request argument");
    }

    @ExceptionHandler(IdentityMessageIdempotencyConflictException.class)
    public ProblemDetail messageIdempotencyConflict() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Idempotency key is already used for a different message");
        problem.setProperty("code", "MESSAGE_IDEMPOTENCY_CONFLICT");
        return problem;
    }

    @ExceptionHandler(IdentityMessageDeliveryUnavailableException.class)
    public ProblemDetail messageDeliveryUnavailable() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Message delivery is unavailable");
        problem.setProperty("code", "MESSAGE_DELIVERY_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail invalidRequestBody() {
        // Resolve here: servlet error dispatch can otherwise enter the browser security chain.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
    }
}

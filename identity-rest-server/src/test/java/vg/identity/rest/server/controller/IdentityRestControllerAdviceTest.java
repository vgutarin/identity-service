package vg.identity.rest.server.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRestControllerAdviceTest {
    @Test
    void invalidArgument_whenHandled_returnsGenericBadRequestProblem() {
        var problem = new IdentityRestControllerAdvice().invalidArgument();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Invalid request argument");
        assertThat(problem.getProperties()).isNull();
    }

    @Test
    void invalidRequestBody_whenHandled_returnsGenericBadRequestProblem() {
        var problem = new IdentityRestControllerAdvice().invalidRequestBody();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Invalid request body");
        assertThat(problem.getProperties()).isNull();
    }

    @Test
    void messageIdempotencyConflict_whenHandled_returnsConflictWithStableCode() {
        var problem = new IdentityRestControllerAdvice().messageIdempotencyConflict();

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Idempotency key is already used for a different message");
        assertThat(problem.getProperties()).containsEntry("code", "MESSAGE_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void messageDeliveryUnavailable_whenHandled_returnsNeutralUnavailabilityWithStableCode() {
        var problem = new IdentityRestControllerAdvice().messageDeliveryUnavailable();

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getDetail()).isEqualTo("Message delivery is unavailable");
        assertThat(problem.getProperties()).containsEntry("code", "MESSAGE_DELIVERY_UNAVAILABLE");
    }
}

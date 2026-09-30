package vg.identity.frontend.vaadin.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramWebhookUiTasksTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void execute_capturesSubmittingCallerAndClearsAnEmptyWorkerContext() {
        var queue = new ArrayDeque<Runnable>();
        var tasks = new TelegramWebhookUiTasks(queue::add);
        var caller = UsernamePasswordAuthenticationToken.authenticated("caller", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);
        var observed = new AtomicReference<Object>();
        tasks.execute(() -> observed.set(SecurityContextHolder.getContext().getAuthentication()));
        SecurityContextHolder.clearContext();

        queue.remove().run();

        assertThat(observed.get()).isSameAs(caller);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void execute_restoresPreviousWorkerContextEvenOnFailure() {
        var queue = new ArrayDeque<Runnable>();
        var tasks = new TelegramWebhookUiTasks(queue::add);
        var caller = UsernamePasswordAuthenticationToken.authenticated("caller", null, java.util.List.of());
        var previous = UsernamePasswordAuthenticationToken.authenticated("previous", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);
        tasks.execute(() -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(caller);
            throw new IllegalStateException("failure");
        });
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(previous);
        assertThatThrownBy(() -> queue.remove().run()).isInstanceOf(IllegalStateException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }
}

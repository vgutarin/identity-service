package vg.identity.frontend.vaadin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;

/** Captures authentication at submission and restores/clears the worker's context after each task. */
@RequiredArgsConstructor
public class TelegramWebhookUiTasks {
    private final Executor executor;

    public void execute(Runnable task) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        executor.execute(new DelegatingSecurityContextRunnable(task, context));
    }
}

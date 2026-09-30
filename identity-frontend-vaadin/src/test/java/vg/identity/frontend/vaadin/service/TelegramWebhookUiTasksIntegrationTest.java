package vg.identity.frontend.vaadin.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import vg.identity.frontend.vaadin.config.TelegramWebhookUiConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig({TelegramWebhookUiConfiguration.class, TelegramWebhookUiTasksIntegrationTest.Config.class})
class TelegramWebhookUiTasksIntegrationTest {
    @Autowired private TelegramWebhookUiTasks tasks;
    @Autowired private ThreadPoolTaskExecutor telegramWebhookExecutor;
    @Autowired private SecuredProbe probe;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void springManagedWorkers_propagateAuthorizationAndDoNotLeakItToNextTask() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("app.read"))));
        var result = new CompletableFuture<String>();
        tasks.execute(() -> {
            try {
                result.complete(probe.inspect());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("admin");
        // Direct, unwrapped submissions must not inherit the preceding task's caller.
        var cleared = new CompletableFuture<Boolean>();
        telegramWebhookExecutor.execute(() -> cleared.complete(SecurityContextHolder.getContext().getAuthentication() == null));
        assertThat(cleared.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(telegramWebhookExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(telegramWebhookExecutor.getMaxPoolSize()).isEqualTo(2);
        assertThat(telegramWebhookExecutor.getQueueCapacity()).isEqualTo(100);
    }

    @Test
    void saturatedExecutor_rejectsWithoutRunningWorkOnSubmittingThread() throws Exception {
        var executor = new TelegramWebhookUiConfiguration().telegramWebhookExecutor();
        executor.initialize();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100; i++) {
                executor.execute(() -> { });
            }
            assertThatThrownBy(() -> executor.execute(() -> { throw new AssertionError("must not run"); }))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean SecuredProbe securedProbe() { return new SecuredProbe(); }
    }

    static class SecuredProbe {
        @PreAuthorize("hasAuthority('app.read')")
        public String inspect() {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
    }
}

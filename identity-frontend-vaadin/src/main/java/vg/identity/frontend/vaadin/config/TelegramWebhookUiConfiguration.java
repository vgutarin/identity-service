package vg.identity.frontend.vaadin.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import vg.identity.frontend.vaadin.service.TelegramWebhookUiTasks;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class TelegramWebhookUiConfiguration {
    @Bean
    public ThreadPoolTaskExecutor telegramWebhookExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("telegram-webhook-ui-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public TelegramWebhookUiTasks telegramWebhookUiTasks(@Qualifier("telegramWebhookExecutor") Executor executor) {
        return new TelegramWebhookUiTasks(executor);
    }
}

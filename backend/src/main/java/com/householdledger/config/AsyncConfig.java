package com.householdledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Backs @Async methods (NotificationEventListener) with a real bounded thread
 * pool instead of Spring's default SimpleAsyncTaskExecutor, which spins up an
 * unbounded number of brand-new threads on demand - fine for a quick demo,
 * a real liability under any actual load. Naming the threads also makes them
 * identifiable in thread dumps instead of showing up as anonymous pool-N-thread-M.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }
}
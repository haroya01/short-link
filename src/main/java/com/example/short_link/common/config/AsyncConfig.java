package com.example.short_link.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  /**
   * Default executor for everything not explicitly routed elsewhere — OG fetch, API key revocation
   * audit logs, etc. DiscardOldest is fine here because every consumer treats it as best-effort.
   */
  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("kurl-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return executor;
  }

  /**
   * Dedicated pool for outgoing webhook POSTs. Click-rate spikes (e.g., one link goes viral) shared
   * the default pool with OG-fetch / preview scrape, and the DiscardOldest policy would silently
   * drop click webhooks before they fired. Here we give webhooks their own headroom and fall back
   * to CallerRuns under saturation — the click thread will block for the HTTP timeout (5s) which is
   * acceptable backpressure compared to dropping events the user explicitly opted into.
   */
  @Bean(name = "webhookExecutor")
  public Executor webhookExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("kurl-webhook-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return executor;
  }
}

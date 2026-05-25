package com.example.short_link.link.og.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.application.LinkOgFetchListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

class OgFetchRetryJobTest {

  private LinkRepository linkRepository;
  private LinkOgFetchListener listener;
  private RedisDistributedLock lock;
  private OgFetchRetryJob job;

  @BeforeEach
  void setUp() {
    linkRepository = Mockito.mock(LinkRepository.class);
    listener = Mockito.mock(LinkOgFetchListener.class);
    lock = Mockito.mock(RedisDistributedLock.class);
    job =
        new OgFetchRetryJob(
            linkRepository,
            listener,
            lock,
            new SimpleMeterRegistry(),
            new OgFetchProperties(3, 30, true));
  }

  @Test
  void lockHeldSkips() {
    when(lock.tryAcquire(eq("kurl:og-fetch:retry"), any(Duration.class))).thenReturn(false);
    job.runDaily();
    verify(linkRepository, never()).findOgRetryCandidates(anyInt(), any(), any());
  }

  @Test
  void processesCandidates() {
    when(lock.tryAcquire(eq("kurl:og-fetch:retry"), any(Duration.class))).thenReturn(true);
    LinkEntity a = Mockito.mock(LinkEntity.class);
    when(a.getShortCode()).thenReturn("r000001");
    when(a.getOriginalUrl()).thenReturn("https://r.example");
    when(linkRepository.findOgRetryCandidates(eq(3), any(), any(Pageable.class)))
        .thenReturn(List.of(a));

    job.runDaily();

    verify(listener).fetchAndStore("r000001", "https://r.example");
    verify(lock).release("kurl:og-fetch:retry");
  }

  @Test
  void perCandidateFailureContinues() {
    when(lock.tryAcquire(eq("kurl:og-fetch:retry"), any(Duration.class))).thenReturn(true);
    LinkEntity a = Mockito.mock(LinkEntity.class);
    LinkEntity b = Mockito.mock(LinkEntity.class);
    when(a.getShortCode()).thenReturn("a000001");
    when(a.getOriginalUrl()).thenReturn("https://a.example");
    when(b.getShortCode()).thenReturn("b000001");
    when(b.getOriginalUrl()).thenReturn("https://b.example");
    when(linkRepository.findOgRetryCandidates(anyInt(), any(), any(Pageable.class)))
        .thenReturn(List.of(a, b));
    Mockito.doThrow(new RuntimeException("boom"))
        .when(listener)
        .fetchAndStore(eq("a000001"), anyString());

    job.runDaily();

    verify(listener, times(1)).fetchAndStore("b000001", "https://b.example");
    verify(lock).release("kurl:og-fetch:retry");
  }
}

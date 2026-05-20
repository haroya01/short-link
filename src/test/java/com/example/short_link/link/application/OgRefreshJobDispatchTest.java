package com.example.short_link.link.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

class OgRefreshJobDispatchTest {

  private LinkRepository linkRepository;
  private LinkOgFetchListener listener;
  private RedisDistributedLock lock;
  private OgRefreshJob job;

  @BeforeEach
  void setUp() throws Exception {
    linkRepository = Mockito.mock(LinkRepository.class);
    listener = Mockito.mock(LinkOgFetchListener.class);
    lock = Mockito.mock(RedisDistributedLock.class);
    job = new OgRefreshJob(linkRepository, listener, lock, new SimpleMeterRegistry());
    setField("staleAfterDays", 30L);
    setField("enabled", true);
  }

  private void setField(String name, Object value) throws Exception {
    Field f = OgRefreshJob.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(job, value);
  }

  @Test
  void disabledSkipsAll() throws Exception {
    setField("enabled", false);
    job.runWeekly();
    verify(lock, never()).tryAcquire(anyString(), any(Duration.class));
  }

  @Test
  void lockHeldSkipsWork() {
    when(lock.tryAcquire(eq("kurl:og-fetch:refresh"), any(Duration.class))).thenReturn(false);
    job.runWeekly();
    verify(linkRepository, never()).findStaleOgCandidates(any(), any());
  }

  @Test
  void processesCandidatesAndReleasesLock() {
    when(lock.tryAcquire(eq("kurl:og-fetch:refresh"), any(Duration.class))).thenReturn(true);
    LinkEntity a = Mockito.mock(LinkEntity.class);
    LinkEntity b = Mockito.mock(LinkEntity.class);
    when(a.getShortCode()).thenReturn("a000001");
    when(a.getOriginalUrl()).thenReturn("https://a.example");
    when(b.getShortCode()).thenReturn("b000001");
    when(b.getOriginalUrl()).thenReturn("https://b.example");
    when(linkRepository.findStaleOgCandidates(any(), any(Pageable.class)))
        .thenReturn(List.of(a, b));

    job.runWeekly();

    verify(listener).fetchAndStore("a000001", "https://a.example");
    verify(listener).fetchAndStore("b000001", "https://b.example");
    verify(lock).release("kurl:og-fetch:refresh");
  }

  @Test
  void perCandidateFailureContinues() {
    when(lock.tryAcquire(eq("kurl:og-fetch:refresh"), any(Duration.class))).thenReturn(true);
    LinkEntity a = Mockito.mock(LinkEntity.class);
    LinkEntity b = Mockito.mock(LinkEntity.class);
    when(a.getShortCode()).thenReturn("a000001");
    when(a.getOriginalUrl()).thenReturn("https://a.example");
    when(b.getShortCode()).thenReturn("b000001");
    when(b.getOriginalUrl()).thenReturn("https://b.example");
    when(linkRepository.findStaleOgCandidates(any(), any(Pageable.class)))
        .thenReturn(List.of(a, b));
    Mockito.doThrow(new RuntimeException("boom"))
        .when(listener)
        .fetchAndStore(eq("a000001"), anyString());

    job.runWeekly();

    verify(listener, times(1)).fetchAndStore("b000001", "https://b.example");
    verify(lock).release("kurl:og-fetch:refresh");
  }
}

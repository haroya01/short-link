package com.example.short_link.note.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NotePollService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotePollCloseJobTest {

  @Mock private NotePollService polls;
  @Mock private RedisDistributedLock lock;

  @Test
  void aTickClosesDuePollsUnderTheLockAndReleasesIt() {
    when(lock.tryAcquire(eq("kurl:note:poll-close"), any())).thenReturn(true);
    when(polls.closeDue()).thenReturn(2, 0);
    NotePollCloseJob job = new NotePollCloseJob(polls, lock);

    job.tick();
    job.tick();

    verify(lock, org.mockito.Mockito.times(2)).release("kurl:note:poll-close");
  }

  @Test
  void anotherNodeHoldingTheLockSkipsTheTick() {
    when(lock.tryAcquire(eq("kurl:note:poll-close"), any())).thenReturn(false);

    new NotePollCloseJob(polls, lock).tick();

    verifyNoInteractions(polls);
    verify(lock, never()).release(any());
  }
}

package com.example.short_link.note.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NoteScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteScheduleJobTest {

  @Mock private NoteScheduleService schedules;
  @Mock private RedisDistributedLock lock;

  @Test
  void aTickPostsDueNotesUnderTheLockAndReleasesIt() {
    when(lock.tryAcquire(eq("kurl:note:schedule"), any())).thenReturn(true);
    when(schedules.publishDue()).thenReturn(2, 0);
    NoteScheduleJob job = new NoteScheduleJob(schedules, lock);

    job.tick();
    job.tick();

    verify(lock, org.mockito.Mockito.times(2)).release("kurl:note:schedule");
  }

  @Test
  void anotherNodeHoldingTheLockSkipsTheTick() {
    when(lock.tryAcquire(eq("kurl:note:schedule"), any())).thenReturn(false);

    new NoteScheduleJob(schedules, lock).tick();

    verifyNoInteractions(schedules);
    verify(lock, never()).release(any());
  }
}

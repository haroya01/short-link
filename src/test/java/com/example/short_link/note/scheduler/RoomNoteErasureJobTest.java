package com.example.short_link.note.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.RoomNoteErasures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomNoteErasureJobTest {

  @Mock private RoomNoteErasures erasures;
  @Mock private RedisDistributedLock lock;

  @Test
  void aTickErasesUnderTheLockAndReleasesIt() {
    when(lock.tryAcquire(eq("kurl:note:room-erasure"), any())).thenReturn(true);
    when(erasures.eraseDue()).thenReturn(50, 0);
    RoomNoteErasureJob job = new RoomNoteErasureJob(erasures, lock);

    job.tick();
    job.tick();

    verify(erasures, times(2)).eraseDue();
    verify(lock, times(2)).release("kurl:note:room-erasure");
  }

  @Test
  void anotherNodeHoldingTheLockSkipsTheTick() {
    when(lock.tryAcquire(eq("kurl:note:room-erasure"), any())).thenReturn(false);

    new RoomNoteErasureJob(erasures, lock).tick();

    verifyNoInteractions(erasures);
    verify(lock, never()).release(any());
  }
}

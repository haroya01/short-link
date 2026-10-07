package com.example.short_link.portability.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.portability.application.AccountImportService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountImportJobTest {

  @Mock private AccountImportService imports;
  @Mock private RedisDistributedLock lock;

  @Test
  void theHolderOfTheLockAppliesABatchAndLetsGo() {
    when(lock.tryAcquire(AccountImportJob.LOCK_KEY, Duration.ofSeconds(9))).thenReturn(true);

    new AccountImportJob(imports, lock).tick();

    verify(imports).processBatch(AccountImportJob.BATCH);
    verify(lock).release(AccountImportJob.LOCK_KEY);
  }

  @Test
  void anotherWorkerHoldingTheLockMeansThisOneWaits() {
    when(lock.tryAcquire(AccountImportJob.LOCK_KEY, Duration.ofSeconds(9))).thenReturn(false);

    new AccountImportJob(imports, lock).tick();

    verifyNoInteractions(imports);
  }
}

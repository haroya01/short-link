package com.example.short_link.customdomain.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.customdomain.application.write.AutoVerifyCustomDomainUseCase;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomDomainAutoVerifyJobTest {

  private CustomDomainQueryService queryService;
  private AutoVerifyCustomDomainUseCase autoVerify;
  private CustomDomainAutoVerifyJob job;

  @BeforeEach
  void setUp() {
    queryService = Mockito.mock(CustomDomainQueryService.class);
    autoVerify = Mockito.mock(AutoVerifyCustomDomainUseCase.class);
    job = new CustomDomainAutoVerifyJob(queryService, autoVerify);
  }

  @Test
  void noPendingShortcuts() {
    when(queryService.findPendingWithinWindow()).thenReturn(List.of());
    job.run();
    verify(autoVerify, never()).execute(any());
  }

  @Test
  void verifiesPendingAndCountsSuccesses() {
    CustomDomainEntity a = Mockito.mock(CustomDomainEntity.class);
    CustomDomainEntity b = Mockito.mock(CustomDomainEntity.class);
    when(a.getDomain()).thenReturn("a.example.com");
    when(b.getDomain()).thenReturn("b.example.com");
    when(queryService.findPendingWithinWindow()).thenReturn(List.of(a, b));
    when(autoVerify.execute(a)).thenReturn(true);
    when(autoVerify.execute(b)).thenReturn(false);

    job.run();

    verify(autoVerify, times(2)).execute(any());
  }

  @Test
  void perCandidateFailureLoggedAndContinues() {
    CustomDomainEntity a = Mockito.mock(CustomDomainEntity.class);
    CustomDomainEntity b = Mockito.mock(CustomDomainEntity.class);
    when(a.getDomain()).thenReturn("boom.example.com");
    when(b.getDomain()).thenReturn("ok.example.com");
    when(queryService.findPendingWithinWindow()).thenReturn(List.of(a, b));
    when(autoVerify.execute(a)).thenThrow(new RuntimeException("dns timeout"));
    when(autoVerify.execute(b)).thenReturn(true);

    job.run();

    verify(autoVerify).execute(b);
  }
}

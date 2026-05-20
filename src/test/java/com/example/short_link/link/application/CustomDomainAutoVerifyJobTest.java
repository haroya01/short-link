package com.example.short_link.link.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.CustomDomainEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomDomainAutoVerifyJobTest {

  private CustomDomainService service;
  private CustomDomainAutoVerifyJob job;

  @BeforeEach
  void setUp() {
    service = Mockito.mock(CustomDomainService.class);
    job = new CustomDomainAutoVerifyJob(service);
  }

  @Test
  void noPendingShortcuts() {
    when(service.findPendingWithinWindow()).thenReturn(List.of());
    job.run();
    verify(service, never()).autoVerifyOne(any());
  }

  @Test
  void verifiesPendingAndCountsSuccesses() {
    CustomDomainEntity a = Mockito.mock(CustomDomainEntity.class);
    CustomDomainEntity b = Mockito.mock(CustomDomainEntity.class);
    when(a.getDomain()).thenReturn("a.example.com");
    when(b.getDomain()).thenReturn("b.example.com");
    when(service.findPendingWithinWindow()).thenReturn(List.of(a, b));
    when(service.autoVerifyOne(a)).thenReturn(true);
    when(service.autoVerifyOne(b)).thenReturn(false);

    job.run();

    verify(service, times(2)).autoVerifyOne(any());
  }

  @Test
  void perCandidateFailureLoggedAndContinues() {
    CustomDomainEntity a = Mockito.mock(CustomDomainEntity.class);
    CustomDomainEntity b = Mockito.mock(CustomDomainEntity.class);
    when(a.getDomain()).thenReturn("boom.example.com");
    when(b.getDomain()).thenReturn("ok.example.com");
    when(service.findPendingWithinWindow()).thenReturn(List.of(a, b));
    when(service.autoVerifyOne(a)).thenThrow(new RuntimeException("dns timeout"));
    when(service.autoVerifyOne(b)).thenReturn(true);

    job.run();

    verify(service).autoVerifyOne(b);
  }
}

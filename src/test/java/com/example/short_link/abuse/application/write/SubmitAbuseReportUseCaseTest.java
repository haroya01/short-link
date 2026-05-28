package com.example.short_link.abuse.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitAbuseReportUseCaseTest {

  @Mock private AbuseReportRepository abuseReportRepository;

  private SubmitAbuseReportUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SubmitAbuseReportUseCase(abuseReportRepository);
  }

  @Test
  void submitsReportWithReporter() {
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity saved =
        useCase.execute(new SubmitAbuseReportCommand(7L, AbuseSubjectType.POST, 42L, "spam"));

    assertThat(saved.getReporterUserId()).isEqualTo(7L);
    assertThat(saved.getSubjectType()).isEqualTo(AbuseSubjectType.POST);
    assertThat(saved.getSubjectId()).isEqualTo(42L);
    assertThat(saved.getReason()).isEqualTo("spam");
  }

  @Test
  void submitsAnonymousReport() {
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity saved =
        useCase.execute(new SubmitAbuseReportCommand(null, AbuseSubjectType.USER, 9L, null));

    assertThat(saved.getReporterUserId()).isNull();
    assertThat(saved.getReason()).isNull();
  }
}

package com.example.short_link.abuse.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolveAbuseReportUseCaseTest {

  @Mock private AbuseReportRepository abuseReportRepository;

  private ResolveAbuseReportUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ResolveAbuseReportUseCase(abuseReportRepository);
  }

  private AbuseReportEntity openReport() {
    return new AbuseReportEntity(7L, AbuseSubjectType.POST, 42L, "spam");
  }

  @Test
  void resolveTransitionsToResolved() {
    AbuseReportEntity r = openReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity result =
        useCase.execute(
            new ResolveAbuseReportCommand(
                1L, ResolveAbuseReportCommand.Resolution.RESOLVED, "removed"));

    assertThat(result.getStatus()).isEqualTo(AbuseReportStatus.RESOLVED);
    assertThat(result.getResolvedAt()).isNotNull();
  }

  @Test
  void reviewingDoesNotStampResolved() {
    AbuseReportEntity r = openReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity result =
        useCase.execute(
            new ResolveAbuseReportCommand(
                1L, ResolveAbuseReportCommand.Resolution.REVIEWING, "checking"));

    assertThat(result.getStatus()).isEqualTo(AbuseReportStatus.REVIEWING);
    assertThat(result.getResolvedAt()).isNull();
  }

  @Test
  void rejectsResolveOfAlreadyResolved() {
    AbuseReportEntity r = openReport();
    r.resolve("done");
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ResolveAbuseReportCommand(
                        1L, ResolveAbuseReportCommand.Resolution.RESOLVED, null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.ALREADY_RESOLVED);
  }

  @Test
  void notFoundThrows() {
    when(abuseReportRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ResolveAbuseReportCommand(
                        99L, ResolveAbuseReportCommand.Resolution.RESOLVED, null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.ABUSE_REPORT_NOT_FOUND);
  }
}

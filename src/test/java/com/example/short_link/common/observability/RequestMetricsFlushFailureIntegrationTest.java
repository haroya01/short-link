package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.short_link.support.FailingSql;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingSql.Config.class)
@Tag("integration")
class RequestMetricsFlushFailureIntegrationTest {

  @Autowired private FailingSql sql;
  @Autowired private RequestMetricsRecorder recorder;
  @Autowired private MeterRegistry meters;

  @AfterEach
  void stopRefusing() {
    sql.reset();
  }

  private double flushErrors() {
    return meters.counter("request_metrics.recorder", "result", "flush_error").count();
  }

  @Test
  void aFlushThatCannotBeWrittenIsCountedAndDoesNotThrow() {
    sql.refuse(
        "insert into request_metrics",
        () -> new SQLException("Got error 1 from storage engine", "HY000", 1030));
    double before = flushErrors();
    recorder.record(
        new RequestMetric(Instant.now(), "/api/v1/links", "GET", 200, "ok", 3, null, null, null));

    assertThatCode(recorder::flush).doesNotThrowAnyException();

    assertThat(sql.refused()).isNotEmpty();
    assertThat(flushErrors()).isGreaterThan(before);
  }
}

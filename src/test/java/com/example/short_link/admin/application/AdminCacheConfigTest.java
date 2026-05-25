package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.application.dto.AdminActiveUsers;
import com.example.short_link.admin.application.dto.AdminCohort;
import com.example.short_link.admin.application.dto.AdminLifecycle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Locks in the @class typing policy that lets the admin-overview cache survive a deserialize
 * round-trip with record return types. NON_FINAL typing skipped these records and they came back as
 * LinkedHashMap, which surfaced in prod as a Cacheable cast failure.
 */
class AdminCacheConfigTest {

  private final GenericJackson2JsonRedisSerializer serializer =
      new GenericJackson2JsonRedisSerializer(AdminCacheConfig.buildOverviewObjectMapper());

  @Test
  void serializedCohortCarriesAtClassOnRecord() {
    AdminCohort sample =
        new AdminCohort(
            4,
            List.of(
                new AdminCohort.Row(
                    "2026-W20",
                    3L,
                    List.of(new AdminCohort.Cell(0, 3L, 1.0), new AdminCohort.Cell(1, 2L, 0.66)))));

    String json = new String(serializer.serialize(sample));

    assertThat(json)
        .contains("\"@class\":\"com.example.short_link.admin.application.dto.AdminCohort\"");
    assertThat(json)
        .contains("\"@class\":\"com.example.short_link.admin.application.dto.AdminCohort$Row\"");
    assertThat(json)
        .contains("\"@class\":\"com.example.short_link.admin.application.dto.AdminCohort$Cell\"");
  }

  @Test
  void cohortRoundTripPreservesValue() {
    AdminCohort sample =
        new AdminCohort(
            4,
            List.of(
                new AdminCohort.Row(
                    "2026-W20",
                    3L,
                    List.of(new AdminCohort.Cell(0, 3L, 1.0), new AdminCohort.Cell(1, 2L, 0.66)))));

    Object restored = serializer.deserialize(serializer.serialize(sample));

    assertThat(restored).isInstanceOf(AdminCohort.class);
    assertThat(restored).isEqualTo(sample);
  }

  @Test
  void lifecycleRoundTripPreservesValue() {
    AdminLifecycle sample =
        new AdminLifecycle(
            7,
            List.of(
                new AdminLifecycle.DayPoint(0, 12L, 4L), new AdminLifecycle.DayPoint(1, 5L, 2L)));

    Object restored = serializer.deserialize(serializer.serialize(sample));

    assertThat(restored).isInstanceOf(AdminLifecycle.class);
    assertThat(restored).isEqualTo(sample);
  }

  @Test
  void activeUsersRoundTripPreservesValue() {
    AdminActiveUsers sample =
        new AdminActiveUsers(
            "day",
            List.of(
                new AdminActiveUsers.Bucket("2026-05-19", 7L),
                new AdminActiveUsers.Bucket("2026-05-20", 9L)));

    Object restored = serializer.deserialize(serializer.serialize(sample));

    assertThat(restored).isInstanceOf(AdminActiveUsers.class);
    assertThat(restored).isEqualTo(sample);
  }
}

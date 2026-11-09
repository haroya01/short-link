package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.ShortLinkApplication;
import com.example.short_link.link.stats.domain.ClickRollup;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ShortLinkApplication.class)
@ActiveProfiles("test")
@Tag("integration")
class ClickRegionCodeBackfillIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  private final long linkId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L);

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM click_daily_dim WHERE link_id = ?", linkId);
  }

  @Test
  void everyJapaneseAndKoreanSubdivisionGeoLite2NamesGetsItsCode() throws Exception {
    List<String[]> subdivisions =
        new ClassPathResource("geoip/geolite2-jp-kr-subdivisions.csv")
            .getContentAsString(StandardCharsets.UTF_8)
            .lines()
            .skip(1)
            .map(line -> line.split(","))
            .toList();
    long[] ids = new long[subdivisions.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = click(subdivisions.get(i)[0], subdivisions.get(i)[1], null);
    }

    backfill();

    assertThat(subdivisions).hasSize(64);
    for (int i = 0; i < ids.length; i++) {
      assertThat(code(ids[i])).as(subdivisions.get(i)[1]).isEqualTo(subdivisions.get(i)[2]);
    }
  }

  @Test
  void onlyClicksWithoutACodeFromTheSameCountryAreFilled() throws Exception {
    long hyogo = click("JP", "Hyogo", null);
    long otherCountry = click("US", "Tokyo", null);
    long coded = click("JP", "Tokyo", "JP-99");
    long unknown = click("JP", "Tokyo-to", null);
    long elsewhere = click("US", "California", null);

    backfill();

    assertThat(code(hyogo)).isEqualTo("JP-28");
    assertThat(code(otherCountry)).isNull();
    assertThat(code(coded)).isEqualTo("JP-99");
    assertThat(code(unknown)).isNull();
    assertThat(code(elsewhere)).isNull();
  }

  @Test
  void rollupRowsAreMatchedByNameOnTheRegionDimensionOnly() throws Exception {
    dailyRow(ClickRollup.REGION, "Kanagawa", null);
    dailyRow(ClickRollup.REGION, "Gyeonggi-do", null);
    dailyRow(ClickRollup.REGION, "Osaka", "JP-99");
    dailyRow(ClickRollup.REGION, "California", null);
    dailyRow(ClickRollup.CITY, "Tokyo", null);

    backfill();

    assertThat(dailyCode(ClickRollup.REGION, "Kanagawa")).isEqualTo("JP-14");
    assertThat(dailyCode(ClickRollup.REGION, "Gyeonggi-do")).isEqualTo("KR-41");
    assertThat(dailyCode(ClickRollup.REGION, "Osaka")).isEqualTo("JP-99");
    assertThat(dailyCode(ClickRollup.REGION, "California")).isNull();
    assertThat(dailyCode(ClickRollup.CITY, "Tokyo")).isNull();
  }

  private void backfill() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V211__click_region_code_backfill.sql"));
    }
  }

  private long click(String country, String region, String regionCode) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement insert =
              connection.prepareStatement(
                  "INSERT INTO click_event (link_id, clicked_at, is_bot, country_code, region_name,"
                      + " region_code) VALUES (?, FROM_UNIXTIME(86400), 0, ?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          insert.setLong(1, linkId);
          insert.setString(2, country);
          insert.setString(3, region);
          insert.setString(4, regionCode);
          return insert;
        },
        key);
    return key.getKey().longValue();
  }

  private String code(long clickId) {
    return jdbc.queryForObject(
        "SELECT region_code FROM click_event WHERE id = ?", String.class, clickId);
  }

  private void dailyRow(int dim, String value, String regionCode) {
    jdbc.update(
        "INSERT INTO click_daily_dim (link_id, dim, day_start, is_bot, value_key, value,"
            + " region_code, clicks, first_at) VALUES (?, ?, FROM_UNIXTIME(86400), 0,"
            + " UNHEX(MD5(?)), ?, ?, 1, FROM_UNIXTIME(86400))",
        linkId,
        dim,
        value,
        value,
        regionCode);
  }

  private String dailyCode(int dim, String value) {
    return jdbc.queryForObject(
        "SELECT region_code FROM click_daily_dim WHERE link_id = ? AND dim = ? AND value = ?",
        String.class,
        linkId,
        dim,
        value);
  }
}

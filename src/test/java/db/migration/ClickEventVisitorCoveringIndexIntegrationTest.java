package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.ShortLinkApplication;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ShortLinkApplication.class)
@ActiveProfiles("test")
class ClickEventVisitorCoveringIndexIntegrationTest {

  private static final String NEW_INDEX = "idx_click_event_link_bot_visitor";
  private static final String OLD_INDEX = "idx_click_event_visitor";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void leaveTheMigratedIndexes() throws Exception {
    migrate();
    assertThat(indexes()).contains(NEW_INDEX).doesNotContain(OLD_INDEX);
  }

  @Test
  void swapsTheOldIndexForTheCoveringOne() throws Exception {
    jdbc.execute("ALTER TABLE click_event DROP INDEX " + NEW_INDEX);
    jdbc.execute("ALTER TABLE click_event ADD INDEX " + OLD_INDEX + " (link_id, visitor_hash)");

    migrate();

    assertThat(indexes()).contains(NEW_INDEX).doesNotContain(OLD_INDEX);
    assertThat(columns(NEW_INDEX))
        .containsExactly("link_id", "is_bot", "visitor_hash", "clicked_at", "referrer_host");
  }

  @Test
  void onlyDropsTheOldIndexWhenTheCoveringOneWasBuiltBeforehand() throws Exception {
    jdbc.execute("ALTER TABLE click_event ADD INDEX " + OLD_INDEX + " (link_id, visitor_hash)");

    migrate();

    assertThat(indexes()).contains(NEW_INDEX).doesNotContain(OLD_INDEX);
  }

  @Test
  void doesNothingOnceTheCoveringIndexIsTheOnlyOne() throws Exception {
    migrate();

    assertThat(indexes()).contains(NEW_INDEX).doesNotContain(OLD_INDEX);
  }

  private void migrate() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      Context context = mock(Context.class);
      when(context.getConnection()).thenReturn(connection);
      new V214__click_event_visitor_covering_index().migrate(context);
    }
  }

  private List<String> indexes() {
    return jdbc.queryForList(
        "SELECT DISTINCT index_name FROM information_schema.statistics"
            + " WHERE table_schema = DATABASE() AND table_name = 'click_event'",
        String.class);
  }

  private List<String> columns(String index) {
    return jdbc.queryForList(
        "SELECT column_name FROM information_schema.statistics WHERE table_schema = DATABASE()"
            + " AND table_name = 'click_event' AND index_name = ? ORDER BY seq_in_index",
        String.class,
        index);
  }
}

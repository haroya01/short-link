package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// click_recent_quarter.age_day was counted from link.created_at, which holds UTC wall-clock text,
// to clicked_at, which holds the instant, so it is nine hours off. Its clicks are still in
// click_event, so the recent sums are reopened once (V213): aggregated_until drops to rolled_until
// and both tables are emptied in the same transaction. Reports read those days from click_event at
// once and the next daily job sums them again. Without the V213 tables there is nothing to reopen.
public class V216__reopen_recent_click_sums extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    if (!hasRecentSums(conn)) return;
    try (Statement st = conn.createStatement()) {
      st.executeUpdate(
          "UPDATE click_rollup_state SET aggregated_until = rolled_until WHERE id = 1");
      st.executeUpdate("DELETE FROM click_recent_quarter");
      st.executeUpdate("DELETE FROM click_recent_dim");
    }
  }

  private static boolean hasRecentSums(Connection conn) throws Exception {
    try (Statement st = conn.createStatement();
        ResultSet rows =
            st.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()"
                    + " AND table_name = 'click_recent_quarter' LIMIT 1")) {
      return rows.next();
    }
  }
}

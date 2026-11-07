package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// Lowering aggregated_until and emptying both sum tables share one transaction, or reports would
// count those days twice or not at all.
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

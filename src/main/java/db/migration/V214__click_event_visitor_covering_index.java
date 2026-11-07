package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// The old index can go only because this one still serves its (link_id, is_bot = 0, visitor_hash)
// lookups.
public class V214__click_event_visitor_covering_index extends BaseJavaMigration {

  private static final String NEW_INDEX = "idx_click_event_link_bot_visitor";
  private static final String OLD_INDEX = "idx_click_event_visitor";

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    boolean hasNew = exists(conn, NEW_INDEX);
    boolean hasOld = exists(conn, OLD_INDEX);
    String change =
        (hasOld ? "DROP INDEX " + OLD_INDEX : "")
            + (hasOld && !hasNew ? ", " : "")
            + (hasNew
                ? ""
                : "ADD INDEX "
                    + NEW_INDEX
                    + " (link_id, is_bot, visitor_hash, clicked_at, referrer_host)");
    if (change.isEmpty()) return;
    try (Statement st = conn.createStatement()) {
      st.execute("ALTER TABLE click_event " + change + ", ALGORITHM = INPLACE, LOCK = NONE");
    }
  }

  private static boolean exists(Connection conn, String index) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()"
                + " AND table_name = 'click_event' AND index_name = ? LIMIT 1")) {
      ps.setString(1, index);
      try (ResultSet rows = ps.executeQuery()) {
        return rows.next();
      }
    }
  }
}

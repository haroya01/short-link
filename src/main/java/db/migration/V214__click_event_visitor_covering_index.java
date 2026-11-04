package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// The link report's visitor reads (unique visitors, return rate, channel depth) filter on
// (link_id, is_bot) and read visitor_hash, clicked_at and referrer_host; this index holds all of
// them and still serves the (link_id, is_bot = 0, visitor_hash) lookups of idx_click_event_visitor.
// Building it is online (INPLACE, LOCK = NONE) but holds the app's start for as long as it takes,
// so
// on a large click_event it can be built by hand before the deploy; this migration then only drops
// whichever of the two indexes is left over.
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

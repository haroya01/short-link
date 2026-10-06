package db.migration;

import com.example.short_link.common.note.Hashtags;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// Existing notes get their tags from the same extractor the app uses when a note is written.
public class V144__note_tag extends BaseJavaMigration {

  private static final int BATCH = 500;

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    try (Statement st = conn.createStatement()) {
      st.execute(
          "CREATE TABLE IF NOT EXISTS note_tag ("
              + "note_id BIGINT NOT NULL,"
              + " tag VARCHAR(40) NOT NULL,"
              + " PRIMARY KEY (note_id, tag),"
              + " KEY idx_note_tag_tag (tag, note_id),"
              + " CONSTRAINT fk_note_tag_note FOREIGN KEY (note_id) REFERENCES note(id)"
              + " ON DELETE CASCADE"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }
    try (PreparedStatement select =
            conn.prepareStatement("SELECT id, body FROM note WHERE body LIKE '%#%'");
        PreparedStatement insert =
            conn.prepareStatement("INSERT IGNORE INTO note_tag (note_id, tag) VALUES (?, ?)");
        ResultSet rows = select.executeQuery()) {
      int inBatch = 0;
      while (rows.next()) {
        for (String tag : Hashtags.of(rows.getString("body"))) {
          insert.setLong(1, rows.getLong("id"));
          insert.setString(2, tag);
          insert.addBatch();
          if (++inBatch == BATCH) {
            insert.executeBatch();
            inBatch = 0;
          }
        }
      }
      if (inBatch > 0) {
        insert.executeBatch();
      }
    }
  }
}

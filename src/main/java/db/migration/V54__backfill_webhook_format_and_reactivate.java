package db.migration;

import com.example.short_link.link.webhook.domain.WebhookFormat;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// V53의 LIKE 패턴은 canary·ptb 같은 discord.com 하위 도메인을 놓쳤다. 앱과 같은 detect 로직으로 format을 다시 판정하고,
// 자동 비활성화됐다가 GENERIC이 아닌 형식으로 바뀐 훅만 다시 켠다. GENERIC으로 남은 훅의 실패는 형식 문제가 아니라서 그대로 둔다.
public class V54__backfill_webhook_format_and_reactivate extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement();
        ResultSet rows =
            select.executeQuery(
                "SELECT id, url, format, enabled, auto_disabled_reason FROM link_webhook")) {
      while (rows.next()) {
        long id = rows.getLong("id");
        String url = rows.getString("url");
        String currentFormat = rows.getString("format");
        boolean enabled = rows.getBoolean("enabled");
        String autoDisabledReason = rows.getString("auto_disabled_reason");

        WebhookFormat detected = WebhookFormat.detect(url);
        boolean formatChanged = !detected.name().equals(currentFormat);
        boolean autoDisabled = !enabled && autoDisabledReason != null;
        boolean reactivate = autoDisabled && formatChanged && detected != WebhookFormat.GENERIC;

        if (!formatChanged && !reactivate) continue;

        String sql =
            reactivate
                ? "UPDATE link_webhook SET format = ?, enabled = TRUE, "
                    + "consecutive_failures = 0, auto_disabled_reason = NULL, "
                    + "last_error = NULL WHERE id = ?"
                : "UPDATE link_webhook SET format = ? WHERE id = ?";
        try (PreparedStatement update = context.getConnection().prepareStatement(sql)) {
          update.setString(1, detected.name());
          update.setLong(2, id);
          update.executeUpdate();
        }
      }
    }
  }
}

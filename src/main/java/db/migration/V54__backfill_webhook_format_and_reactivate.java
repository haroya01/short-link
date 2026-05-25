package db.migration;

import com.example.short_link.link.webhook.domain.WebhookFormat;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V53 added the {@code format} column and back-filled it with two narrow LIKE patterns ({@code
 * 'https://discord.com/api/webhooks/%'} and the Slack variant). Two problems showed up in
 * production:
 *
 * <ul>
 *   <li>The Java-side {@link WebhookFormat#detect(String)} matches every subdomain that ends in
 *       {@code .discord.com} (canary / ptb), while the SQL patterns covered only the bare {@code
 *       discord.com} host. Rows registered with a canary/ptb URL stayed on {@code GENERIC} and kept
 *       POSTing the kurl JSON to a receiver that rejects it.
 *   <li>Any row whose hook had already racked up 5 consecutive failures by the time V53 ran was
 *       still {@code enabled = false} after the format flip — the user never sees the receiver come
 *       back to life on its own because the dispatcher short-circuits on {@code enabled}.
 * </ul>
 *
 * <p>This migration re-evaluates {@code format} for every row using the same {@link
 * WebhookFormat#detect} logic the application uses at registration time, and reactivates rows that
 * (a) were auto-disabled and (b) flipped to a non-GENERIC format — those are the rows whose
 * failures were a payload-shape mismatch we just fixed, not a real receiver problem.
 *
 * <p>We deliberately do not reactivate rows that stay on {@code GENERIC}: their failures were not
 * payload-shape related, so flipping them back on would just re-trigger the same auto-disable.
 */
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

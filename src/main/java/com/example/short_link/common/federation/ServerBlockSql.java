package com.example.short_link.common.federation;

// What this server's moderators decided about whole servers, as SQL a statement can carry — so a
// list, a lookup or a notice check learns it without another query. A block on a domain covers its
// subdomains, as on Mastodon. The domain and its parents down to two labels are joined to the block
// table's unique key, so a check costs the same however many servers are blocked; a block on a
// domain of more than six labels covers that domain alone.
public final class ServerBlockSql {

  static final int DEEPEST_PARENT = 6;

  private ServerBlockSql() {}

  public static String suspends(String domainExpr) {
    return covered(domainExpr, " WHERE cb.severity = 'SUSPEND'");
  }

  public static String limits(String domainExpr) {
    return covered(domainExpr, " WHERE cb.severity = 'LIMIT'");
  }

  public static String blocks(String domainExpr) {
    return covered(domainExpr, "");
  }

  // The account (remote actor id expression) is on a suspended server.
  public static String suspended(String remoteActorExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_actor sa WHERE sa.id = "
        + remoteActorExpr
        + " AND "
        + suspends("sa.domain")
        + ")";
  }

  // The account is on a limited server and the viewer does not follow it.
  public static String limitedFor(String remoteActorExpr, String viewerExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_actor la WHERE la.id = "
        + remoteActorExpr
        + " AND "
        + limits("la.domain")
        + " AND NOT EXISTS (SELECT 1 FROM federation_following lf WHERE lf.user_id = "
        + viewerExpr
        + " AND lf.remote_actor_id = la.id AND lf.accepted_at IS NOT NULL))";
  }

  private static String covered(String domainExpr, String which) {
    return "EXISTS (SELECT 1 FROM "
        + parents(domainExpr)
        + " cp JOIN federation_domain_block cb ON cb.domain = cp.domain"
        + which
        + ")";
  }

  private static String parents(String domainExpr) {
    StringBuilder sql = new StringBuilder("(SELECT ").append(domainExpr).append(" AS domain");
    for (int labels = DEEPEST_PARENT; labels >= 2; labels--) {
      sql.append(" UNION ALL SELECT SUBSTRING_INDEX(")
          .append(domainExpr)
          .append(", '.', -")
          .append(labels)
          .append(")");
    }
    return sql.append(")").toString();
  }
}

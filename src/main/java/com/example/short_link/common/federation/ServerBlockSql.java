package com.example.short_link.common.federation;

// What this server's moderators decided about whole servers, as SQL a statement can carry — so a
// list, a lookup or a notice check learns it without another query. A block on a domain covers its
// subdomains, as on Mastodon.
public final class ServerBlockSql {

  private ServerBlockSql() {}

  public static String covers(String blockAlias, String domainExpr) {
    return "("
        + domainExpr
        + " = "
        + blockAlias
        + ".domain OR "
        + domainExpr
        + " LIKE CONCAT('%.', "
        + blockAlias
        + ".domain))";
  }

  // The account (remote actor id expression) is on a suspended server.
  public static String suspended(String remoteActorExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_actor sa JOIN federation_domain_block sb ON "
        + covers("sb", "sa.domain")
        + " WHERE sa.id = "
        + remoteActorExpr
        + " AND sb.severity = 'SUSPEND')";
  }

  // The account is on a limited server and the viewer does not follow it.
  public static String limitedFor(String remoteActorExpr, String viewerExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_actor la JOIN federation_domain_block lb ON "
        + covers("lb", "la.domain")
        + " WHERE la.id = "
        + remoteActorExpr
        + " AND lb.severity = 'LIMIT'"
        + " AND NOT EXISTS (SELECT 1 FROM federation_following lf WHERE lf.user_id = "
        + viewerExpr
        + " AND lf.remote_actor_id = la.id AND lf.accepted_at IS NOT NULL))";
  }
}

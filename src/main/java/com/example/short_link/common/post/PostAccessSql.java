package com.example.short_link.common.post;

// The list half of PostAccessPolicy: each fragment keeps the same posts as its Java twin there. The
// caller binds :viewer (-1 when anonymous) wherever PROFILE is used.
public final class PostAccessSql {

  public static final boolean REGION_RESTRICTED_IN_DISCOVERY = true;

  private static final String PUBLISHED = "p.status = 'PUBLISHED'";

  public static final String LISTED =
      PUBLISHED
          + " AND p.audience = 'PUBLIC'"
          + (REGION_RESTRICTED_IN_DISCOVERY ? "" : " AND p.blocked_countries IS NULL");

  public static final String OPEN =
      PUBLISHED + " AND p.audience = 'PUBLIC' AND p.blocked_countries IS NULL";

  public static final String CARD = PUBLISHED + " AND p.audience <> 'UNLISTED'";

  public static final String LISTED_AS_Q =
      "q.status = 'PUBLISHED' AND q.audience = 'PUBLIC'"
          + (REGION_RESTRICTED_IN_DISCOVERY ? "" : " AND q.blocked_countries IS NULL");

  public static final String PROFILE =
      PUBLISHED + " AND (p.audience <> 'UNLISTED' OR p.user_id = :viewer)";

  private PostAccessSql() {}

  public static String listed(String alias) {
    return aliased(LISTED, alias);
  }

  public static String open(String alias) {
    return aliased(OPEN, alias);
  }

  public static String card(String alias) {
    return aliased(CARD, alias);
  }

  private static String aliased(String fragment, String alias) {
    return fragment.replace("p.", alias + ".");
  }
}

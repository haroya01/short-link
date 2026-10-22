package com.example.short_link.link.stats.domain;

public final class ClickRollup {

  // click_event rows below this instant are already in the rollup tables and wait for deletion.
  public static final String ROLLED_UNTIL =
      "(SELECT rolled_until FROM click_rollup_state WHERE id = 1)";

  public static final int REFERRER = 1;
  public static final int REFERRER_HOST = 2;
  public static final int DEVICE = 3;
  public static final int OS = 4;
  public static final int BROWSER = 5;
  public static final int BOT_NAME = 6;
  public static final int UTM_CAMPAIGN = 7;
  public static final int UTM_SOURCE = 8;
  public static final int UTM_MEDIUM = 9;
  public static final int UTM_CONTENT = 10;
  public static final int UTM_TERM = 11;
  public static final int CLIENT_APP = 12;
  public static final int FETCH_SITE = 13;
  public static final int POST = 14;
  public static final int SOURCE_CHANNEL = 15;
  public static final int DESTINATION = 16;
  public static final int COUNTRY = 17;
  public static final int REGION = 18;
  public static final int CITY = 19;
  public static final int LANGUAGE = 20;
  public static final int ASN = 21;

  private ClickRollup() {}
}

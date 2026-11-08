package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorParser;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

final class PublicStatuses {

  private PublicStatuses() {}

  static boolean keepable(JsonNode status, FederationUrls urls) {
    return safe(status) && !namesUs(status, urls);
  }

  static boolean safe(JsonNode status) {
    return !status.path("sensitive").asBoolean(false)
        && status.path("spoiler_text").asString("").isBlank()
        && !status.path("account").path("bot").asBoolean(false);
  }

  static boolean namesUs(JsonNode status, FederationUrls urls) {
    for (JsonNode mention : status.path("mentions")) {
      String acct = mention.path("acct").asString("");
      int at = acct.lastIndexOf('@');
      String host = at < 0 ? null : RemoteActorParser.host("https://" + acct.substring(at + 1));
      if (urls.isOurs(mention.path("url").asString(null))
          || (host != null && host.equals(urls.domain()))) {
        return true;
      }
    }
    return false;
  }

  static Instant createdAt(JsonNode status) {
    String at = status.path("created_at").asString(null);
    try {
      return at == null ? null : Instant.parse(at);
    } catch (DateTimeParseException malformed) {
      return null;
    }
  }
}

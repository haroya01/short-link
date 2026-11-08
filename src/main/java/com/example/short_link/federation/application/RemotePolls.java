package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.dateTime;

import com.example.short_link.common.note.RemoteNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

final class RemotePolls {

  // A poll fits the note's poll column as a member's does: 2 to 4 options of up to 50 characters.
  private static final int MAX_POLL_OPTIONS = 4;
  private static final int MAX_POLL_OPTION = 50;

  private RemotePolls() {}

  static RemoteNotes.Poll poll(JsonNode question) {
    JsonNode single = question.get("oneOf");
    boolean multiple = single == null || !single.isArray();
    JsonNode choices = multiple ? question.get("anyOf") : single;
    if (!fits(choices)) {
      return null;
    }
    List<String> options = new ArrayList<>();
    List<Long> votes = new ArrayList<>();
    long total = 0;
    for (JsonNode choice : choices) {
      String name = RemoteHtml.oneLine(choice.get("name"));
      if (name == null) {
        return null;
      }
      long count = count(choice.path("replies").get("totalItems"));
      options.add(RemoteHtml.cut(name, MAX_POLL_OPTION));
      votes.add(count);
      total += count;
    }
    JsonNode voters = question.get("votersCount");
    Instant ends = dateTime(question.get("endTime"));
    JsonNode closedNode = question.get("closed");
    Instant closedAt = dateTime(closedNode);
    return new RemoteNotes.Poll(
        List.copyOf(options),
        List.copyOf(votes),
        voters == null ? total : count(voters),
        multiple,
        endsAt(ends, closedAt),
        closedAt != null || (closedNode != null && closedNode.asBoolean(false)));
  }

  private static boolean fits(JsonNode choices) {
    return choices != null
        && choices.isArray()
        && choices.size() >= 2
        && choices.size() <= MAX_POLL_OPTIONS;
  }

  private static Instant endsAt(Instant ends, Instant closedAt) {
    return closedAt != null && (ends == null || closedAt.isBefore(ends)) ? closedAt : ends;
  }

  private static long count(JsonNode node) {
    return node != null && node.isIntegralNumber() && node.longValue() > 0 ? node.longValue() : 0;
  }
}

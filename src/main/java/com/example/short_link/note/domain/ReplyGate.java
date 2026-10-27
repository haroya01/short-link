package com.example.short_link.note.domain;

import com.example.short_link.common.note.Mentions;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// Who may answer a thread whose first note limits replies: its writer, anyone the first note names,
// and under following anyone its writer follows. A thread whose first note is gone, or that started
// on another server, takes answers from anyone. writerFollows is asked last, and only then.
public final class ReplyGate {

  private ReplyGate() {}

  public sealed interface Replier permits Member, Remote {}

  public record Member(Long id, String username) implements Replier {}

  public record Remote(String handle) implements Replier {}

  public static boolean admits(NoteEntity root, Replier replier, BooleanSupplier writerFollows) {
    if (root == null || root.isRemote()) {
      return true;
    }
    if (replier instanceof Member member && root.isOwnedBy(member.id())) {
      return true;
    }
    if (named(root, replier)) {
      return true;
    }
    return root.getReplyPolicy() == NoteReplyPolicy.FOLLOWING && writerFollows.getAsBoolean();
  }

  private static boolean named(NoteEntity root, Replier replier) {
    return switch (replier) {
      case Member member ->
          member.username() != null && Mentions.of(root.getBody()).contains(member.username());
      case Remote remote ->
          remote.handle() != null
              && Mentions.remote(root.getBody()).contains(remote.handle().toLowerCase(Locale.ROOT));
    };
  }
}

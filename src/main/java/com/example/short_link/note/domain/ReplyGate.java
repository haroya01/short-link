package com.example.short_link.note.domain;

import com.example.short_link.common.note.Mentions;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// writerFollows is asked last, and only when the other answers left the question open.
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

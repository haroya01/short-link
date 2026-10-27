package com.example.short_link.portability.application;

import com.example.short_link.common.federation.LocalAddress;
import com.example.short_link.federation.application.DomainBlocks;
import com.example.short_link.federation.application.RemoteBlocks;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.federation.application.RemoteMutes;
import com.example.short_link.note.application.write.NoteFeedSettingsService;
import com.example.short_link.note.application.write.NoteInteractions;
import com.example.short_link.note.application.write.NoteListService;
import com.example.short_link.portability.domain.ImportKind;
import com.example.short_link.user.application.write.BlockUseCase;
import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.application.write.MuteUseCase;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Applies one line of a Mastodon export as the member would by hand: the same follow, block,
// mute, server block, bookmark or list membership, with the same checks. An account on another
// server is followed, blocked or muted after it is looked up; lists take members of this server
// only. A line that cannot be applied is counted as failed and the rest go on.
@Slf4j
@Component
class ImportRowApplier {

  private final FollowUseCase follows;
  private final BlockUseCase blocks;
  private final MuteUseCase mutes;
  private final RemoteFollowing remoteFollowing;
  private final RemoteBlocks remoteBlocks;
  private final RemoteMutes remoteMutes;
  private final DomainBlocks domainBlocks;
  private final NoteInteractions notes;
  private final NoteFeedSettingsService feedSettings;
  private final NoteListService lists;
  private final ImportLookup lookup;
  private final String baseUrl;
  private final String domain;

  ImportRowApplier(
      FollowUseCase follows,
      BlockUseCase blocks,
      MuteUseCase mutes,
      RemoteFollowing remoteFollowing,
      RemoteBlocks remoteBlocks,
      RemoteMutes remoteMutes,
      DomainBlocks domainBlocks,
      NoteInteractions notes,
      NoteFeedSettingsService feedSettings,
      NoteListService lists,
      ImportLookup lookup,
      LocalAddress local) {
    this.follows = follows;
    this.blocks = blocks;
    this.mutes = mutes;
    this.remoteFollowing = remoteFollowing;
    this.remoteBlocks = remoteBlocks;
    this.remoteMutes = remoteMutes;
    this.domainBlocks = domainBlocks;
    this.notes = notes;
    this.feedSettings = feedSettings;
    this.lists = lists;
    this.lookup = lookup;
    this.baseUrl = local.baseUrl();
    this.domain = local.domain();
  }

  record Address(String username, String domain) {}

  boolean apply(Long userId, ImportKind kind, List<String> cells) {
    try {
      return switch (kind) {
        case FOLLOWING -> follow(userId, cells);
        case BLOCKS -> block(userId, cells);
        case MUTES -> mute(userId, cells);
        case DOMAIN_BLOCKS -> run(() -> domainBlocks.block(userId, cells.get(0)));
        case BOOKMARKS ->
            noteId(cells.get(0))
                .map(id -> run(() -> notes.setBookmark(userId, id, true)))
                .orElse(false);
        case LISTS -> list(userId, cells);
      };
    } catch (RuntimeException e) {
      log.debug("import line not applied: {} {}", kind, e.getMessage());
      return false;
    }
  }

  private boolean follow(Long userId, List<String> cells) {
    Address address = address(cells.get(0));
    if (address == null) {
      return false;
    }
    if (isLocal(address)) {
      follows.follow(userId, address.username(), null);
      if ("false".equalsIgnoreCase(cell(cells, 1))) {
        feedSettings.setRepostsHidden(userId, address.username(), true);
      }
      String notify = cell(cells, 2);
      if ("true".equalsIgnoreCase(notify) || "false".equalsIgnoreCase(notify)) {
        try {
          follows.setNoteNotifications(userId, address.username(), Boolean.parseBoolean(notify));
        } catch (RuntimeException e) {
          log.debug("bell left as is for a follow still waiting: {}", e.getMessage());
        }
      }
      return true;
    }
    remoteFollowing.followFromImport(userId, remoteId(userId, address));
    return true;
  }

  private boolean block(Long userId, List<String> cells) {
    Address address = address(cells.get(0));
    if (address == null) {
      return false;
    }
    if (isLocal(address)) {
      blocks.block(userId, address.username());
    } else {
      remoteBlocks.block(userId, remoteId(userId, address));
    }
    return true;
  }

  private boolean mute(Long userId, List<String> cells) {
    Address address = address(cells.get(0));
    if (address == null) {
      return false;
    }
    boolean notifications = !"false".equalsIgnoreCase(cell(cells, 1));
    if (isLocal(address)) {
      mutes.mute(userId, address.username(), notifications, null);
    } else {
      remoteMutes.mute(userId, remoteId(userId, address), notifications, null);
    }
    return true;
  }

  private Long remoteId(Long userId, Address address) {
    return remoteFollowing.lookup(userId, address.username() + "@" + address.domain()).id();
  }

  private boolean list(Long userId, List<String> cells) {
    String title = cell(cells, 0);
    Optional<String> member = local(cell(cells, 1));
    if (title.isEmpty() || member.isEmpty()) {
      return false;
    }
    Long listId =
        lists.mine(userId).stream()
            .filter(l -> l.title().equals(title))
            .findFirst()
            .map(NoteListService.ListView::id)
            .orElseGet(() -> lists.create(userId, title).id());
    lists.add(userId, listId, member.get());
    return true;
  }

  private Optional<String> local(String raw) {
    Address address = address(raw);
    return address != null && isLocal(address) ? Optional.of(address.username()) : Optional.empty();
  }

  private Optional<Long> noteId(String url) {
    String prefix = baseUrl + "/ap/notes/";
    if (url.startsWith(prefix)) {
      try {
        return Optional.of(Long.parseLong(url.substring(prefix.length())));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
    return lookup.noteIdByUri(url);
  }

  private boolean isLocal(Address address) {
    return address.domain() == null || address.domain().equalsIgnoreCase(domain);
  }

  static Address address(String raw) {
    String handle = raw == null ? "" : raw.trim();
    if (handle.startsWith("@")) {
      handle = handle.substring(1);
    }
    if (handle.isEmpty()) {
      return null;
    }
    int at = handle.lastIndexOf('@');
    if (at < 0) {
      return new Address(handle, null);
    }
    String name = handle.substring(0, at);
    String host = handle.substring(at + 1).toLowerCase(Locale.ROOT);
    return name.isEmpty() || host.isEmpty() ? null : new Address(name, host);
  }

  private static String cell(List<String> cells, int index) {
    return index < cells.size() ? cells.get(index) : "";
  }

  private static boolean run(Runnable action) {
    action.run();
    return true;
  }
}

package com.example.short_link.user.domain.repository;

import java.util.List;

// What a member keeps about other accounts, read for Mastodon's CSV export. A null domain is an
// account on this server.
public interface AccountExportReader {

  record Account(String username, String domain) {}

  record Follow(Account account, boolean showReposts, boolean notifyNotes) {}

  record Mute(Account account, boolean hideNotifications) {}

  record Bookmark(Long noteId, String uri) {}

  record ListMember(String title, Account account) {}

  List<Follow> follows(Long userId);

  List<Account> blocks(Long userId);

  List<Mute> mutes(Long userId);

  List<String> blockedDomains(Long userId);

  List<Bookmark> bookmarks(Long userId);

  List<ListMember> lists(Long userId);
}

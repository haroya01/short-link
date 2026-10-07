package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.repository.AccountExportReader;
import com.example.short_link.user.domain.repository.AccountExportReader.Account;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Mastodon's data export, file for file, so another server's import reads them as they are: the
// accounts a member follows, blocks and mutes, the servers they blocked, their bookmarks and their
// lists. Accounts here are written as name@this-server.
@Service
public class AccountExportService {

  public enum Kind {
    FOLLOWING("following_accounts.csv"),
    BLOCKS("blocked_accounts.csv"),
    MUTES("muted_accounts.csv"),
    DOMAIN_BLOCKS("blocked_domains.csv"),
    BOOKMARKS("bookmarks.csv"),
    LISTS("lists.csv");

    private final String filename;

    Kind(String filename) {
      this.filename = filename;
    }

    public String filename() {
      return filename;
    }

    public static Kind of(String name) {
      try {
        return valueOf(name.replace('-', '_').toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new UserException(UserErrorCode.EXPORT_KIND_NOT_FOUND, name);
      }
    }
  }

  private final AccountExportReader reader;
  private final String baseUrl;
  private final String domain;

  public AccountExportService(
      AccountExportReader reader,
      @Value("${short-link.federation.base-url:http://localhost:8080}") String baseUrl) {
    this.reader = reader;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    URI uri = URI.create(this.baseUrl);
    this.domain = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
  }

  @Transactional(readOnly = true)
  public String csv(Long userId, Kind kind) {
    List<String> lines =
        switch (kind) {
          case FOLLOWING ->
              Stream.concat(
                      Stream.of("Account address,Show boosts,Notify on new posts,Languages"),
                      reader.follows(userId).stream()
                          .map(
                              f ->
                                  row(
                                      address(f.account()),
                                      String.valueOf(f.showReposts()),
                                      String.valueOf(f.notifyNotes()),
                                      "")))
                  .toList();
          case BLOCKS -> reader.blocks(userId).stream().map(a -> row(address(a))).toList();
          case MUTES ->
              Stream.concat(
                      Stream.of("Account address,Hide notifications"),
                      reader.mutes(userId).stream()
                          .map(
                              m ->
                                  row(address(m.account()), String.valueOf(m.hideNotifications()))))
                  .toList();
          case DOMAIN_BLOCKS ->
              reader.blockedDomains(userId).stream().map(AccountExportService::row).toList();
          case BOOKMARKS ->
              reader.bookmarks(userId).stream()
                  .map(b -> row(b.uri() != null ? b.uri() : baseUrl + "/ap/notes/" + b.noteId()))
                  .toList();
          case LISTS ->
              reader.lists(userId).stream().map(l -> row(l.title(), address(l.account()))).toList();
        };
    return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
  }

  private String address(Account account) {
    return account.username() + "@" + (account.domain() == null ? domain : account.domain());
  }

  // RFC 4180: a field with a comma, quote or line break is quoted, its quotes doubled.
  private static String row(String... fields) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        line.append(',');
      }
      String field = fields[i];
      if (field.contains(",")
          || field.contains("\"")
          || field.contains("\n")
          || field.contains("\r")) {
        line.append('"').append(field.replace("\"", "\"\"")).append('"');
      } else {
        line.append(field);
      }
    }
    return line.toString();
  }
}

package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.read.AccountExportService.Kind;
import com.example.short_link.user.domain.repository.AccountExportReader;
import com.example.short_link.user.domain.repository.AccountExportReader.Account;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountExportServiceTest {

  @Mock private AccountExportReader reader;

  private AccountExportService service() {
    return new AccountExportService(reader, "https://kurl.me/");
  }

  @Test
  void followsAreMastodonsFollowingFileWithThisServersName() {
    when(reader.follows(7L))
        .thenReturn(
            List.of(
                new AccountExportReader.Follow(new Account("sori", null), false, true),
                new AccountExportReader.Follow(
                    new Account("alice", "mastodon.social"), true, false)));

    assertThat(service().csv(7L, Kind.FOLLOWING))
        .isEqualTo(
            "Account address,Show boosts,Notify on new posts,Languages\n"
                + "sori@kurl.me,false,true,\n"
                + "alice@mastodon.social,true,false,\n");
  }

  @Test
  void blocksAndDomainsAreOneAddressALine() {
    when(reader.blocks(7L)).thenReturn(List.of(new Account("troll", null)));
    when(reader.blockedDomains(7L)).thenReturn(List.of("spam.example"));
    AccountExportService service = service();

    assertThat(service.csv(7L, Kind.BLOCKS)).isEqualTo("troll@kurl.me\n");
    assertThat(service.csv(7L, Kind.DOMAIN_BLOCKS)).isEqualTo("spam.example\n");
  }

  @Test
  void mutesSayWhetherNotificationsAreHiddenToo() {
    when(reader.mutes(7L))
        .thenReturn(List.of(new AccountExportReader.Mute(new Account("loud", null), true)));

    assertThat(service().csv(7L, Kind.MUTES))
        .isEqualTo("Account address,Hide notifications\nloud@kurl.me,true\n");
  }

  @Test
  void bookmarksAreTheNotesAddressesWhereverTheyLive() {
    when(reader.bookmarks(7L))
        .thenReturn(
            List.of(
                new AccountExportReader.Bookmark(5L, null),
                new AccountExportReader.Bookmark(
                    9L, "https://mastodon.social/users/a/statuses/1")));

    assertThat(service().csv(7L, Kind.BOOKMARKS))
        .isEqualTo("https://kurl.me/ap/notes/5\nhttps://mastodon.social/users/a/statuses/1\n");
  }

  @Test
  void aListNameWithACommaOrQuoteIsQuoted() {
    when(reader.lists(7L))
        .thenReturn(
            List.of(
                new AccountExportReader.ListMember("friends, close", new Account("sori", null)),
                new AccountExportReader.ListMember("say \"hi\"", new Account("mina", null))));

    assertThat(service().csv(7L, Kind.LISTS))
        .isEqualTo("\"friends, close\",sori@kurl.me\n\"say \"\"hi\"\"\",mina@kurl.me\n");
  }

  @Test
  void nothingToExportIsAnEmptyFileAndAnUnknownKindIsNotFound() {
    when(reader.blocks(7L)).thenReturn(List.of());

    assertThat(service().csv(7L, Kind.BLOCKS)).isEmpty();
    assertThat(Kind.of("domain-blocks")).isEqualTo(Kind.DOMAIN_BLOCKS);
    assertThatThrownBy(() -> Kind.of("passwords"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.EXPORT_KIND_NOT_FOUND));
  }

  @Test
  void aServerOnAPortKeepsItInTheAddress() {
    when(reader.blocks(7L)).thenReturn(List.of(new Account("troll", null)));

    assertThat(new AccountExportService(reader, "http://localhost:8080").csv(7L, Kind.BLOCKS))
        .isEqualTo("troll@localhost:8080\n");
  }
}

package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import org.junit.jupiter.api.Test;

class AccountExportHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String EXPORTS = "/api/v1/users/me/exports/";

  @Test
  void aMemberDownloadsMastodonsExportFiles() throws Exception {
    UserEntity loud = createUser();
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, notify_notes, created_at)"
            + " VALUES (?, ?, TRUE, NOW(6))",
        owner.getId(),
        stranger.getId());
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        loud.getId());
    jdbc.update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?, ?, TRUE, NOW(6))",
        owner.getId(),
        stranger.getId());
    jdbc.update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, 'spam.example', NOW(6))",
        owner.getId());
    jdbc.update(
        "INSERT INTO note_list (user_id, title, created_at) VALUES (?, 'friends, close', NOW(6))",
        owner.getId());
    jdbc.update(
        "INSERT INTO note_list_member (list_id, member_id, created_at)"
            + " SELECT id, ?, NOW(6) FROM note_list WHERE user_id = ?",
        stranger.getId(),
        owner.getId());

    var following = call("export-following", "GET", EXPORTS + "following", null, token, 200);
    assertThat(following.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
    assertThat(following.headers().firstValue("Content-Disposition").orElse(""))
        .contains("following_accounts.csv");
    assertThat(following.body())
        .startsWith("Account address,Show boosts,Notify on new posts,Languages\n")
        .contains(stranger.getUsername() + "@")
        .contains(",true,true,");
    assertThat(call("export-blocks", "GET", EXPORTS + "blocks", null, token, 200).body())
        .startsWith(loud.getUsername() + "@");
    assertThat(call("export-mutes", "GET", EXPORTS + "mutes", null, token, 200).body())
        .contains(stranger.getUsername() + "@")
        .endsWith(",true\n");
    assertThat(
            call("export-domain-blocks", "GET", EXPORTS + "domain-blocks", null, token, 200).body())
        .isEqualTo("spam.example\n");
    assertThat(call("export-bookmarks", "GET", EXPORTS + "bookmarks", null, token, 200).body())
        .isEmpty();
    assertThat(call("export-lists", "GET", EXPORTS + "lists", null, token, 200).body())
        .startsWith("\"friends, close\"," + stranger.getUsername() + "@");
    call("export-unknown", "GET", EXPORTS + "passwords", null, token, 404);
    assertThat(
            call("export-other-member", "GET", EXPORTS + "following", null, strangerToken, 200)
                .body())
        .isEqualTo("Account address,Show boosts,Notify on new posts,Languages\n");
  }
}

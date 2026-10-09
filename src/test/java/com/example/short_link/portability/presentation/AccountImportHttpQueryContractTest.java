package com.example.short_link.portability.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.portability.application.AccountImportService;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountImportHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String IMPORTS = "/api/v1/users/me/imports";

  @Autowired private AccountImportService imports;

  @Test
  void aMemberBringsMastodonsFilesInAndWatchesThemApplied() throws Exception {
    UserEntity newbie = createUser();
    String following =
        "Account address,Show boosts,Notify on new posts,Languages\n"
            + stranger.getUsername()
            + ",false,true,\n@"
            + newbie.getUsername()
            + ",true,false,\nnobody-here-at-all,true,false,\n";

    var started =
        body(
            call(
                "import-start-following",
                "POST",
                IMPORTS,
                Map.of("kind", "following", "csv", following),
                token,
                201));
    assertThat(started.path("kind").asString()).isEqualTo("FOLLOWING");
    assertThat(started.path("total").asInt()).isEqualTo(3);
    assertThat(
            count(
                "SELECT COUNT(*) FROM account_import_row WHERE import_id = ?",
                started.path("id").asLong()))
        .isEqualTo(3);
    call(
        "import-start-running",
        "POST",
        IMPORTS,
        Map.of("kind", "blocks", "csv", newbie.getUsername()),
        token,
        409);
    assertThat(
            body(call("import-list-running", "GET", IMPORTS, null, token, 200))
                .get(0)
                .path("finished")
                .asBoolean())
        .isFalse();

    assertThat(imports.processBatch(100)).isEqualTo(3);
    // Imported follows notify asynchronously; let that settle so it is not measured as the next
    // step.
    awaitAsyncWork();
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_follow WHERE follower_id = ? AND following_id IN (?, ?)",
                owner.getId(),
                stranger.getId(),
                newbie.getId()))
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM note_repost_mute WHERE user_id = ? AND muted_user_id = ?",
                owner.getId(),
                stranger.getId()))
        .isEqualTo(1);
    var done = body(call("import-list-finished", "GET", IMPORTS, null, token, 200)).get(0);
    assertThat(done.path("finished").asBoolean()).isTrue();
    assertThat(done.path("processed").asInt()).isEqualTo(3);
    assertThat(done.path("imported").asInt()).isEqualTo(2);

    call(
        "import-start-domain-blocks",
        "POST",
        IMPORTS,
        Map.of("kind", "domain-blocks", "csv", "spam.example\nother.example\n"),
        token,
        201);
    imports.processBatch(100);
    awaitAsyncWork();
    assertThat(count("SELECT COUNT(*) FROM user_domain_block WHERE user_id = ?", owner.getId()))
        .isEqualTo(2);

    call(
        "import-start-unknown",
        "POST",
        IMPORTS,
        Map.of("kind", "passwords", "csv", "x"),
        token,
        400);
    call(
        "import-start-empty",
        "POST",
        IMPORTS,
        Map.of("kind", "following", "csv", "Account address,Show boosts\n"),
        token,
        400);
  }
}

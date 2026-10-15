package com.example.short_link.blogimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ImportSourceEntityTest {

  private static final Instant VERIFIED = Instant.parse("2026-10-15T00:00:00Z");

  private static ImportSourceEntity pending() {
    return new ImportSourceEntity(
        7L,
        BlogAddress.parse("velog.io/@velopert").orElseThrow(),
        " velopert.log ",
        true,
        "kurl-ABCDEF");
  }

  @Test
  void aNewSourceAwaitsVerificationWithItsCode() {
    ImportSourceEntity source = pending();

    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.PENDING_VERIFICATION);
    assertThat(source.awaitsVerification()).isTrue();
    assertThat(source.isVerified()).isFalse();
    assertThat(source.getVerificationCode()).isEqualTo("kurl-ABCDEF");
    assertThat(source.getBlogTitle()).isEqualTo("velopert.log");
    assertThat(source.getClaimKey()).isNull();
    assertThat(source.isAnnounceNewPosts()).isTrue();
    assertThat(source.isShowInDiscover()).isTrue();
    assertThat(source.isOwnedBy(7L)).isTrue();
    assertThat(source.isOwnedBy(8L)).isFalse();
    assertThat(source.address().feedUrl()).isEqualTo("https://v2.velog.io/rss/@velopert");
    assertThat(source.address().id()).isEqualTo("velopert");
  }

  @Test
  void verifyingClaimsTheBlogAndStartsTheFirstImport() {
    ImportSourceEntity source = pending();

    source.verify(VERIFIED, 20);

    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.IMPORTING);
    assertThat(source.getClaimKey()).isEqualTo("velog:velopert");
    assertThat(source.getVerificationCode()).isNull();
    assertThat(source.getVerifiedAt()).isEqualTo(VERIFIED);
    assertThat(source.getProgressTotal()).isEqualTo(20);
    assertThat(source.getProgressDone()).isZero();
  }

  @Test
  void runsMoveBetweenSyncingAndError() {
    ImportSourceEntity source = pending();
    source.failRun("IMPORT_SOURCE_UNREACHABLE");
    source.finishRun(VERIFIED);
    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.PENDING_VERIFICATION);

    source.verify(VERIFIED, 2);
    source.recordProgress(1, 1);
    source.finishRun(VERIFIED.plusSeconds(60));
    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.SYNCING);
    assertThat(source.getLastSyncedAt()).isEqualTo(VERIFIED.plusSeconds(60));
    assertThat(source.getProgressFailed()).isEqualTo(1);

    source.failRun("IMPORT_FEED_NOT_FOUND");
    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.ERROR);
    assertThat(source.getLastError()).isEqualTo("IMPORT_FEED_NOT_FOUND");
    assertThat(source.isVerified()).isTrue();

    source.finishRun(VERIFIED.plusSeconds(120));
    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.SYNCING);
    assertThat(source.getLastError()).isNull();
  }

  @Test
  void disconnectingReleasesTheClaimAndReconnectingAsksForANewCode() {
    ImportSourceEntity source = pending();
    source.verify(VERIFIED, 0);

    source.disconnect();

    assertThat(source.isDisconnected()).isTrue();
    assertThat(source.isVerified()).isFalse();
    assertThat(source.getClaimKey()).isNull();

    source.reconnect("kurl-GHJKMN", "new title", false);
    assertThat(source.awaitsVerification()).isTrue();
    assertThat(source.getVerificationCode()).isEqualTo("kurl-GHJKMN");
    assertThat(source.getBlogTitle()).isEqualTo("new title");
    assertThat(source.isFullContent()).isFalse();
  }

  @Test
  void onlyPostsPublishedAfterVerificationAreAnnounced() {
    ImportSourceEntity source = pending();
    assertThat(source.announces(VERIFIED.plusSeconds(1))).isFalse();

    source.verify(VERIFIED, 0);

    assertThat(source.announces(VERIFIED.plusSeconds(1))).isTrue();
    assertThat(source.announces(VERIFIED)).isFalse();
    assertThat(source.announces(null)).isFalse();
    source.updateSettings(false, null);
    assertThat(source.announces(VERIFIED.plusSeconds(1))).isFalse();
    assertThat(source.isShowInDiscover()).isTrue();
    source.updateSettings(null, false);
    assertThat(source.isShowInDiscover()).isFalse();
    assertThat(source.isAnnounceNewPosts()).isFalse();
  }

  @Test
  void titlesAreClippedToTheColumn() {
    ImportSourceEntity source = pending();

    source.refresh("t".repeat(300), true);
    assertThat(source.getBlogTitle()).hasSize(ImportSourceEntity.MAX_TITLE_LENGTH);
    source.refresh(null, true);
    assertThat(source.getBlogTitle()).isEmpty();
  }

  @Test
  void ledgerItemsSettleOnSuccessOrAfterTheirLastAttempt() {
    ImportItemEntity item = new ImportItemEntity(1L, "hash");
    assertThat(item.isSettled()).isFalse();
    item.attachPost(9L);
    assertThat(item.getPostId()).isEqualTo(9L);
    for (int i = 0; i < ImportItemEntity.MAX_ATTEMPTS - 1; i++) {
      item.fail();
    }
    assertThat(item.isSettled()).isFalse();
    item.fail();
    assertThat(item.isSettled()).isTrue();

    ImportItemEntity done = new ImportItemEntity(1L, "other");
    done.complete(VERIFIED);
    assertThat(done.isSettled()).isTrue();
    assertThat(done.getImportedAt()).isEqualTo(VERIFIED);
  }
}

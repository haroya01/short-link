package com.example.short_link.note.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.common.net.PublicImageFetcher.Image;
import com.example.short_link.common.net.PublicImageFetcher.Refused;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest(
    properties = {
      "short-link.remote-media.enabled=true",
      "short-link.image-variants.enabled=true",
      "short-link.avatar.bucket=remote-media-it",
      "short-link.avatar.region=ap-northeast-1",
      "short-link.avatar.public-base-url=https://cdn.example"
    })
@ActiveProfiles("test")
class RemoteMediaCacheIntegrationTest {

  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final byte[] PNG = png(1600, 900);

  @MockitoBean private S3Client s3;
  @MockitoBean private PublicImageFetcher fetcher;
  @Autowired private RemoteNotes remoteNotes;
  @Autowired private RemoteMediaQueue queue;
  @Autowired private JdbcTemplate jdbc;

  private final String run = UUID.randomUUID().toString().substring(0, 8);
  private long actor;

  @BeforeEach
  void anAccountElsewhere() {
    String uri = "https://rm.example/users/" + run;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " shared_inbox, username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, NULL, ?, 'rm.example', NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        "rm" + run);
    actor =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);
  }

  @AfterEach
  void forget() {
    jdbc.update("DELETE FROM note WHERE remote_actor_id = ?", actor);
    jdbc.update("DELETE FROM federation_remote_actor WHERE id = ?", actor);
    for (String key :
        jdbc.queryForList(
            "SELECT storage_key FROM remote_media WHERE remote_url LIKE ?",
            String.class,
            picture("%"))) {
      jdbc.update("DELETE FROM image_variant WHERE storage_key = ?", key);
      jdbc.update("DELETE FROM remote_media WHERE storage_key = ?", key);
    }
  }

  @Test
  void aPictureOnANoteFromElsewhereIsServedFromOurCdnOnceItAndItsCopiesAreStored() {
    String photo = picture("photo.png");
    String gif = picture("moving.gif");
    when(fetcher.fetch(photo, MAX_BYTES)).thenReturn(new Image(PNG, "image/png"));

    long note =
        receive(
            "first",
            List.of(
                new RemoteNotes.Media(photo, "a cat", "image/png", 1600, 900),
                new RemoteNotes.Media(gif, null, "image/gif")));

    String key = key(photo);
    awaitShown(note, 0);
    Map<String, Object> shown = row(note, 0);
    assertThat(shown.get("url")).isEqualTo("https://cdn.example/" + key);
    assertThat(shown.get("storage_key")).isEqualTo(key);
    assertThat(shown.get("remote_url")).isEqualTo(photo);
    Map<String, Object> untouched = row(note, 1);
    assertThat(untouched.get("url")).isEqualTo(gif);
    assertThat(untouched.get("storage_key")).isEqualTo("");
    assertThat(untouched.get("remote_url")).isNull();

    ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3, times(3)).putObject(puts.capture(), any(RequestBody.class));
    String stem = key.substring(0, key.length() - ".png".length());
    assertThat(puts.getAllValues())
        .extracting(PutObjectRequest::key)
        .containsExactly(key, stem + ".w1080.png", stem + ".w480.png");
    assertThat(puts.getAllValues())
        .allSatisfy(
            put -> {
              assertThat(put.bucket()).isEqualTo("remote-media-it");
              assertThat(put.contentType()).isEqualTo("image/png");
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT remote_url FROM remote_media WHERE storage_key = ?", String.class, key))
        .isEqualTo(photo);
    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM image_variant WHERE storage_key = ?", String.class, key))
        .isEqualTo("MADE");
    verify(fetcher, times(1)).fetch(anyString(), anyInt());
  }

  @Test
  void theSameAddressOnTwoNotesIsFetchedOnceAndShownOnBoth() {
    String photo = picture("shared.png");
    when(fetcher.fetch(photo, MAX_BYTES)).thenReturn(new Image(PNG, "image/png"));

    long first = receive("one", List.of(new RemoteNotes.Media(photo, null, "image/png")));
    long second = receive("two", List.of(new RemoteNotes.Media(photo, null, "image/png")));

    awaitShown(first, 0);
    awaitShown(second, 0);
    verify(fetcher, times(1)).fetch(photo, MAX_BYTES);
    verify(s3, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void aPictureTheOtherServerDoesNotHandOverStaysOnThatServer() {
    String photo = picture("gone.png");
    when(fetcher.fetch(photo, MAX_BYTES)).thenReturn(new Refused("HTTP 404"));

    long note = receive("refused", List.of(new RemoteNotes.Media(photo, null, "image/png")));

    verify(fetcher, timeout(10_000)).fetch(photo, MAX_BYTES);
    settle();
    Map<String, Object> row = row(note, 0);
    assertThat(row.get("url")).isEqualTo(photo);
    assertThat(row.get("storage_key")).isEqualTo("");
    assertThat(row.get("remote_url")).isNull();
    assertThat(copies(key(photo))).isZero();
    verify(s3, times(0)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void anUpdateThatKeepsThePictureKeepsTheCopyWithoutFetchingAgain() {
    String photo = picture("kept.png");
    when(fetcher.fetch(photo, MAX_BYTES)).thenReturn(new Image(PNG, "image/png"));
    long note = receive("before", List.of(new RemoteNotes.Media(photo, "alt", "image/png")));
    awaitShown(note, 0);

    remoteNotes.revise(
        actor,
        note,
        new RemoteNotes.Revision(
            "after",
            null,
            false,
            Instant.now().plusSeconds(1),
            List.of(new RemoteNotes.Media(photo, "alt", "image/png")),
            null,
            RemoteNotes.Shape.NOTE,
            null,
            List.of()));
    settle();

    assertThat(row(note, 0).get("url")).isEqualTo("https://cdn.example/" + key(photo));
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, note))
        .isEqualTo("after");
    verify(fetcher, times(1)).fetch(photo, MAX_BYTES);
  }

  @Test
  void aCopyOlderThanThirtyDaysGoesAndItsRowsPointBackAtTheOtherServer() {
    String old = picture("old.png");
    String fresh = picture("fresh.png");
    when(fetcher.fetch(anyString(), eq(MAX_BYTES))).thenReturn(new Image(PNG, "image/png"));
    long oldNote = receive("old", List.of(new RemoteNotes.Media(old, null, "image/png")));
    long freshNote = receive("fresh", List.of(new RemoteNotes.Media(fresh, null, "image/png")));
    awaitShown(oldNote, 0);
    awaitShown(freshNote, 0);
    jdbc.update(
        "UPDATE remote_media SET cached_at = cached_at - INTERVAL 31 DAY WHERE storage_key = ?",
        key(old));

    assertThat(queue.sweep()).isTrue();
    await(() -> copies(key(old)) == 0);

    Map<String, Object> reverted = row(oldNote, 0);
    assertThat(reverted.get("url")).isEqualTo(old);
    assertThat(reverted.get("storage_key")).isEqualTo("");
    assertThat(reverted.get("remote_url")).isNull();
    assertThat(deleted()).contains(key(old), variant(key(old), 1080), variant(key(old), 480));
    assertThat(variantsRecorded(key(old))).isZero();
    assertThat(row(freshNote, 0).get("storage_key")).isEqualTo(key(fresh));
    assertThat(copies(key(fresh))).isOne();
    assertThat(deleted()).doesNotContain(key(fresh));
  }

  @Test
  void aNoteThatPicksUpAnAgingCopyKeepsShowingItAfterTheNextSweep() {
    String photo = picture("again.png");
    when(fetcher.fetch(photo, MAX_BYTES)).thenReturn(new Image(PNG, "image/png"));
    long first = receive("first", List.of(new RemoteNotes.Media(photo, null, "image/png")));
    awaitShown(first, 0);
    jdbc.update(
        "UPDATE remote_media SET cached_at = cached_at - INTERVAL 31 DAY WHERE storage_key = ?",
        key(photo));

    long second = receive("second", List.of(new RemoteNotes.Media(photo, null, "image/png")));
    awaitShown(second, 0);
    assertThat(queue.sweep()).isTrue();
    settle();

    assertThat(row(second, 0).get("url")).isEqualTo("https://cdn.example/" + key(photo));
    assertThat(copies(key(photo))).isOne();
    verify(fetcher, times(1)).fetch(photo, MAX_BYTES);
  }

  @Test
  void aCopyGoesWhenNoNoteShowsItAnyMore() {
    String shared = picture("both.png");
    String alone = picture("alone.png");
    when(fetcher.fetch(anyString(), eq(MAX_BYTES))).thenReturn(new Image(PNG, "image/png"));
    long keeper = receive("keeper", List.of(new RemoteNotes.Media(shared, null, "image/png")));
    long leaver =
        receive(
            "leaver",
            List.of(
                new RemoteNotes.Media(shared, null, "image/png"),
                new RemoteNotes.Media(alone, null, "image/png")));
    awaitShown(keeper, 0);
    awaitShown(leaver, 0);
    awaitShown(leaver, 1);

    assertThat(remoteNotes.retract(actor, uri("leaver"))).isTrue();
    assertThat(queue.sweep()).isTrue();
    await(() -> copies(key(alone)) == 0);

    assertThat(deleted()).contains(key(alone)).doesNotContain(key(shared));
    assertThat(copies(key(shared))).isOne();
    assertThat(row(keeper, 0).get("storage_key")).isEqualTo(key(shared));
  }

  private long receive(String name, List<RemoteNotes.Media> media) {
    return remoteNotes
        .receive(
            new RemoteNotes.Received(
                actor,
                uri(name),
                null,
                name,
                null,
                false,
                NoteVisibility.PUBLIC,
                null,
                null,
                null,
                List.of(),
                media,
                null,
                RemoteNotes.Shape.NOTE,
                false,
                null,
                List.of()))
        .orElseThrow();
  }

  private String uri(String name) {
    return "https://rm.example/users/" + run + "/statuses/" + name;
  }

  private String picture(String name) {
    return "https://files.rm.example/" + run + "/original/" + name;
  }

  private static String key(String url) {
    return RemoteMediaKeys.keyOf(url, "image/png");
  }

  private static String variant(String key, int width) {
    return key.substring(0, key.lastIndexOf('.')) + ".w" + width + ".png";
  }

  private Map<String, Object> row(long note, int position) {
    return jdbc.queryForMap(
        "SELECT url, storage_key, remote_url FROM note_media WHERE note_id = ? AND position = ?",
        note,
        position);
  }

  private long copies(String key) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM remote_media WHERE storage_key = ?", Long.class, key);
  }

  private long variantsRecorded(String key) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM image_variant WHERE storage_key = ?", Long.class, key);
  }

  private List<String> deleted() {
    ArgumentCaptor<DeleteObjectRequest> deletes =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3, atLeastOnce()).deleteObject(deletes.capture());
    return deletes.getAllValues().stream().map(DeleteObjectRequest::key).toList();
  }

  private void awaitShown(long note, int position) {
    await(() -> !"".equals(row(note, position).get("storage_key")));
  }

  private void settle() {
    String marker = picture("marker.png");
    when(fetcher.fetch(marker, MAX_BYTES)).thenReturn(new Refused("marker"));
    receive("marker", List.of(new RemoteNotes.Media(marker, null, "image/png")));
    verify(fetcher, timeout(10_000)).fetch(marker, MAX_BYTES);
  }

  private static void await(BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      assertThat(System.nanoTime()).as("waited 10s").isLessThan(deadline);
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  private static byte[] png(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

package com.example.short_link.note.application.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class RemoteMediaCopiesTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RemoteMediaCopies copies = new RemoteMediaCopies(events);

  @Test
  void savedJpegAndPngRowsOverHttpsAreAnnouncedForCopying() {
    copies.request(
        List.of(
            row(1L, "https://m.example/a.png", "image/png"),
            row(2L, "https://m.example/b.gif", "image/gif"),
            row(3L, "http://m.example/c.jpg", "image/jpeg"),
            row(4L, "https://m.example/d.jpg", "IMAGE/JPEG"),
            row(5L, "https://m.example/e.mp4", "video/mp4")));

    verify(events)
        .publishEvent(
            new RemoteImagesSaved(
                List.of(
                    new RemoteImage(1L, "https://m.example/a.png", "image/png"),
                    new RemoteImage(4L, "https://m.example/d.jpg", "image/jpeg"))));
  }

  @Test
  void nothingToCopyAnnouncesNothing() {
    copies.request(List.of());
    copies.request(List.of(row(2L, "https://m.example/b.gif", "image/gif")));

    verify(events, never()).publishEvent(any(Object.class));
  }

  private static NoteMediaEntity row(Long id, String url, String type) {
    NoteMediaEntity row = new NoteMediaEntity(9L, 0, "", url, type, null);
    ReflectionTestUtils.setField(row, "id", id);
    return row;
  }
}

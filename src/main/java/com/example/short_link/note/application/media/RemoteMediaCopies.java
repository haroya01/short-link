package com.example.short_link.note.application.media;

import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemoteMediaCopies {

  private final ApplicationEventPublisher events;

  public void request(List<NoteMediaEntity> saved) {
    List<RemoteImage> images =
        saved.stream()
            .filter(
                row ->
                    row.getId() != null
                        && RemoteMediaKeys.copyable(row.getUrl(), row.getContentType()))
            .map(
                row ->
                    new RemoteImage(
                        row.getId(), row.getUrl(), RemoteMediaKeys.type(row.getContentType())))
            .toList();
    if (!images.isEmpty()) {
      events.publishEvent(new RemoteImagesSaved(images));
    }
  }
}

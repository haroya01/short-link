package com.example.short_link.federation.presentation;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.NoteDocuments;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteObjectController {

  private final NoteSnapshotReader notes;
  private final FederationActorService actors;
  private final NoteDocuments documents;
  private final FederationUrls urls;

  @GetMapping("/ap/notes/{id}")
  public ResponseEntity<Map<String, Object>> note(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
    return notes
        .find(id)
        .filter(
            note ->
                note.visibility() == NoteSnapshotReader.Visibility.PUBLIC
                    || note.visibility() == NoteSnapshotReader.Visibility.UNLISTED)
        .flatMap(
            note ->
                actors
                    .byUsername(note.authorUsername())
                    .map(
                        actor -> {
                          if (ActivityPubMedia.wantsHtml(accept)) {
                            return ResponseEntity.status(302)
                                .location(URI.create(urls.notePage(note.authorUsername(), id)))
                                .<Map<String, Object>>build();
                          }
                          Map<String, Object> body = new LinkedHashMap<>();
                          body.put("@context", ActivityPubMedia.CONTEXT);
                          body.putAll(documents.note(note, actor.publicId()));
                          return ResponseEntity.ok()
                              .contentType(ActivityPubMedia.ACTIVITY_JSON)
                              .cacheControl(
                                  CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                              .body(body);
                        }))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}

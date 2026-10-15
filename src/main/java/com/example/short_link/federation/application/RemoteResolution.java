package com.example.short_link.federation.application;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class RemoteResolution {

  private final RemoteAccountFinder finder;
  private final RemoteDocuments documents;
  private final RemoteActorResolver resolver;
  private final RemoteNoteIntake notes;
  private final RemoteFollowing following;
  private final NoteViewReader noteViews;
  private final FederationUrls urls;

  public record Resolved(RemoteAccountView account, Object note) {}

  public Resolved resolve(Long viewerId, String query) {
    String asked = query == null ? "" : query.strip();
    String lower = asked.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
      return account(viewerId, finder.resolve(asked));
    }
    URI uri = address(asked);
    if (urls.isOurs(asked)) {
      throw RemoteDocuments.notFound(asked);
    }
    JsonNode document = documents.fetch(uri, asked);
    if (RemoteNoteParser.kindOf(document).isPresent()) {
      Long noteId = notes.fetched(document, uri).orElseThrow(() -> RemoteDocuments.notFound(asked));
      return new Resolved(
          null,
          noteViews.view(noteId, viewerId).orElseThrow(() -> RemoteDocuments.notFound(asked)));
    }
    return account(
        viewerId,
        resolver.stored(document, uri).orElseThrow(() -> RemoteDocuments.notFound(asked)));
  }

  private Resolved account(Long viewerId, RemoteActorEntity actor) {
    return new Resolved(following.view(viewerId, actor), null);
  }

  private static URI address(String asked) {
    try {
      URI uri = URI.create(asked);
      if (uri.getHost() != null) {
        return uri;
      }
    } catch (IllegalArgumentException malformed) {
      throw new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
    }
    throw new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
  }
}

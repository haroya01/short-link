package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.Signer;
import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;

// Implementations sign every request and only connect to public addresses.
public interface FederationHttp {

  Result get(URI uri, Signer signer);

  // The same GET, giving up once the server has taken the given time to answer.
  Result get(URI uri, Signer signer, Duration patience);

  // A server's own JSON API (not ActivityPub), with the same patience.
  Result getJson(URI uri, Signer signer, Duration patience);

  // A JRD document. Redirects are followed one signed hop at a time, only to https hosts the
  // predicate allows; every hop, the first included, is asked of it.
  Result webFinger(URI uri, Signer signer, Predicate<String> allowedHost);

  Result post(URI inbox, byte[] body, Signer signer);

  sealed interface Result {
    record Ok(int status, byte[] body, String contentType) implements Result {

      public Ok(int status, byte[] body) {
        this(status, body, null);
      }

      public boolean activityStreams() {
        return ActivityStreams.isDocumentType(contentType);
      }
    }

    record Failed(int status, String reason) implements Result {

      public boolean deniedAccess() {
        return status == 401 || status == 403;
      }
    }

    record Refused(String reason) implements Result {}

    record Unreachable(String reason) implements Result {}
  }
}

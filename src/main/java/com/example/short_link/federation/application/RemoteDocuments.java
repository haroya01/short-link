package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteDocuments {

  private static final int TOO_MANY_REQUESTS = 429;

  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final JsonMapper json;

  public JsonNode fetch(URI uri, String asked) {
    if (serverBlocks.suspends(uri.getHost())) {
      throw notFound(asked);
    }
    FederationHttp.Result result = http.get(uri, signingKeys.forInstance());
    if (result instanceof FederationHttp.Result.Ok ok) {
      return document(ok.body(), asked);
    }
    log.info("federation resolve outcome={} uri={}", result, uri);
    if (result instanceof FederationHttp.Result.Unreachable
        || (result instanceof FederationHttp.Result.Failed failed
            && (failed.status() >= 500 || failed.status() == TOO_MANY_REQUESTS))) {
      throw new FederationException(FederationErrorCode.REMOTE_SERVER_UNREACHABLE, uri.getHost());
    }
    throw notFound(asked);
  }

  private JsonNode document(byte[] body, String asked) {
    try {
      JsonNode document = json.readTree(body);
      if (document != null && document.isObject()) {
        return document;
      }
    } catch (JacksonException malformed) {
      log.info("federation resolve malformed asked={}", asked);
    }
    throw notFound(asked);
  }

  static FederationException notFound(String asked) {
    return new FederationException(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, asked);
  }
}

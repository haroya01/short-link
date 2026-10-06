package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.Signer;
import java.net.URI;

// Outbound ActivityPub HTTP. Implementations sign every request and only connect to public
// addresses.
public interface FederationHttp {

  Result get(URI uri, Signer signer);

  Result post(URI inbox, byte[] body, Signer signer);

  sealed interface Result {
    record Ok(int status, byte[] body) implements Result {}

    record Failed(int status, String reason) implements Result {}

    record Refused(String reason) implements Result {}

    record Unreachable(String reason) implements Result {}
  }
}

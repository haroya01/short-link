package com.example.short_link.federation.application.inbox;

import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.PemKeys;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SignedInboxRequests {

  private SignedInboxRequests() {}

  public static Map<String, String> headers(
      String keyId,
      String privateKeyPem,
      String host,
      String path,
      byte[] body,
      Instant date,
      List<String> signed) {
    Map<String, String> values = new HashMap<>();
    values.put(HttpSignatures.REQUEST_TARGET, HttpSignatures.requestTarget("POST", path, null));
    values.put("host", host);
    values.put("date", HttpSignatures.httpDate(date));
    values.put("digest", HttpSignatures.digest(body));
    Map<String, String> headers = new HashMap<>();
    headers.put("date", values.get("date"));
    headers.put("digest", values.get("digest"));
    headers.put(
        "signature",
        HttpSignatures.sign(keyId, PemKeys.privateKey(privateKeyPem), signed, values::get));
    return headers;
  }

  public static Map<String, String> headers(
      String keyId, String privateKeyPem, String host, String path, byte[] body, Instant date) {
    return headers(keyId, privateKeyPem, host, path, body, date, HttpSignatures.POST_HEADERS);
  }
}

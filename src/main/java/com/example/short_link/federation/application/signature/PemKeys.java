package com.example.short_link.federation.application.signature;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

public final class PemKeys {

  private PemKeys() {}

  public static Optional<PublicKey> publicKey(String pem) {
    try {
      return Optional.of(
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(der(pem, "PUBLIC KEY"))));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static PrivateKey privateKey(String pem) {
    try {
      return KeyFactory.getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(der(pem, "PRIVATE KEY")));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("stored private key is unreadable", e);
    }
  }

  private static byte[] der(String pem, String label) {
    if (pem == null || !pem.contains("-----BEGIN " + label + "-----")) {
      throw new IllegalArgumentException("not a " + label + " PEM");
    }
    String body =
        pem.replace("-----BEGIN " + label + "-----", "")
            .replace("-----END " + label + "-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}

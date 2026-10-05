package com.example.short_link.federation.application;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

// Mastodon and Misskey verify rsa-sha256 signatures; RSA 2048 is what they issue themselves.
@Component
public class ActorKeys {

  private static final int BITS = 2048;

  public Pem generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(BITS);
      KeyPair pair = generator.generateKeyPair();
      return new Pem(
          pem("PUBLIC KEY", pair.getPublic().getEncoded()),
          pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA is unavailable", e);
    }
  }

  static String pem(String label, byte[] der) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
  }

  public record Pem(String publicKey, String privateKey) {}
}

package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ActorKeysTest {

  private static byte[] der(String pem) {
    String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }

  @Test
  void generatesAPemPairThatSignsAndVerifies() throws Exception {
    ActorKeys.Pem pem = new ActorKeys().generate();

    assertThat(pem.publicKey()).startsWith("-----BEGIN PUBLIC KEY-----\n");
    assertThat(pem.publicKey()).endsWith("-----END PUBLIC KEY-----\n");
    assertThat(pem.privateKey()).startsWith("-----BEGIN PRIVATE KEY-----\n");
    assertThat(pem.publicKey().lines().skip(1).findFirst().orElseThrow()).hasSize(64);

    KeyFactory rsa = KeyFactory.getInstance("RSA");
    var publicKey = rsa.generatePublic(new X509EncodedKeySpec(der(pem.publicKey())));
    var privateKey = rsa.generatePrivate(new PKCS8EncodedKeySpec(der(pem.privateKey())));
    assertThat(((RSAPublicKey) publicKey).getModulus().bitLength()).isEqualTo(2048);

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update("hello".getBytes());
    byte[] signature = signer.sign();
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(publicKey);
    verifier.update("hello".getBytes());
    assertThat(verifier.verify(signature)).isTrue();
  }
}

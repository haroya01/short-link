package com.example.short_link.user.application.twofactor;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Generates the codes shown once to the user and matches them against stored password hashes. */
@Component
class RecoveryCodes {

  static final int COUNT = 10;
  private static final int LENGTH = 10;
  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private final SecureRandom random = new SecureRandom();
  private final PasswordEncoder encoder;

  RecoveryCodes(@Qualifier("recoveryCodePasswordEncoder") PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  List<String> generate() {
    List<String> codes = new ArrayList<>(COUNT);
    for (int i = 0; i < COUNT; i++) {
      StringBuilder code = new StringBuilder(LENGTH + 1);
      for (int j = 0; j < LENGTH; j++) {
        if (j == LENGTH / 2) code.append('-');
        code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      }
      codes.add(code.toString());
    }
    return codes;
  }

  List<String> hashAll(List<String> plainCodes) {
    return plainCodes.stream().map(encoder::encode).toList();
  }

  Optional<String> matchingHash(List<String> storedHashes, String suppliedCode) {
    if (suppliedCode == null || suppliedCode.isBlank()) return Optional.empty();
    String normalized = suppliedCode.trim().toUpperCase(Locale.ROOT);
    return storedHashes.stream().filter(hash -> encoder.matches(normalized, hash)).findFirst();
  }
}

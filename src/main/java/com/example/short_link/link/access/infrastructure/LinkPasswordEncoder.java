package com.example.short_link.link.access.infrastructure;

import com.example.short_link.common.crypto.DigestedBcrypt;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

final class LinkPasswordEncoder implements PasswordEncoder {

  private static final int LONGEST_UNDIGESTED_BYTES = 72;

  private final PasswordEncoder digested;
  private final PasswordEncoder undigested;

  LinkPasswordEncoder(int strength) {
    this.digested = new DigestedBcrypt(strength);
    this.undigested = new BCryptPasswordEncoder(strength);
  }

  @Override
  public String encode(CharSequence raw) {
    return digested.encode(raw);
  }

  @Override
  public boolean matches(CharSequence raw, String hash) {
    return digested.matches(raw, hash)
        || (couldHaveBeenStoredUndigested(raw) && undigested.matches(raw, hash));
  }

  private static boolean couldHaveBeenStoredUndigested(CharSequence raw) {
    return raw != null
        && raw.toString().getBytes(StandardCharsets.UTF_8).length <= LONGEST_UNDIGESTED_BYTES;
  }
}

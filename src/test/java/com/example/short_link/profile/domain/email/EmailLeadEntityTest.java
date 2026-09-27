package com.example.short_link.profile.domain.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailLeadEntityTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not-an-email", "a@b", "a b@example.com"})
  void cannotConstructAnInvalidAddressWithoutTheService(String email) {
    assertInvalid(email);
  }

  @Test
  void rejectsAnAddressLongerThanTheStorageLimit() {
    assertInvalid("a".repeat(249) + "@x.com");
  }

  @Test
  void acceptsAndNormalizesAnAddressAtTheStorageLimit() {
    String email = "A".repeat(248) + "@X.COM";
    EmailLeadEntity lead = new EmailLeadEntity(1L, 2L, "  " + email + "  ", null);

    assertThat(lead.getEmail()).hasSize(254).isEqualTo(email.toLowerCase(Locale.ROOT));
  }

  @Test
  @ResourceLock(Resources.LOCALE)
  void addressIdentityDoesNotDependOnTheServerLanguage() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      EmailLeadEntity lead = new EmailLeadEntity(1L, 2L, "  INFO@EXAMPLE.COM  ", null);

      assertThat(lead.getEmail()).isEqualTo("info@example.com");
    } finally {
      Locale.setDefault(previous);
    }
  }

  private static void assertInvalid(String email) {
    assertThatThrownBy(() -> new EmailLeadEntity(1L, 2L, email, null))
        .isInstanceOfSatisfying(
            ProfileException.class,
            failure -> assertThat(failure.errorCode()).isEqualTo(ProfileErrorCode.INVALID_EMAIL));
  }
}

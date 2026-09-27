package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkExpiryFilter;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.presentation.request.MyLinksRequest;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.LOCALE)
class LocaleIndependentInputsTest {
  private Locale previous;

  @BeforeEach
  void useTurkishServerLocale() {
    previous = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(previous);
  }

  @Test
  void destinationCountryAndDeviceKeepTheirProtocolValues() {
    LinkDestinationEntity destination =
        new LinkDestinationEntity(
            new LinkId(1L), "https://example.com", 1, "India mobile", "in", "MOBILE", "WINDOWS");

    assertThat(destination.getCountryCode()).isEqualTo("IN");
    assertThat(destination.getDeviceClass()).isEqualTo("mobile");
    assertThat(destination.getOs()).isEqualTo("windows");
  }

  @Test
  void requestFiltersKeepTheirMeaning() {
    var query = MyLinksRequest.builder().expiry("active").sort("CLICK_COUNT").build().toQuery(20);

    assertThat(query.expiry()).isEqualTo(LinkExpiryFilter.ACTIVE);
    assertThat(query.sort()).isEqualTo(SortKey.CLICK_COUNT);
  }
}

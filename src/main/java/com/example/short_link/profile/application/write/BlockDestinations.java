package com.example.short_link.profile.application.write;

import com.example.short_link.common.security.OutboundUrlPolicy;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.contact.ProductCardCarousel;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BlockDestinations {

  private final OutboundUrlPolicy outbound;

  void requireAllowed(ProfileBlockType type, String validatedContent) {
    if (type != ProfileBlockType.PRODUCT_CARD) {
      return;
    }
    for (String url : ProductCardCarousel.buyUrls(validatedContent)) {
      if (!outbound.allows(url)) {
        throw new ProfileException(ProfileErrorCode.MALICIOUS_URL);
      }
    }
  }
}

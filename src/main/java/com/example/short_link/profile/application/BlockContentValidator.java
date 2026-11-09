package com.example.short_link.profile.application;

import com.example.short_link.profile.application.email.EmailFormConfig;
import com.example.short_link.profile.application.oembed.EmbedProvider;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.contact.BlockConfig;
import com.example.short_link.profile.domain.contact.Booking;
import com.example.short_link.profile.domain.contact.ContactCard;
import com.example.short_link.profile.domain.contact.Event;
import com.example.short_link.profile.domain.contact.Gallery;
import com.example.short_link.profile.domain.contact.Place;
import com.example.short_link.profile.domain.contact.ProductCardCarousel;
import com.example.short_link.profile.domain.contact.TextBlockBody;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;

public final class BlockContentValidator {

  private BlockContentValidator() {}

  public static String validate(ProfileBlockType type, String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    return switch (type) {
      case DIVIDER -> null;
      case TEXT -> TextBlockBody.normalize(raw);
      case IMAGE -> validateImageUrl(trimmed);
      case EMBED -> validateEmbedUrl(trimmed);
      case EMAIL_FORM ->
          EmailFormConfig.normalize(within(trimmed, 2048, "email form config too long"));
      case CONTACT_CARD -> ContactCard.normalize(within(trimmed, 2048, "contact card too long"));
      case GALLERY -> Gallery.normalize(within(trimmed, 2048, "gallery too long"));
      case PRODUCT_CARD ->
          ProductCardCarousel.normalize(within(trimmed, 16384, "product card too long"));
      case BOOKING -> Booking.normalize(within(trimmed, 2048, "booking too long"));
      case EVENT -> Event.normalize(within(trimmed, 2048, "event too long"));
      case PLACE -> Place.normalize(within(trimmed, 2048, "place too long"));
    };
  }

  private static String within(String trimmed, int max, String tooLong) {
    if (trimmed.length() > max) {
      throw BlockConfig.invalid(tooLong);
    }
    return trimmed;
  }

  private static String validateImageUrl(String trimmed) {
    if (trimmed.isEmpty())
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url required");
    if (trimmed.length() > 2048)
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url too long");
    BlockConfig.requireHttpUrl(trimmed, "image url");
    return trimmed;
  }

  private static String validateEmbedUrl(String trimmed) {
    if (trimmed.isEmpty())
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "embed url required");
    if (trimmed.length() > 2048)
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "embed url too long");
    if (EmbedProvider.resolve(trimmed).isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "embed url: unsupported provider");
    }
    return trimmed;
  }
}

package com.example.short_link.profile.application;

import com.example.short_link.profile.application.email.EmailFormConfig;
import com.example.short_link.profile.application.oembed.EmbedProvider;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.contact.Booking;
import com.example.short_link.profile.domain.contact.ContactCard;
import com.example.short_link.profile.domain.contact.Event;
import com.example.short_link.profile.domain.contact.Gallery;
import com.example.short_link.profile.domain.contact.Place;
import com.example.short_link.profile.domain.contact.ProductCardCarousel;
import com.example.short_link.profile.domain.contact.TextBlockBody;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;

/**
 * Per-type validation + normalization for profile block content. Returns the trimmed value to
 * persist (null for types that don't carry content). Throws {@link ProfileException} on bad input —
 * controller turns it into 400.
 *
 * <p>Shared by {@code CreateBlockUseCase} and {@code UpdateBlockUseCase}.
 */
public final class BlockContentValidator {

  private BlockContentValidator() {}

  public static String validate(ProfileBlockType type, String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    return switch (type) {
      case DIVIDER -> null;
      case TEXT -> TextBlockBody.normalize(raw);
      case IMAGE -> {
        if (trimmed.isEmpty())
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url required");
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url too long");
        try {
          java.net.URI uri = java.net.URI.create(trimmed);
          String scheme = uri.getScheme();
          if (scheme == null
              || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ProfileException(
                ProfileErrorCode.INVALID_USERNAME, "image url must be http(s)");
          }
          if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url missing host");
          }
        } catch (IllegalArgumentException ex) {
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "image url malformed");
        }
        yield trimmed;
      }
      case EMBED -> {
        if (trimmed.isEmpty())
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "embed url required");
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "embed url too long");
        if (EmbedProvider.resolve(trimmed).isEmpty()) {
          throw new ProfileException(
              ProfileErrorCode.INVALID_USERNAME, "embed url: unsupported provider");
        }
        yield trimmed;
      }
      case EMAIL_FORM -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(
              ProfileErrorCode.INVALID_USERNAME, "email form config too long");
        yield EmailFormConfig.normalize(trimmed);
      }
      case CONTACT_CARD -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "contact card too long");
        yield ContactCard.normalize(trimmed);
      }
      case GALLERY -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "gallery too long");
        yield Gallery.normalize(trimmed);
      }
      case PRODUCT_CARD -> {
        if (trimmed.length() > 16384)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "product card too long");
        yield ProductCardCarousel.normalize(trimmed);
      }
      case BOOKING -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "booking too long");
        yield Booking.normalize(trimmed);
      }
      case EVENT -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event too long");
        yield Event.normalize(trimmed);
      }
      case PLACE -> {
        if (trimmed.length() > 2048)
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place too long");
        yield Place.normalize(trimmed);
      }
    };
  }
}

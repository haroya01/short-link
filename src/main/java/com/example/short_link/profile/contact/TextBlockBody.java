package com.example.short_link.profile.contact;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/**
 * TEXT block payload — markdown body plus optional visual hints (layout / accent / icon). The
 * payload migrated from "plain markdown string" to "JSON object" so the seller can pick a Toss-
 * style highlight box or a quote rail without us hijacking the markdown syntax for our own
 * directives. Reads tolerate both shapes:
 *
 * <ul>
 *   <li>JSON {@code {body, layout?, accent?, icon?}} — the new shape.
 *   <li>Plain string — the legacy shape, treated as {@code body} with all visual hints at their
 *       defaults ({@code layout=inline}, no accent, no icon).
 * </ul>
 *
 * <p>Writes always emit the JSON shape so the storage layer eventually converges. Markdown source
 * is preserved verbatim — rendering happens client-side via react-markdown which strips raw HTML by
 * default, so we don't need server-side sanitization.
 */
public final class TextBlockBody {

  /** Same 2000-char cap the v1 TEXT block enforced — protects the editor's row preview. */
  private static final int BODY_MAX = 2000;

  /** Single emoji or short symbol — used as the visual hook on the {@code card} layout. */
  private static final int ICON_MAX = 8;

  /**
   * Layouts: {@code inline} keeps the v1 inline-markdown rendering; {@code card} wraps the body in
   * a tinted box; {@code quote} adds an accent left-rail with indent. Unknown values fall back to
   * inline rather than reject so a frontend ahead of a backend deploy doesn't 400 every write.
   */
  private static final Set<String> LAYOUT_IDS = Set.of("inline", "card", "quote");

  /**
   * Accent palette ids. Mapped to fixed Tailwind shades on the frontend so the visual language is
   * consistent across locales / themes.
   */
  private static final Set<String> ACCENT_IDS = Set.of("blue", "amber", "green", "red", "violet");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TextBlockBody() {}

  /** New JSON shape. {@code body} required; rest optional. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String body, String layout, String accent, String icon) {}

  /**
   * Output record — always emits all four fields so the consumer never has to branch on absence.
   */
  private record PayloadOut(String body, String layout, String accent, String icon) {}

  /**
   * Validates and normalizes a TEXT block payload. Accepts the new JSON shape or a legacy plain
   * markdown string. Always returns a JSON-serialized {@link PayloadOut} so storage converges.
   */
  public static String normalize(String raw) {
    if (raw == null) {
      throw new InvalidUsernameException("text block content required");
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      throw new InvalidUsernameException("text block content required");
    }

    String body;
    String layout = "inline";
    String accent = null;
    String icon = null;

    // Try JSON first when the raw payload looks like an object literal — a plain markdown body
    // never starts with '{' (markdown's `{` has no semantic meaning at line start), so this prefix
    // check is a cheap discriminator without false positives in practice.
    if (trimmed.startsWith("{")) {
      try {
        Payload parsed = MAPPER.readValue(trimmed, Payload.class);
        if (parsed != null && parsed.body() != null) {
          body = parsed.body();
          layout = normalizeLayout(parsed.layout());
          accent = normalizeAccent(parsed.accent());
          icon = trimIcon(parsed.icon());
        } else {
          // Object with no body field — treat the whole raw string as legacy markdown.
          body = trimmed;
        }
      } catch (JsonProcessingException ex) {
        // Looked like JSON but didn't parse — treat as legacy markdown verbatim.
        body = trimmed;
      }
    } else {
      body = trimmed;
    }

    body = body.trim();
    if (body.isEmpty()) {
      throw new InvalidUsernameException("text block content required");
    }
    if (body.length() > BODY_MAX) {
      throw new InvalidUsernameException("text block too long");
    }

    try {
      return MAPPER.writeValueAsString(new PayloadOut(body, layout, accent, icon));
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("text block: serialization failed");
    }
  }

  private static String normalizeLayout(String raw) {
    if (raw == null) return "inline";
    String t = raw.trim();
    return LAYOUT_IDS.contains(t) ? t : "inline";
  }

  private static String normalizeAccent(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    return ACCENT_IDS.contains(t) ? t : null;
  }

  private static String trimIcon(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    // Cap at a few code points — enough for one emoji including ZWJ sequences like 👨‍💻 but
    // short enough that the chip stays visually compact.
    return t.length() <= ICON_MAX ? t : t.substring(0, ICON_MAX);
  }
}

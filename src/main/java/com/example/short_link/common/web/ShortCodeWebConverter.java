package com.example.short_link.common.web;

import com.example.short_link.link.domain.ShortCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets Spring MVC bind {@code @PathVariable ShortCode shortCode} and {@code @RequestParam ShortCode
 * ...} from plain strings. Validation (3..16 alphanumeric) happens in {@link ShortCode}'s
 * constructor — invalid input becomes {@link IllegalArgumentException}, which the global handler
 * maps to 400.
 */
@Configuration
public class ShortCodeWebConverter implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToShortCodeConverter());
  }

  private static final class StringToShortCodeConverter implements Converter<String, ShortCode> {
    @Override
    public ShortCode convert(String source) {
      return source == null || source.isBlank() ? null : new ShortCode(source);
    }
  }
}

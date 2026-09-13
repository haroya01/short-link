package com.example.short_link.link.web;

import com.example.short_link.link.domain.ShortCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Invalid {@link ShortCode} input throws {@link IllegalArgumentException}, which the global handler
 * maps to HTTP 400.
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

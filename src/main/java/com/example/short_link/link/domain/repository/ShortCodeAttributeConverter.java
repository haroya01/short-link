package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.ShortCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA bridge between {@link ShortCode} entity fields and the VARCHAR columns that back them. {@code
 * autoApply = true} so existing entity fields typed as {@code ShortCode} get the conversion
 * automatically without needing per-field {@code @Convert} annotations.
 */
@Converter(autoApply = true)
public class ShortCodeAttributeConverter implements AttributeConverter<ShortCode, String> {

  @Override
  public String convertToDatabaseColumn(ShortCode attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public ShortCode convertToEntityAttribute(String dbData) {
    return dbData == null ? null : new ShortCode(dbData);
  }
}

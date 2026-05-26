package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA bridge for non-{@code @Id} {@link LinkId} columns — FK columns like {@code
 * click_event.link_id}, {@code link_webhook.link_id}, etc. The primary key {@code link.id} itself
 * is intentionally left as raw {@code Long} so Hibernate's identity generation +
 * persistence-context cache stay on their proven path. {@code autoApply = true} picks up any entity
 * field typed as {@code LinkId} automatically.
 */
@Converter(autoApply = true)
public class LinkIdAttributeConverter implements AttributeConverter<LinkId, Long> {

  @Override
  public Long convertToDatabaseColumn(LinkId attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public LinkId convertToEntityAttribute(Long dbData) {
    return dbData == null ? null : new LinkId(dbData);
  }
}

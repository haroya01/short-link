package com.example.short_link.post.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

record OffsetLimit(long offset, int limit) implements Pageable {

  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return Sort.unsorted();
  }

  @Override
  public Pageable next() {
    return new OffsetLimit(offset + limit, limit);
  }

  @Override
  public Pageable previousOrFirst() {
    return new OffsetLimit(Math.max(offset - limit, 0), limit);
  }

  @Override
  public Pageable first() {
    return new OffsetLimit(0, limit);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return new OffsetLimit((long) pageNumber * limit, limit);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }

  @Override
  public Optional<Pageable> toOptional() {
    return Optional.of(this);
  }
}

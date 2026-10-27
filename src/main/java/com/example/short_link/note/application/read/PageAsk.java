package com.example.short_link.note.application.read;

import java.util.List;
import java.util.function.BiFunction;

public record PageAsk(int page, int size) {

  public static final int MAX_SIZE = 50;

  public PageAsk {
    page = Math.max(page, 0);
    size = Math.clamp(size, 1, MAX_SIZE);
  }

  public <R> Rows<R> fetch(BiFunction<Integer, Integer, List<R>> offsetLimit) {
    List<R> rows = offsetLimit.apply(page * size, size + 1);
    boolean hasNext = rows.size() > size;
    return new Rows<>(hasNext ? rows.subList(0, size) : rows, hasNext);
  }

  public record Rows<R>(List<R> current, boolean hasNext) {}
}

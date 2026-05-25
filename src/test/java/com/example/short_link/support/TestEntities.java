package com.example.short_link.support;

import java.lang.reflect.Field;

/**
 * Centralised entity-id reflection for tests. JPA's {@code @GeneratedValue(IDENTITY)} only fills
 * the id on flush, so tests that build entities in memory have nothing to assert against until they
 * save — except the ones that wire up object graphs (block + link + click + ...) where saving
 * everything to the DB just to get a foreign-key id back is overkill. Reflection lives in one
 * place; every test imports {@link #withId(Object, Long)} and the per-file {@code writeField}
 * helpers go away.
 *
 * <p>Production code still treats id as immutable — this only opens it for the test classpath.
 */
public final class TestEntities {

  private TestEntities() {}

  public static <T> T withId(T entity, Long id) {
    setField(entity, "id", id);
    return entity;
  }

  public static <T> T setField(T target, String name, Object value) {
    Field f = findField(target.getClass(), name);
    try {
      f.setAccessible(true);
      f.set(target, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot set " + name + " on " + target.getClass(), e);
    }
    return target;
  }

  private static Field findField(Class<?> cls, String name) {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new IllegalStateException("no field " + name + " on " + cls);
  }
}

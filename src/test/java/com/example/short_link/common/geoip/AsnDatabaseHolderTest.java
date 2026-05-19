package com.example.short_link.common.geoip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.maxmind.geoip2.DatabaseReader;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AsnDatabaseHolderTest {

  @Test
  void getOrNullEmptyReturnsNull() {
    assertThat(new AsnDatabaseHolder().getOrNull()).isNull();
  }

  @Test
  void setReplacesAndClosesPrevious() throws IOException {
    AsnDatabaseHolder holder = new AsnDatabaseHolder();
    DatabaseReader first = mock(DatabaseReader.class);
    DatabaseReader second = mock(DatabaseReader.class);
    holder.set(first);
    assertThat(holder.getOrNull()).isSameAs(first);
    holder.set(second);
    verify(first).close();
    assertThat(holder.getOrNull()).isSameAs(second);
  }

  @Test
  void setSwallowsCloseFailure() throws IOException {
    AsnDatabaseHolder holder = new AsnDatabaseHolder();
    DatabaseReader first = mock(DatabaseReader.class);
    doThrow(new IOException("boom")).when(first).close();
    holder.set(first);
    holder.set(mock(DatabaseReader.class));
  }

  @Test
  void onShutdownClosesReader() throws Exception {
    AsnDatabaseHolder holder = new AsnDatabaseHolder();
    DatabaseReader reader = mock(DatabaseReader.class);
    holder.set(reader);
    Method m = AsnDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
    verify(reader).close();
  }

  @Test
  void onShutdownNoOpWhenEmpty() throws Exception {
    AsnDatabaseHolder holder = new AsnDatabaseHolder();
    Method m = AsnDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
  }

  @Test
  void onShutdownSwallowsCloseFailure() throws Exception {
    AsnDatabaseHolder holder = new AsnDatabaseHolder();
    DatabaseReader reader = mock(DatabaseReader.class);
    doThrow(new IOException("boom")).when(reader).close();
    holder.set(reader);
    Method m = AsnDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
  }
}

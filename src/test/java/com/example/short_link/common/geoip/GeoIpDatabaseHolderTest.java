package com.example.short_link.common.geoip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.maxmind.geoip2.DatabaseReader;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GeoIpDatabaseHolderTest {

  @Test
  void getWithoutInitThrows() {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    assertThatThrownBy(holder::get).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void setAfterInitClosesPrevious() throws IOException {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    DatabaseReader first = mock(DatabaseReader.class);
    DatabaseReader second = mock(DatabaseReader.class);
    holder.set(first);
    assertThat(holder.get()).isSameAs(first);
    holder.set(second);
    verify(first).close();
    assertThat(holder.get()).isSameAs(second);
  }

  @Test
  void setSwallowsCloseFailure() throws IOException {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    DatabaseReader first = mock(DatabaseReader.class);
    doThrow(new IOException("boom")).when(first).close();
    holder.set(first);
    holder.set(mock(DatabaseReader.class));
  }

  @Test
  void onShutdownClosesReader() throws Exception {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    DatabaseReader reader = mock(DatabaseReader.class);
    holder.set(reader);
    Method m = GeoIpDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
    verify(reader).close();
  }

  @Test
  void onShutdownNoOpWhenEmpty() throws Exception {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    Method m = GeoIpDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
  }

  @Test
  void onShutdownSwallowsCloseFailure() throws Exception {
    GeoIpDatabaseHolder holder = new GeoIpDatabaseHolder();
    DatabaseReader reader = mock(DatabaseReader.class);
    doThrow(new IOException("boom")).when(reader).close();
    holder.set(reader);
    Method m = GeoIpDatabaseHolder.class.getDeclaredMethod("onShutdown");
    m.setAccessible(true);
    m.invoke(holder);
  }
}

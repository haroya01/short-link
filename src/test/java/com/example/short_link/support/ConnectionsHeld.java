package com.example.short_link.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

public final class ConnectionsHeld implements BeanPostProcessor {

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {
    @Bean
    static ConnectionsHeld connectionsHeld() {
      return new ConnectionsHeld();
    }
  }

  private static final class Count {
    private int now;
    private int most;
  }

  private final ThreadLocal<Count> counts = ThreadLocal.withInitial(Count::new);

  public void reset() {
    counts.remove();
  }

  public int mostAtOnceOnThisThread() {
    return counts.get().most;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof DataSource dataSource) {
      return proxy(
          DataSource.class,
          (self, method, args) -> {
            Object result = call(dataSource, method, args);
            if (result instanceof Connection connection
                && method.getReturnType() == Connection.class) {
              return counted(connection);
            }
            return result;
          });
    }
    return bean;
  }

  private Connection counted(Connection connection) {
    Count count = counts.get();
    count.now++;
    count.most = Math.max(count.most, count.now);
    AtomicBoolean open = new AtomicBoolean(true);
    return proxy(
        Connection.class,
        (self, method, args) -> {
          if (method.getName().equals("close") && open.compareAndSet(true, false)) {
            counts.get().now--;
          }
          return call(connection, method, args);
        });
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    InvocationHandler identityAware =
        (self, method, args) ->
            switch (method.getName()) {
              case "equals" -> self == args[0];
              case "hashCode" -> System.identityHashCode(self);
              default -> handler.invoke(self, method, args);
            };
    return type.cast(
        Proxy.newProxyInstance(
            ConnectionsHeld.class.getClassLoader(), new Class<?>[] {type}, identityAware));
  }

  private static Object call(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }
}

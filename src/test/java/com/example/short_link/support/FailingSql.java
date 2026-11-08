package com.example.short_link.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

public final class FailingSql implements BeanPostProcessor {

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {
    @Bean
    static FailingSql failingSql() {
      return new FailingSql();
    }
  }

  private record Rule(String fragment, Supplier<SQLException> error) {}

  private final List<Rule> rules = new CopyOnWriteArrayList<>();
  private final List<String> refused = new CopyOnWriteArrayList<>();

  public void refuse(String sqlFragment, Supplier<SQLException> error) {
    rules.add(new Rule(sqlFragment.toLowerCase(Locale.ROOT), error));
  }

  public List<String> refused() {
    return List.copyOf(refused);
  }

  public void reset() {
    rules.clear();
    refused.clear();
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof DataSource dataSource) {
      return wrap(DataSource.class, dataSource, null);
    }
    return bean;
  }

  private Object wrap(Class<?> type, Object target, String preparedSql) {
    InvocationHandler handler =
        (proxy, method, args) -> invoke(proxy, target, preparedSql, method, args);
    return Proxy.newProxyInstance(
        FailingSql.class.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private Object invoke(
      Object proxy, Object target, String preparedSql, Method method, Object[] args)
      throws Throwable {
    switch (method.getName()) {
      case "equals" -> {
        return proxy == args[0];
      }
      case "hashCode" -> {
        return System.identityHashCode(proxy);
      }
      default -> {}
    }
    if (target instanceof Statement && method.getName().startsWith("execute")) {
      String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : preparedSql;
      refuseIfArmed(sql);
    }
    Object result = call(target, method, args);
    Class<?> returned = method.getReturnType();
    if (result instanceof Connection && returned == Connection.class) {
      return wrap(Connection.class, result, null);
    }
    if (result instanceof Statement && Statement.class.isAssignableFrom(returned)) {
      String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
      return wrap(returned, result, sql);
    }
    return result;
  }

  private void refuseIfArmed(String sql) throws SQLException {
    if (sql == null) return;
    String lower = sql.toLowerCase(Locale.ROOT);
    for (Rule rule : rules) {
      if (lower.contains(rule.fragment())) {
        refused.add(sql);
        throw rule.error().get();
      }
    }
  }

  private static Object call(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }
}

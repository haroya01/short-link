package com.example.short_link.testsupport;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.query.QueryEnhancerFactories;
import org.springframework.data.jpa.repository.query.QueryEnhancerSelector;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;

/** Keeps QueryAudit's test-only JSqlParser dependency from changing JPA's native query handling. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class ProductionJpaParserTestConfiguration {

  @Bean
  static BeanPostProcessor preserveProductionJpaQueryParser() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof JpaRepositoryFactoryBean<?, ?, ?> repositoryFactory) {
          repositoryFactory.setQueryEnhancerSelectorSource(
              query -> {
                if (query.isNative()) {
                  return QueryEnhancerFactories.fallback();
                }
                return QueryEnhancerSelector.DEFAULT_SELECTOR.select(query);
              });
        }
        return bean;
      }
    };
  }
}

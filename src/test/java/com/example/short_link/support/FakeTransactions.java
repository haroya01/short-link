package com.example.short_link.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class FakeTransactions {

  private FakeTransactions() {}

  public static TransactionTemplate template() {
    return new TransactionTemplate(
        new AbstractPlatformTransactionManager() {
          @Override
          protected Object doGetTransaction() {
            return new Object();
          }

          @Override
          protected void doBegin(Object transaction, TransactionDefinition definition) {}

          @Override
          protected void doCommit(DefaultTransactionStatus status) {}

          @Override
          protected void doRollback(DefaultTransactionStatus status) {}
        });
  }

  public static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive() ? "inside" : "outside";
  }
}

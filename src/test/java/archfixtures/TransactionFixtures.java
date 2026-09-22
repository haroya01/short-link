package archfixtures;

import java.net.http.HttpClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Breaks the transaction rules on purpose, next to the calls that must stay unflagged. */
public final class TransactionFixtures {

  private TransactionFixtures() {}

  public static class RequiresNewCalledFromItsOwnClass {
    @Transactional
    public void outer() {
      inner();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {}
  }

  public static class TransactionalCalledFromPlainMethod {
    public void outer() {
      inner();
    }

    @Transactional
    public void inner() {}
  }

  public static class TransactionalCalledFromTransactionalMethod {
    @Transactional
    public void outer() {
      inner();
    }

    @Transactional(readOnly = true)
    public void inner() {}
  }

  public static class CallsNetworkInsideTransaction {
    private final HttpClient client = HttpClient.newHttpClient();

    @Transactional
    public Object run() {
      return client.version();
    }
  }

  public static class CallsNetworkOutsideTransaction {
    private final HttpClient client = HttpClient.newHttpClient();

    public Object run() {
      return client.version();
    }
  }
}

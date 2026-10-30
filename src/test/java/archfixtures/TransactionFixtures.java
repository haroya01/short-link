package archfixtures;

import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushSender;
import java.net.http.HttpClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Breaks the transaction rules on purpose, next to the calls that must stay unflagged.
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

  public static class Fetcher {
    private final HttpClient client = HttpClient.newHttpClient();

    public Object fetch() {
      return client.version();
    }
  }

  public interface Store {
    Object size();
  }

  public static class NetworkStore implements Store {
    private final Fetcher fetcher = new Fetcher();

    @Override
    public Object size() {
      return fetcher.fetch();
    }
  }

  public static class LaterFetcher {
    private final Fetcher fetcher = new Fetcher();

    @Async
    public void fetch() {
      fetcher.fetch();
    }
  }

  public static class ReachesNetworkThroughAHelperInsideTransaction {
    private final Fetcher fetcher = new Fetcher();

    @Transactional
    public Object run() {
      return checked(fetcher);
    }

    private Object checked(Fetcher fetcher) {
      return fetcher.fetch();
    }
  }

  public static class ReachesNetworkThroughAPortInsideTransaction {
    @Transactional
    public Object run(Store store) {
      return store.size();
    }
  }

  public static class ReachesNetworkBeforeTheTransaction {
    private final Fetcher fetcher = new Fetcher();

    public Object run() {
      return fetcher.fetch();
    }

    @Transactional
    public Object write(Object fetched) {
      return fetched;
    }
  }

  public static class HandsNetworkToAnotherThreadInsideTransaction {
    @Transactional
    public void run(LaterFetcher later) {
      later.fetch();
    }
  }

  public static class CallsNetworkInsideANewTransaction {
    private final Fetcher fetcher = new Fetcher();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object run() {
      return fetcher.fetch();
    }
  }

  public static class SuspendsTheTransactionToCallNetwork {
    private final Fetcher fetcher = new Fetcher();

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Object run() {
      return fetcher.fetch();
    }
  }

  public static class ReachesNetworkThroughASuspendingBeanInsideTransaction {
    @Transactional
    public Object run(SuspendsTheTransactionToCallNetwork suspending) {
      return suspending.run();
    }
  }

  public static class HandsAPushPastTheCommitInsideTransaction {
    @Transactional
    public void run(NotificationPushDelivery pushes) {
      pushes.send(1L, new PushSender.PushMessage("title", null, "body"));
    }
  }
}

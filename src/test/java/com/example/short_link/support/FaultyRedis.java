package com.example.short_link.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class FaultyRedis {

  private enum Mode {
    PASS,
    HANG,
    DROP
  }

  private static FaultyRedis line;

  private final String upstreamHost;
  private final int upstreamPort;
  private final ServerSocket listener;
  private final List<Socket> open = new CopyOnWriteArrayList<>();
  private final Object gate = new Object();
  private volatile Mode mode = Mode.PASS;

  private FaultyRedis(String upstreamHost, int upstreamPort) throws IOException {
    this.upstreamHost = upstreamHost;
    this.upstreamPort = upstreamPort;
    this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    daemon(this::accept);
  }

  public static synchronized FaultyRedis inFrontOfTheTestRedis() {
    if (line == null) {
      String host = System.getenv().getOrDefault("SPRING_DATA_REDIS_HOST", "localhost");
      int port = Integer.parseInt(System.getenv().getOrDefault("SPRING_DATA_REDIS_PORT", "6379"));
      try {
        line = new FaultyRedis(host, port);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return line;
  }

  public void route(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", () -> "127.0.0.1");
    registry.add("spring.data.redis.port", listener::getLocalPort);
  }

  public void hang() {
    mode = Mode.HANG;
  }

  public void drop() {
    synchronized (gate) {
      mode = Mode.DROP;
      gate.notifyAll();
    }
    for (Socket socket : open) {
      close(socket);
    }
    open.clear();
  }

  public void heal() {
    synchronized (gate) {
      mode = Mode.PASS;
      gate.notifyAll();
    }
  }

  private void accept() {
    while (true) {
      Socket client;
      try {
        client = listener.accept();
      } catch (IOException e) {
        return;
      }
      if (mode == Mode.DROP) {
        close(client);
        continue;
      }
      try {
        Socket upstream = new Socket(upstreamHost, upstreamPort);
        open.add(client);
        open.add(upstream);
        daemon(() -> pump(client, upstream));
        daemon(() -> pump(upstream, client));
      } catch (IOException e) {
        close(client);
      }
    }
  }

  private void pump(Socket from, Socket to) {
    byte[] buffer = new byte[8192];
    try (InputStream in = from.getInputStream()) {
      OutputStream out = to.getOutputStream();
      int read;
      while ((read = in.read(buffer)) >= 0) {
        waitWhileHung();
        out.write(buffer, 0, read);
        out.flush();
      }
    } catch (IOException | InterruptedException e) {
      close(from);
      close(to);
    }
  }

  private void waitWhileHung() throws InterruptedException {
    synchronized (gate) {
      while (mode == Mode.HANG) {
        gate.wait();
      }
    }
  }

  private static void close(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }

  private static void daemon(Runnable task) {
    Thread thread = new Thread(task, "faulty-redis");
    thread.setDaemon(true);
    thread.start();
  }
}

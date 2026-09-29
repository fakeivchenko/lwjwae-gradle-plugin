package dev.ivchenko.lwjwae.gradle.frontend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs the application against the development server of the frontend, with hot reload: starts
 * {@code npm run dev}, waits until the server answers, runs the application the way {@code run}
 * does with {@code LWJWAE_DEV_SERVER_URL} set, and stops the server when the application exits.
 *
 * <p>The server's output goes to the build log, line by line, so its errors show where Gradle's do.
 * The server is stopped with every process that it started: npm runs Vite in a process of its own,
 * which would otherwise keep the port.
 */
@DisableCachingByDefault(because = "Runs an application")
public abstract class RunDev extends JavaExec {
  private static final Duration STARTUP = Duration.ofSeconds(60);

  /** The npm project. */
  @Internal
  public abstract DirectoryProperty getDirectory();

  /** The script that starts the server. */
  @Input
  public abstract Property<String> getScript();

  /** Where the server serves the page. */
  @Input
  public abstract Property<String> getDevServerUrl();

  /** Starts the server, runs the application, and stops the server. */
  @Override
  @TaskAction
  public void exec() {
    Process server;
    try {
      server =
          new ProcessBuilder(Npm.command("run", this.getScript().get()))
              .directory(this.getDirectory().get().getAsFile())
              .redirectErrorStream(true)
              .start();
    } catch (IOException e) {
      throw new GradleException("Could not start the development server: " + e.getMessage(), e);
    }
    Thread.ofVirtual().start(() -> this.log(server));
    try {
      String url = this.getDevServerUrl().get();
      RunDev.waitFor(url, server);
      this.environment("LWJWAE_DEV_SERVER_URL", url);
      super.exec();
    } finally {
      server.descendants().forEach(ProcessHandle::destroy);
      server.destroy();
    }
  }

  private void log(Process server) {
    try (BufferedReader lines =
        new BufferedReader(
            new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
      for (String line = lines.readLine(); line != null; line = lines.readLine()) {
        this.getLogger().lifecycle("[{}] {}", this.getScript().get(), line);
      }
    } catch (IOException _) {
      // The server stopped; its exit is reported by waitFor, or it was stopped on purpose.
    }
  }

  /**
   * Waits until something listens at the host and port of {@code url}, or {@code server} exits, or
   * the time is up.
   *
   * <p>Every address of the host counts: Node.js 17 and later resolve {@code localhost} to {@code
   * ::1} first, so Vite listens there alone, and a client that tries {@code 127.0.0.1} would wait
   * for a server that is up. The web engine of the window tries both.
   */
  private static void waitFor(String url, Process server) {
    URI uri = URI.create(url);
    int port = uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
    Instant deadline = Instant.now().plus(STARTUP);
    try {
      while (Instant.now().isBefore(deadline)) {
        if (!server.isAlive()) {
          throw new GradleException(
              "The development server exited with "
                  + server.exitValue()
                  + " before it served "
                  + url);
        }
        for (InetAddress address : RunDev.addresses(uri.getHost())) {
          try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 500);
            return;
          } catch (IOException _) {
            // Not this address, or not yet.
          }
        }
        Thread.sleep(250);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GradleException("Interrupted while waiting for the development server", e);
    }
    throw new GradleException(
        "The development server didn't serve "
            + url
            + " within "
            + STARTUP.toSeconds()
            + " seconds");
  }

  private static InetAddress[] addresses(String host) {
    try {
      return InetAddress.getAllByName(host);
    } catch (UnknownHostException _) {
      return new InetAddress[0];
    }
  }
}

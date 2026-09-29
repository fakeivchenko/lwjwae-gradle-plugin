package dev.ivchenko.lwjwae.gradle.frontend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

  /** Waits until {@code url} answers, or {@code server} exits, or the time is up. */
  private static void waitFor(String url, Process server) {
    Instant deadline = Instant.now().plus(STARTUP);
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).build();
      while (Instant.now().isBefore(deadline)) {
        if (!server.isAlive()) {
          throw new GradleException(
              "The development server exited with "
                  + server.exitValue()
                  + " before it served "
                  + url);
        }
        try {
          client.send(request, HttpResponse.BodyHandlers.discarding());
          return;
        } catch (IOException _) {
          Thread.sleep(250);
        }
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
}

package dev.ivchenko.lwjwae.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * The versions of the library and the codecs that a project is pinned to, in {@code
 * lwjwae.properties} at the root of the build.
 *
 * <p>A project builds with the same versions until someone moves it on, whatever release came out
 * in the meantime and whichever version of the plugin builds it: a release of lwjwae can change its
 * API, and a build that picked it up on its own would break without a change of its own. The file
 * belongs in the repository of the project, next to the build script.
 *
 * <p>The first build without the file pins the newest releases that the repository of lwjwae names,
 * so a new project starts on the current version. An offline build, or one that can't reach the
 * repository, pins the versions that the plugin was released with instead. {@code lwjwaeUpgrade}
 * moves the project to the newest releases later. A version set in the {@code lwjwae {}} block wins
 * over the file.
 */
@UtilityClass
class PinnedVersions {
  /** The name of the file at the root of the build. */
  final String FILE = "lwjwae.properties";

  /** The Gradle property that names another repository, such as a mirror. */
  final String REPOSITORY_PROPERTY = "lwjwae.repository";

  private final String REPOSITORY = "https://repo.ivchenko.dev/releases";
  private final String LIBRARY_METADATA = "/dev/ivchenko/lwjwae/lwjwae-core/maven-metadata.xml";
  private final String CODECS_METADATA =
      "/dev/ivchenko/lwjwae/codec/lwjwae-codec-jackson/maven-metadata.xml";
  private final String LIBRARY_KEY = "version";
  private final String CODECS_KEY = "codecsVersion";
  private final int TIMEOUT_MILLIS = 5000;
  private final Pattern RELEASE = Pattern.compile("<release>\\s*([^<\\s]+)\\s*</release>");
  private final Logger LOG = Logging.getLogger(PinnedVersions.class);

  /**
   * The versions that {@code project} is pinned to: the ones of the file, or, without one, the
   * newest releases, which it writes into a new file.
   */
  Versions pinned(Project project) {
    Path file = PinnedVersions.file(project);
    if (Files.isRegularFile(file)) {
      return PinnedVersions.read(file);
    }
    Versions newest = PinnedVersions.newest(project);
    PinnedVersions.write(file, newest);
    LOG.lifecycle(
        "lwjwae: pinned lwjwae {} and lwjwae-codecs {} in {}",
        newest.library(),
        newest.codecs(),
        FILE);
    return newest;
  }

  /** Pins {@code project} to the newest releases, and tells what changed. */
  void upgrade(Project project) {
    Path file = PinnedVersions.file(project);
    Versions old = Files.isRegularFile(file) ? PinnedVersions.read(file) : null;
    Versions newest = PinnedVersions.newest(project);
    PinnedVersions.write(file, newest);
    if (newest.equals(old)) {
      LOG.lifecycle(
          "lwjwae: already on the newest releases, lwjwae {} and lwjwae-codecs {}",
          newest.library(),
          newest.codecs());
      return;
    }
    LOG.lifecycle(
        "lwjwae: lwjwae {} -> {}, lwjwae-codecs {} -> {}",
        old == null ? "none" : old.library(),
        newest.library(),
        old == null ? "none" : old.codecs(),
        newest.codecs());
  }

  private Path file(Project project) {
    return project.getRootProject().getProjectDir().toPath().resolve(FILE);
  }

  /** The newest releases, or the versions of the plugin for each that the repository won't tell. */
  private Versions newest(Project project) {
    if (project.getGradle().getStartParameter().isOffline()) {
      return new Versions(DefaultVersions.library(), DefaultVersions.codecs());
    }
    Object configured = project.findProperty(REPOSITORY_PROPERTY);
    String repository = configured == null ? REPOSITORY : configured.toString();
    return new Versions(
        PinnedVersions.release(repository + LIBRARY_METADATA, DefaultVersions.library()),
        PinnedVersions.release(repository + CODECS_METADATA, DefaultVersions.codecs()));
  }

  /** The {@code release} of the Maven metadata at {@code url}, or {@code fallback}. */
  private String release(String url, String fallback) {
    try {
      URLConnection connection = URI.create(url).toURL().openConnection();
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      try (InputStream in = connection.getInputStream()) {
        Matcher release = RELEASE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        if (release.find()) {
          return release.group(1);
        }
      }
      LOG.warn("lwjwae: {} names no release; using {}", url, fallback);
    } catch (IOException | IllegalArgumentException e) {
      LOG.warn("lwjwae: could not read {} ({}); using {}", url, e.getMessage(), fallback);
    }
    return fallback;
  }

  private Versions read(Path file) {
    Properties properties = new Properties();
    try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      properties.load(in);
    } catch (IOException e) {
      throw new GradleException("Could not read " + file, e);
    }
    return new Versions(
        properties.getProperty(LIBRARY_KEY, DefaultVersions.library()).strip(),
        properties.getProperty(CODECS_KEY, DefaultVersions.codecs()).strip());
  }

  private void write(Path file, Versions versions) {
    try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      out.write(
          """
          # The versions of lwjwae that this project builds with. Keep this file in the repository;
          # ./gradlew lwjwaeUpgrade moves it to the newest releases.
          %s=%s
          %s=%s
          """
              .formatted(LIBRARY_KEY, versions.library(), CODECS_KEY, versions.codecs()));
    } catch (IOException e) {
      throw new GradleException("Could not write " + file, e);
    }
  }
}

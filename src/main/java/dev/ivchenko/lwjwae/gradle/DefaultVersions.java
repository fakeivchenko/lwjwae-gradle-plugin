package dev.ivchenko.lwjwae.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;

/**
 * The library and codec versions that the plugin was released with, read from a resource that the
 * build of the plugin writes from its {@code gradle.properties}.
 */
@UtilityClass
public class DefaultVersions {
  private final String RESOURCE = "/dev/ivchenko/lwjwae/gradle/versions.properties";
  private final Properties VERSIONS = DefaultVersions.load();

  /** The default version of {@code lwjwae-core} and the backends. */
  public String library() {
    return VERSIONS.getProperty("lwjwae");
  }

  /** The default version of the codec modules. */
  public String codecs() {
    return VERSIONS.getProperty("lwjwae-codecs");
  }

  private Properties load() {
    Properties properties = new Properties();
    try (InputStream stream = DefaultVersions.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new GradleException("The plugin JAR file carries no default versions");
      }
      properties.load(stream);
    } catch (IOException e) {
      throw new GradleException("Could not read the default versions of the plugin", e);
    }
    return properties;
  }
}

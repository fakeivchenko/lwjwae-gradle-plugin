package dev.ivchenko.lwjwae.gradle.linux;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The AppImage: one executable file that carries the application, its desktop entry, and its icon,
 * and runs on any Linux that has the toolkit of the backend installed: GTK 3 and WebKitGTK 4.1, or
 * GTK 4 and WebKitGTK 6.0.
 *
 * <p>The toolkit isn't bundled. That keeps the file around 20 MB and leaves rendering to the WebKit
 * of the system, which gets its security updates from the distribution; without it, the application
 * reports the package to install. The AppImage is built with {@code appimagetool}, which the plugin
 * downloads once into the Gradle cache and verifies by checksum.
 */
public abstract class AppImageExtension {
  /** Whether {@code packageAll} builds the AppImage. Default: {@code false}. */
  public abstract Property<Boolean> getEnabled();

  /**
   * The {@code appimagetool} to use instead of the downloaded one: the {@code .AppImage} file, or
   * the {@code AppRun} of an extracted one.
   */
  public abstract RegularFileProperty getTool();

  /** The name of the file, without the {@code .AppImage} suffix. Default: the image name. */
  public abstract Property<String> getFileName();
}

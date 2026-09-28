package dev.ivchenko.lwjwae.gradle.linux;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The Debian package: the executable in {@code /usr/bin}, a desktop entry, and the icon in the
 * {@code hicolor} theme, with a {@code control} file that names the toolkit packages it needs.
 *
 * <p>The package is built by the plugin itself, without {@code dpkg}, so it can be built on any
 * Linux and installed with {@code dpkg -i} or {@code apt install ./package.deb}, which also pulls
 * the dependencies. It isn't signed.
 */
public abstract class DebExtension {
  /** Whether {@code packageAll} builds the package. Default: {@code false}. */
  public abstract Property<Boolean> getEnabled();

  /**
   * The package name: lowercase letters, digits, {@code -}, {@code +}, {@code .}. Default: the
   * image name, lowercased.
   */
  public abstract Property<String> getPackageName();

  /**
   * The {@code Maintainer} field, as {@code Name <email>}. Required by the format. Default: the
   * vendor, when it has that shape.
   */
  public abstract Property<String> getMaintainer();

  /**
   * The {@code Depends} field. Default: the toolkit packages of the Linux backend, by their Debian
   * and Ubuntu names; see {@code LinuxBackend}.
   */
  public abstract ListProperty<String> getDepends();

  /** The {@code Section} field. Default: {@code utils}. */
  public abstract Property<String> getSection();

  /** The {@code Priority} field. Default: {@code optional}. */
  public abstract Property<String> getPriority();

  /** The {@code Version} field. Default: the project version. */
  public abstract Property<String> getVersion();
}

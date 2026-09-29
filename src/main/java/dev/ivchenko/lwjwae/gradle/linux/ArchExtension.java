package dev.ivchenko.lwjwae.gradle.linux;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The Arch Linux package: the executable in {@code /usr/bin}, a desktop entry, and the icon in the
 * {@code hicolor} theme, with a {@code .PKGINFO} that names the toolkit packages it needs.
 *
 * <p>The package is built by the plugin itself, without {@code makepkg}, as a {@code .pkg.tar.xz},
 * which {@code pacman -U} installs on Arch Linux and the distributions based on it, pulling the
 * dependencies from their repositories. It isn't signed. A package for the official repositories or
 * the AUR is built from a {@code PKGBUILD} there instead.
 */
public abstract class ArchExtension {
  /** Whether {@code packageAll} builds the package. Default: {@code false}. */
  public abstract Property<Boolean> getEnabled();

  /**
   * The package name: lowercase letters, digits, and {@code @ . _ + -}, not starting with a hyphen
   * or a dot. Default: the image name, lowercased.
   */
  public abstract Property<String> getPackageName();

  /**
   * The {@code packager} field, usually {@code Name <email>}. Default: the vendor, or {@code
   * Unknown Packager} as {@code makepkg} writes without one.
   */
  public abstract Property<String> getPackager();

  /**
   * The {@code depend} entries. Default: the toolkit packages of the Linux backend, by their Arch
   * Linux names; see {@code LinuxBackend}.
   */
  public abstract ListProperty<String> getDepends();

  /** The {@code license} entries, as SPDX identifiers. Default: none. */
  public abstract ListProperty<String> getLicenses();

  /**
   * The {@code pkgver}: the version without a hyphen, which separates the release. Default: the
   * project version, with every hyphen turned into an underscore.
   */
  public abstract Property<String> getVersion();

  /** The {@code pkgrel}: the release of the package of one version. Default: {@code 1}. */
  public abstract Property<String> getRelease();
}

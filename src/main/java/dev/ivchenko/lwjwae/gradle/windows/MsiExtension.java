package dev.ivchenko.lwjwae.gradle.windows;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The Windows installer: an {@code .msi} that puts the executable in place, adds a Start menu
 * shortcut, and registers the application in Settings, so it uninstalls and upgrades like any
 * other.
 *
 * <p>The installer is built with the WiX Toolset, version 6, as a .NET tool. The plugin looks for
 * {@code wix} on the {@code PATH} and otherwise installs it into the Gradle cache with {@code
 * dotnet tool install}, which needs the .NET SDK. The installer isn't signed.
 */
public abstract class MsiExtension {
  /** Whether {@code packageAll} builds the installer. Default: {@code false}. */
  public abstract Property<Boolean> getEnabled();

  /**
   * Whether the installer works for the current user alone, under {@code %LOCALAPPDATA%\Programs}
   * and without administrator rights, or for the whole machine under {@code Program Files}, which
   * asks for elevation. Default: per user.
   */
  public abstract Property<Boolean> getPerUser();

  /**
   * The {@code UpgradeCode}: the GUID that ties every version of the application together, so a new
   * installer replaces the old installation. Keep it stable for the life of the application.
   * Default: derived from the project group and name, which is stable as long as those are.
   */
  public abstract Property<String> getUpgradeCode();

  /** The {@code Manufacturer} shown in Settings. Default: the vendor, or the company name. */
  public abstract Property<String> getManufacturer();

  /** The product name shown in Settings and the Start menu. Default: the file description. */
  public abstract Property<String> getProductName();

  /** The {@code wix} tool to use instead of the one from the {@code PATH} or the cache. */
  public abstract RegularFileProperty getTool();

  /** The WiX version to install when none is found. Default: the one the plugin was tested with. */
  public abstract Property<String> getWixVersion();

  /** The name of the file, without the {@code .msi} suffix. Default: the image name. */
  public abstract Property<String> getFileName();
}

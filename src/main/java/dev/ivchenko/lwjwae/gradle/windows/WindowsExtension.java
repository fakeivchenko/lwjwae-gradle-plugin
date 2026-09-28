package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * What a Windows executable carries besides its code: the subsystem it is linked for, and the icon
 * and the version block that Explorer shows.
 *
 * <p>The plugin writes a resource script from these values and compiles it with {@code rc.exe} from
 * the Windows SDK. {@link #getResourceScript()} replaces the generated script with one of your own.
 */
public abstract class WindowsExtension {
  /**
   * A ready-made {@code .ico} file for the executable and its windows. It replaces the file
   * rendered from {@link LwjwaeExtension#getIcon()}. The icon is resource ID 1, which the backend
   * loads at runtime.
   */
  public abstract RegularFileProperty getIcon();

  /**
   * The pixel sizes rendered into the generated {@code .ico} file. Default: 16, 24, 32, 48, 64,
   * 128, and 256.
   */
  public abstract ListProperty<Integer> getIconSizes();

  /**
   * Whether to keep a console window. Default: off, so a double-click opens the window and nothing
   * else.
   */
  public abstract Property<Boolean> getConsole();

  /** The {@code FileDescription} of the version block. Default: the image name. */
  public abstract Property<String> getFileDescription();

  /** {@code ProductName}. Default: the image name. */
  public abstract Property<String> getProductName();

  /** {@code CompanyName}. Empty by default. */
  public abstract Property<String> getCompanyName();

  /** {@code LegalCopyright}. Empty by default. */
  public abstract Property<String> getCopyright();

  /**
   * {@code FileVersion} and {@code ProductVersion}, as {@code major.minor.patch[.build]}. Default:
   * the project version.
   */
  public abstract Property<String> getVersion();

  /** A complete {@code .rc} script to compile instead of the generated one. */
  public abstract RegularFileProperty getResourceScript();
}

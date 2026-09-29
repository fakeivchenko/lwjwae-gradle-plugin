package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.linux.AppImageExtension;
import dev.ivchenko.lwjwae.gradle.linux.ArchExtension;
import dev.ivchenko.lwjwae.gradle.linux.DebExtension;
import dev.ivchenko.lwjwae.gradle.macos.DmgExtension;
import dev.ivchenko.lwjwae.gradle.windows.MsiExtension;
import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code packaging {}} block: the installers and bundles that wrap the native executable, and
 * what they say about the application.
 *
 * <p>Each format is off until the build script names it, with {@code all()}, with its shortcut such
 * as {@code deb()}, or with its block, and has its own task: {@code packageDeb}, {@code
 * packageArch}, {@code packageAppImage}, {@code packageApp}, {@code packageDmg}, {@code
 * packageMsi}. {@code packageAll} runs the enabled ones that the current operating system can
 * build. Every package lands in {@code build/lwjwae/dist}.
 *
 * <pre>{@code
 * lwjwae {
 *     packaging.all()
 * }
 * }</pre>
 *
 * <p>or, format by format, with the settings of each:
 *
 * <pre>{@code
 * lwjwae {
 *     packaging {
 *         description = "Notes that stay on your machine"
 *         homepage = "https://example.com"
 *         deb()
 *         appImage { fileName = "notes" }
 *         msi { perUser = false }
 *     }
 * }
 * }</pre>
 */
public abstract class PackagingExtension {
  /**
   * One line about the application, for the package manager and the desktop entry. Default: the
   * project description, or the image name.
   */
  public abstract Property<String> getDescription();

  /** The website of the application, where a format shows one. Empty by default. */
  public abstract Property<String> getHomepage();

  /**
   * The {@code Categories} of the Linux desktop entry, from the freedesktop menu specification.
   * Default: {@code Utility}.
   */
  public abstract ListProperty<String> getCategories();

  /** The Debian package. */
  @Nested
  public abstract DebExtension getDeb();

  /** The Arch Linux package. */
  @Nested
  public abstract ArchExtension getArch();

  /** The AppImage. */
  @Nested
  public abstract AppImageExtension getAppImage();

  /** The macOS disk image around the {@code .app} bundle. */
  @Nested
  public abstract DmgExtension getDmg();

  /** The Windows installer. */
  @Nested
  public abstract MsiExtension getMsi();

  /** Turns on every format; {@code packageAll} then builds the ones that this system can. */
  public void all() {
    this.deb();
    this.arch();
    this.appImage();
    this.dmg();
    this.msi();
  }

  /** Turns the Debian package on. */
  public void deb() {
    this.getDeb().getEnabled().set(true);
  }

  /** Turns {@link #getDeb()} on and configures it; {@code enabled = false} inside turns it off. */
  public void deb(Action<? super DebExtension> action) {
    this.deb();
    action.execute(this.getDeb());
  }

  /** Turns the Arch Linux package on. */
  public void arch() {
    this.getArch().getEnabled().set(true);
  }

  /** Turns {@link #getArch()} on and configures it; {@code enabled = false} inside turns it off. */
  public void arch(Action<? super ArchExtension> action) {
    this.arch();
    action.execute(this.getArch());
  }

  /** Turns the AppImage on. */
  public void appImage() {
    this.getAppImage().getEnabled().set(true);
  }

  /**
   * Turns {@link #getAppImage()} on and configures it; {@code enabled = false} inside turns it off.
   */
  public void appImage(Action<? super AppImageExtension> action) {
    this.appImage();
    action.execute(this.getAppImage());
  }

  /** Turns the disk image on. */
  public void dmg() {
    this.getDmg().getEnabled().set(true);
  }

  /** Turns {@link #getDmg()} on and configures it; {@code enabled = false} inside turns it off. */
  public void dmg(Action<? super DmgExtension> action) {
    this.dmg();
    action.execute(this.getDmg());
  }

  /** Turns the Windows installer on. */
  public void msi() {
    this.getMsi().getEnabled().set(true);
  }

  /** Turns {@link #getMsi()} on and configures it; {@code enabled = false} inside turns it off. */
  public void msi(Action<? super MsiExtension> action) {
    this.msi();
    action.execute(this.getMsi());
  }
}

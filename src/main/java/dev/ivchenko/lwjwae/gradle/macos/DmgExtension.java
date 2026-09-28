package dev.ivchenko.lwjwae.gradle.macos;

import org.gradle.api.provider.Property;

/**
 * The macOS disk image: a compressed {@code .dmg} with the {@code .app} bundle inside, built with
 * {@code hdiutil}, which every Mac has. {@code packageApp} builds the bundle alone.
 */
public abstract class DmgExtension {
  /** Whether {@code packageAll} builds the disk image. Default: {@code false}. */
  public abstract Property<Boolean> getEnabled();

  /** The name of the mounted volume. Default: the bundle name. */
  public abstract Property<String> getVolumeName();
}

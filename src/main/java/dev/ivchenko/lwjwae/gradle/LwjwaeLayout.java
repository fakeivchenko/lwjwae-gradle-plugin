package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/**
 * Where the plugin reads and writes: its own files under {@code build/lwjwae}, the packages under
 * {@code build/lwjwae/dist}, the executable that {@code nativeCompile} writes, and the tools that
 * it downloads once for every project, under {@code GRADLE_USER_HOME/caches/lwjwae}.
 *
 * <p>Every location is a provider, so it follows the image name and the build directory when a
 * build script changes them after the plugin was applied.
 */
public final class LwjwaeLayout {
  private final Project project;
  private final LwjwaeExtension extension;

  LwjwaeLayout(Project project, LwjwaeExtension extension) {
    this.project = project;
    this.extension = extension;
  }

  /** A file of the plugin: {@code build/lwjwae/PATH}. */
  public Provider<RegularFile> file(String path) {
    return this.project.getLayout().getBuildDirectory().file("lwjwae/" + path);
  }

  /** A directory of the plugin: {@code build/lwjwae/PATH}. */
  public Provider<Directory> directory(String path) {
    return this.project.getLayout().getBuildDirectory().dir("lwjwae/" + path);
  }

  /** A package: {@code build/lwjwae/dist/NAME}. */
  public Provider<RegularFile> distributionFile(Provider<String> name) {
    return name.flatMap(file -> this.file("dist/" + file));
  }

  /** A package that is a directory, such as a macOS bundle: {@code build/lwjwae/dist/NAME}. */
  public Provider<Directory> distributionDirectory(Provider<String> name) {
    return name.flatMap(directory -> this.directory("dist/" + directory));
  }

  /** The executable that {@code nativeCompile} writes. */
  public Provider<RegularFile> executable() {
    return this.extension
        .getImageName()
        .flatMap(
            name ->
                this.project
                    .getLayout()
                    .getBuildDirectory()
                    .file("native/nativeCompile/" + name + Platform.executableSuffix()));
  }

  /** Where downloaded and installed tools stay between builds of every project. */
  public Provider<Directory> toolCache() {
    return this.project
        .getLayout()
        .dir(
            this.project.provider(
                () -> new File(this.project.getGradle().getGradleUserHomeDir(), "caches/lwjwae")));
  }

  /**
   * The project version, read when a task needs it, in the form that every package takes: a leading
   * {@code v} goes, and a version that doesn't start with a digit, such as the {@code unspecified}
   * of a project without one, becomes {@code 0.0.0}.
   */
  public Provider<String> projectVersion() {
    return this.project.provider(
        () -> LwjwaeLayout.packageVersion(this.project.getVersion().toString()));
  }

  /** {@code version} in the form that a package takes, see {@link #projectVersion()}. */
  static String packageVersion(String version) {
    String stripped = version.strip().replaceFirst("^[vV](?=\\d)", "");
    return stripped.matches("\\d.*") ? stripped : "0.0.0";
  }
}

package dev.ivchenko.lwjwae.gradle.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Puts the package of this platform into the directory of a release, under the name of the
 * platform: the {@code .msi} on Windows, the AppImage on Linux, and on macOS the {@code .app}
 * bundle as a ZIP file that {@code ditto} writes, which keeps its signature and links.
 */
@DisableCachingByDefault(because = "Copies or zips a package")
public abstract class PackageUpdate extends DefaultTask {
  /** The package, when it's a file. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getPackageFile();

  /** The {@code .app} bundle, on macOS. */
  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract DirectoryProperty getBundle();

  /** The file of the release, such as {@code build/lwjwae/update/linux-x64.AppImage}. */
  @OutputFile
  public abstract RegularFileProperty getUpdateFile();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Copies or zips the package. */
  @TaskAction
  public void pack() throws IOException {
    Path target = this.getUpdateFile().get().getAsFile().toPath();
    Files.createDirectories(target.getParent());
    Files.deleteIfExists(target);
    if (this.getBundle().isPresent()) {
      String bundle = this.getBundle().get().getAsFile().getAbsolutePath();
      this.getExecOperations()
          .exec(
              spec -> {
                spec.setExecutable("ditto");
                spec.args("-c", "-k", "--keepParent", bundle, target.toString());
              });
    } else {
      Files.copy(
          this.getPackageFile().get().getAsFile().toPath(),
          target,
          StandardCopyOption.COPY_ATTRIBUTES);
    }
  }
}

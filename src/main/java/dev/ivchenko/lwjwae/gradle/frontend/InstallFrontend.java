package dev.ivchenko.lwjwae.gradle.frontend;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Installs the packages of the frontend: {@code npm ci} with a lock file, which installs exactly
 * what it lists, or {@code npm install} without one, which writes it. Up to date while {@code
 * package.json} and the lock file are unchanged.
 */
@DisableCachingByDefault(because = "node_modules is large and quicker to install than to unpack")
public abstract class InstallFrontend extends DefaultTask {
  /** The npm project. */
  @Internal
  public abstract DirectoryProperty getDirectory();

  /** Its {@code package.json}. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getPackageJson();

  /** Its {@code package-lock.json}, if any. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getLockFile();

  /** The installed packages. */
  @OutputDirectory
  public abstract DirectoryProperty getNodeModules();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Installs. */
  @TaskAction
  public void install() {
    boolean locked =
        this.getLockFile().isPresent() && this.getLockFile().get().getAsFile().isFile();
    this.getExecOperations()
        .exec(
            spec -> {
              spec.commandLine(Npm.command(locked ? "ci" : "install"));
              spec.setWorkingDir(this.getDirectory().get().getAsFile());
            });
  }
}

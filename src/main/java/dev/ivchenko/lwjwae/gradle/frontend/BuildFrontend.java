package dev.ivchenko.lwjwae.gradle.frontend;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * Builds the page with a script of {@code package.json}, {@code npm run build} by default. Up to
 * date, and taken from the build cache, while the files of the npm project are unchanged.
 */
@CacheableTask
public abstract class BuildFrontend extends DefaultTask {
  /** The npm project. */
  @Internal
  public abstract DirectoryProperty getDirectory();

  /** Its files, but the installed packages and the output. */
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSources();

  /** The script to run. */
  @Input
  public abstract Property<String> getScript();

  /** Where the script writes the page. */
  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Builds. */
  @TaskAction
  public void build() {
    this.getExecOperations()
        .exec(
            spec -> {
              spec.commandLine(Npm.command("run", this.getScript().get()));
              spec.setWorkingDir(this.getDirectory().get().getAsFile());
            });
  }
}

package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.util.Icons;
import java.io.File;
import java.nio.file.Files;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Renders one image, usually a PNG file of any size, into a Windows {@code .ico} file that holds
 * the image at every size that Explorer, the taskbar, and the title bar ask for.
 */
@CacheableTask
public abstract class GenerateWindowsIcon extends DefaultTask {
  /** The source image. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getSource();

  /** The pixel sizes to render, each one an entry of the icon. */
  @Input
  public abstract ListProperty<Integer> getSizes();

  /** The {@code .ico} file to write. */
  @OutputFile
  public abstract RegularFileProperty getIcon();

  /** Renders the icon. */
  @TaskAction
  @SneakyThrows
  public void generate() {
    File icon = this.getIcon().get().getAsFile();
    Files.createDirectories(icon.getParentFile().toPath());
    Files.write(
        icon.toPath(),
        Icons.ico(Icons.read(this.getSource().get().getAsFile()), this.getSizes().get()));
  }
}

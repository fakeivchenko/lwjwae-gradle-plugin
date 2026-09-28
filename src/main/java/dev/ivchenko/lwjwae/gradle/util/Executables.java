package dev.ivchenko.lwjwae.gradle.util;

import java.io.File;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/** Finds the external tools that the tasks run. */
@UtilityClass
public class Executables {
  /** The file {@code name} in the first directory of the {@code PATH} that has it. */
  public Optional<File> onPath(String name) {
    String path = System.getenv("PATH");
    if (path == null) {
      return Optional.empty();
    }
    for (String entry : path.split(File.pathSeparator)) {
      File candidate = new File(entry, name);
      if (candidate.isFile()) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}

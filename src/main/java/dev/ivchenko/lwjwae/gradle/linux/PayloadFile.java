package dev.ivchenko.lwjwae.gradle.linux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One file that a Linux package installs: rendered in memory, or streamed from a file that can run
 * to tens of megabytes, such as the executable.
 *
 * @param path The installed path, relative to the root: {@code usr/bin/notes}.
 * @param mode The Unix permissions.
 * @param content The content, or {@code null} when {@code source} has it.
 * @param source The file with the content, or {@code null} when {@code content} is it.
 */
record PayloadFile(String path, int mode, byte[] content, Path source) {
  /** A file whose content is in memory. */
  static PayloadFile of(String path, int mode, byte[] content) {
    return new PayloadFile(path, mode, content, null);
  }

  /** A file whose content is streamed from {@code source}. */
  static PayloadFile of(String path, int mode, Path source) {
    return new PayloadFile(path, mode, null, source);
  }

  /** The size in bytes. */
  long size() throws IOException {
    return this.content != null ? this.content.length : Files.size(this.source);
  }

  /** Opens the content. */
  InputStream open() throws IOException {
    return this.content != null
        ? new ByteArrayInputStream(this.content)
        : Files.newInputStream(this.source);
  }
}

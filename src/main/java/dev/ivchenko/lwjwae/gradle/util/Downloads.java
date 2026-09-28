package dev.ivchenko.lwjwae.gradle.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;

/**
 * Downloads of the tools that a task runs, each one pinned to a release and checked against the
 * SHA-256 that the plugin was released with, so a changed file on the server fails the build
 * instead of running.
 */
@UtilityClass
public class Downloads {
  /**
   * Returns {@code target}, downloading it from {@code url} first unless it's there with the
   * expected checksum already.
   *
   * @throws GradleException If the downloaded file has another checksum; it never reaches {@code
   *     target} then.
   */
  @SneakyThrows
  public Path verified(String url, String sha256, Path target) {
    Files.createDirectories(target.getParent());
    // Two builds that share the cache, in parallel or in two daemons, take turns; the second finds
    // the file of the first. The file itself is only ever moved in whole, so no build runs a half.
    Path lockFile = target.resolveSibling(target.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock _ = channel.lock()) {
      if (Files.isRegularFile(target) && Downloads.sha256(target).equals(sha256)) {
        return target;
      }
      Path partial = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".part");
      try {
        try (InputStream stream = URI.create(url).toURL().openStream()) {
          Files.copy(stream, partial, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = Downloads.sha256(partial);
        if (!actual.equals(sha256)) {
          throw new GradleException(
              "Checksum mismatch for " + url + ": expected " + sha256 + ", got " + actual);
        }
        Files.move(
            partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(partial);
      }
      return target;
    }
  }

  @SneakyThrows
  private String sha256(Path file) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream stream = new DigestInputStream(Files.newInputStream(file), digest)) {
      stream.transferTo(OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}

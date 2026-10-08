package dev.ivchenko.lwjwae.gradle.windows;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;

/**
 * Signs a file with {@code signtool} of the Windows SDK: SHA-256 for the file and for the time
 * stamp, from a {@code .pfx} file or a certificate of the store, with the name of the application
 * as the description that the dialog of User Account Control shows.
 */
@UtilityClass
public class Signtool {
  /**
   * The arguments of {@code signtool} that sign {@code file}.
   *
   * @param certificate The {@code .pfx} file, or {@code null} for {@code thumbprint}.
   * @param password The password of the {@code .pfx} file, or {@code null}.
   * @param thumbprint The thumbprint of a certificate of the store, used without {@code
   *     certificate}.
   * @param timestampUrl The time stamp server.
   * @param description What the dialog of User Account Control calls the file.
   */
  public List<String> arguments(
      File certificate,
      String password,
      String thumbprint,
      String timestampUrl,
      String description,
      File file) {
    List<String> arguments =
        new ArrayList<>(List.of("sign", "/fd", "SHA256", "/td", "SHA256", "/tr", timestampUrl));
    if (certificate != null) {
      arguments.addAll(List.of("/f", certificate.getAbsolutePath()));
      if (password != null && !password.isEmpty()) {
        arguments.addAll(List.of("/p", password));
      }
    } else if (thumbprint != null) {
      arguments.addAll(List.of("/sha1", thumbprint.replace(" ", "")));
    } else {
      throw new GradleException("Signing needs a certificate file or a certificate thumbprint");
    }
    arguments.addAll(List.of("/d", description, file.getAbsolutePath()));
    return arguments;
  }

  /** Signs {@code file} in place with the arguments of {@link #arguments}. */
  public void sign(ExecOperations exec, List<String> arguments) {
    String tool = WindowsSdk.tool("signtool.exe");
    exec.exec(
        spec -> {
          spec.setExecutable(tool);
          spec.args(arguments);
        });
  }
}

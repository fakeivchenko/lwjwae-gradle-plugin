package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigntoolTest {
  @Test
  void signsWithSha256AndTimeStampFromFileOrStore() {
    File app = new File("app.exe");
    Assertions.assertEquals(
        List.of(
            "sign",
            "/fd",
            "SHA256",
            "/td",
            "SHA256",
            "/tr",
            "http://ts",
            "/f",
            new File("cert.pfx").getAbsolutePath(),
            "/p",
            "secret",
            "/d",
            "Demo",
            app.getAbsolutePath()),
        Signtool.arguments(new File("cert.pfx"), "secret", null, "http://ts", "Demo", app));
    Assertions.assertEquals(
        List.of(
            "sign",
            "/fd",
            "SHA256",
            "/td",
            "SHA256",
            "/tr",
            "http://ts",
            "/sha1",
            "ABCDEF",
            "/d",
            "Demo",
            app.getAbsolutePath()),
        Signtool.arguments(null, null, "AB CD EF", "http://ts", "Demo", app));
    Assertions.assertThrows(
        GradleException.class,
        () -> Signtool.arguments(null, null, null, "http://ts", "Demo", app));
  }

  @Test
  void installerPacksSignedExecutableOnlyWithCertificate(@TempDir Path directory) {
    Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    project.getPluginManager().apply("application");
    project.getPluginManager().apply("dev.ivchenko.lwjwae");
    LwjwaeExtension extension = project.getExtensions().getByType(LwjwaeExtension.class);
    extension.getImageName().set("demo");
    PackageMsi msi = (PackageMsi) project.getTasks().getByName("packageMsi");
    // Path, not text: the separators are the ones of the machine.
    Path unsigned = msi.getExecutable().get().getAsFile().toPath();
    Assertions.assertTrue(
        unsigned.endsWith(Path.of("native", "nativeCompile", "demo.exe"))
            || unsigned.endsWith(Path.of("native", "nativeCompile", "demo")),
        unsigned.toString());

    extension.getWindows().getSigning().getCertificateThumbprint().set("ABCDEF");
    Path signed = msi.getExecutable().get().getAsFile().toPath();
    Assertions.assertTrue(
        signed.endsWith(Path.of("lwjwae", "windows", "signed", "demo.exe")), signed.toString());
    Assertions.assertEquals(
        WindowsSigningExtension.DEFAULT_TIMESTAMP_URL, msi.getTimestampUrl().get());
  }
}

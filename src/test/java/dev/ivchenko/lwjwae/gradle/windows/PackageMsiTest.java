package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageMsiTest {
  @Test
  void installerRegistersTheSchemesAndTheTypesOfFilesForTheExecutable(@TempDir Path directory)
      throws Exception {
    Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    project.getPluginManager().apply("application");
    project.getPluginManager().apply("dev.ivchenko.lwjwae");
    LwjwaeExtension extension = project.getExtensions().getByType(LwjwaeExtension.class);
    extension.getImageName().set("demo");
    extension.getDisplayName().set("Demo & Co");
    extension.urlScheme("demo");
    extension.fileType("note", "application/x-demo-note", "Note");
    PackageMsi task = (PackageMsi) project.getTasks().getByName("packageMsi");
    task.getExecutable().set(Files.writeString(directory.resolve("demo.exe"), "").toFile());

    String source = task.source();
    Assertions.assertTrue(source.contains("<File Id=\"ExecutableFile\" "), source);
    Assertions.assertTrue(
        source.contains(
            """
                    <RegistryKey Root="HKCR" Key="demo">
                      <RegistryValue Type="string" Value="URL:Demo &amp; Co" />
                      <RegistryValue Name="URL Protocol" Type="string" Value="" />
                      <RegistryValue Key="DefaultIcon" Type="string" Value="&quot;[INSTALLFOLDER]demo.exe&quot;,0" />
                      <RegistryValue Key="shell\\open\\command" Type="string" Value="&quot;[INSTALLFOLDER]demo.exe&quot; &quot;%1&quot;" />
                    </RegistryKey>
            """),
        source);
    Assertions.assertTrue(
        source.contains(
            """
                    <ProgId Id="DemoCo.note" Description="Note" Icon="ExecutableFile" IconIndex="0" Advertise="no">
                      <Extension Id="note" ContentType="application/x-demo-note">
                        <Verb Id="open" TargetFile="ExecutableFile" Argument="&quot;%1&quot;" />
                      </Extension>
                    </ProgId>
            """),
        source);
  }
}

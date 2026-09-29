package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.util.Executables;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds a frontend with npm through the plugin. The npm project has no dependencies, and its
 * scripts are small Node.js programs, so the test needs npm on the PATH but no network.
 */
class FrontendTest {
  @TempDir Path project;

  @BeforeEach
  void writeProject() throws IOException {
    Assumptions.assumeTrue(
        Executables.onPath(Platform.isWindows() ? "npm.cmd" : "npm").isPresent(),
        "npm is on the PATH");
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
    Path frontend = Files.createDirectories(this.project.resolve("frontend"));
    Files.writeString(
        frontend.resolve("package.json"),
        """
        {
          "name": "demo-frontend",
          "private": true,
          "scripts": { "build": "node build.js", "dev": "node dev.js" }
        }
        """);
    Files.writeString(
        frontend.resolve("build.js"),
        """
        const fs = require("fs");
        fs.mkdirSync("dist/assets", { recursive: true });
        fs.writeFileSync("dist/index.html", "<h1>" + fs.readFileSync("title.txt", "utf8") + "</h1>");
        fs.writeFileSync("dist/assets/app.js", "console.log('page');");
        """);
    Files.writeString(frontend.resolve("title.txt"), "Demo");
    Files.writeString(
        frontend.resolve("dev.js"),
        """
        const http = require("http");
        // On ::1 alone, where Vite listens for localhost on Node.js 17 and later.
        http.createServer((_, response) => response.end("dev")).listen(Number(process.env.PORT), "::1");
        console.log("serving");
        """);
    Path main = Files.createDirectories(this.project.resolve("src/main/java/demo"));
    Files.writeString(
        main.resolve("Main.java"),
        """
        package demo;

        public class Main {
          // Every address of the host, as the web engine of a window tries them.
          public static void main(String[] args) throws Exception {
            java.net.URI url = java.net.URI.create(System.getenv("LWJWAE_DEV_SERVER_URL"));
            for (var address : java.net.InetAddress.getAllByName(url.getHost())) {
              try (var socket = new java.net.Socket(address, url.getPort())) {
                socket.getOutputStream().write("GET / HTTP/1.0\\r\\n\\r\\n".getBytes());
                String response = new String(socket.getInputStream().readAllBytes());
                System.out.println("PAGE " + url + " " + response.substring(response.indexOf("\\r\\n\\r\\n") + 4));
                return;
              } catch (java.io.IOException e) {
                // The next address.
              }
            }
          }
        }
        """);
  }

  @Test
  void buildsThePageIntoTheResourcesUnderApp() throws IOException {
    this.writeBuild("");
    BuildResult first = this.run("processResources");
    Assertions.assertEquals(TaskOutcome.SUCCESS, first.task(":buildFrontend").getOutcome());
    Path page = this.project.resolve("build/resources/main/app");
    Assertions.assertEquals("<h1>Demo</h1>", Files.readString(page.resolve("index.html")));
    Assertions.assertTrue(Files.exists(page.resolve("assets/app.js")));
    Assertions.assertTrue(
        Files.exists(this.project.resolve("frontend/package-lock.json")),
        "npm install wrote the lock file, and the next install is npm ci");

    BuildResult second = this.run("processResources");
    Assertions.assertEquals(TaskOutcome.UP_TO_DATE, second.task(":buildFrontend").getOutcome());

    Files.writeString(this.project.resolve("frontend/title.txt"), "Changed");
    this.run("processResources");
    Assertions.assertEquals("<h1>Changed</h1>", Files.readString(page.resolve("index.html")));
  }

  @Test
  void projectWithoutFrontendBuildsWithoutNpm() throws IOException {
    this.writeBuild("");
    Files.delete(this.project.resolve("frontend/package.json"));
    BuildResult result = this.run("processResources");
    Assertions.assertEquals(TaskOutcome.SKIPPED, result.task(":buildFrontend").getOutcome());
    Assertions.assertFalse(Files.exists(this.project.resolve("build/resources/main/app")));
  }

  @Test
  void runDevRunsTheApplicationAgainstTheDevelopmentServerAndStopsIt() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    String url = "http://localhost:" + port;
    this.writeBuild(
        """
        lwjwae { frontend { devServerUrl = "%s" } }
        """
            .formatted(url));
    // The development server takes its port from the environment of the build.
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put("PORT", String.valueOf(port));
    BuildResult result =
        GradleRunner.create()
            .withProjectDir(this.project.toFile())
            .withPluginClasspath()
            .withArguments("runDev", "--stacktrace")
            .withEnvironment(environment)
            .build();
    Assertions.assertTrue(result.getOutput().contains("PAGE " + url + " dev"), result.getOutput());
    Assertions.assertTrue(result.getOutput().contains("[dev] serving"), result.getOutput());
    try (ServerSocket socket = new ServerSocket(port)) {
      Assertions.assertEquals(port, socket.getLocalPort(), "the server let go of its port");
    }
  }

  private void writeBuild(String configuration) throws IOException {
    Files.writeString(
        this.project.resolve("build.gradle.kts"),
        """
        plugins {
            id("application")
            id("dev.ivchenko.lwjwae")
        }
        application { mainClass = "demo.Main" }
        lwjwae { managed = false }
        """
            + configuration);
  }

  private BuildResult run(String task) {
    return GradleRunner.create()
        .withProjectDir(this.project.toFile())
        .withPluginClasspath()
        .withArguments(task, "--stacktrace")
        .build();
  }
}

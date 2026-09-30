<h1 align="center">lwjwae-gradle-plugin</h1>

<p align="center">
  <b>The Gradle plugin for lwjwae applications</b><br/>
</p>

<p align="center">
  <a href="https://github.com/fakeivchenko/lwjwae-gradle-plugin/actions/workflows/tests.yml"><img alt="Tests" src="https://github.com/fakeivchenko/lwjwae-gradle-plugin/actions/workflows/tests.yml/badge.svg?branch=dev"></a>
  <a href="https://github.com/fakeivchenko/lwjwae-gradle-plugin/actions/workflows/release.yml"><img alt="Release" src="https://github.com/fakeivchenko/lwjwae-gradle-plugin/actions/workflows/release.yml/badge.svg?branch=release"></a>
  <a href="https://repo.ivchenko.dev/#/releases/dev/ivchenko/lwjwae/lwjwae-gradle-plugin"><img alt="Latest release" src="https://repo.ivchenko.dev/api/badge/latest/releases/dev/ivchenko/lwjwae/lwjwae-gradle-plugin?color=40c14a&name=release"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-blue">
  <a href="LICENSE"><img alt="Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-green"></a>
</p>

The plugin sets a Java project up as an lwjwae application:

- **The dependencies.** `lwjwae-core` to compile against, the backend of the machine that runs the
  build at runtime, and a codec from [lwjwae-codecs](https://github.com/fakeivchenko/lwjwae-codecs)
  when you ask for one. The versions come with the plugin; a build script overrides them when it
  needs to.
- **The native image.** The plugin applies the GraalVM Native Build Tools plugin and configures
  `nativeCompile`: `--enable-native-access` for the FFM API, `-Os`, a capped heap, the image name.
  The same `--enable-native-access` goes to `gradle run`.
- **The page.** A frontend in `frontend/`, such as a Vite project, installed and built with npm
  into the resources, and served with hot reload for `./gradlew runDev`.
- **Windows.** A GUI subsystem executable, so a double-click opens the window and no console, with
  an icon rendered from one PNG file and a version block for the properties dialog, compiled by
  `rc.exe` from the Windows SDK.
- **macOS.** An `Info.plist` embedded into the executable with the bundle identifier that the
  helper processes of WebKit need, and the name that the menu bar and the Dock show.
- **Packages.** A `.deb`, an Arch Linux package, and an AppImage on Linux, an `.app` bundle and a `.dmg` on macOS, an
  `.msi` installer on Windows, each from one task, with the icon and the metadata above.
- **Updates.** With `updates { manifestUrl; publicKey }`, the application finds its new versions
  through `application.updater()`. `generateUpdateKeys` makes the Ed25519 keys once,
  `packageUpdate` puts the package of each platform into the release directory, and
  `updateManifest` writes the manifest over them and signs it with the private key from
  `LWJWAE_UPDATE_PRIVATE_KEY`. Upload the directory to the manifest URL and the release is out.

## Usage

In `settings.gradle.kts`, add the repository that the plugin and the library are published to:

```kotlin
pluginManagement {
    repositories {
        maven("https://repo.ivchenko.dev/releases")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.ivchenko.dev/releases")
    }
}
```

In `build.gradle.kts`, apply the plugin:

```kotlin
plugins {
    application
    id("dev.ivchenko.lwjwae").version("VERSION")
}

application {
    mainClass = "com.example.Main"
}

lwjwae {
    displayName = "My app"
    vendor = "Example <hello@example.com>"
    jackson()
    packaging.all()
}
```

Put the icon, one square PNG of 256 pixels or larger, at `src/main/icons/app.png`. Then:

```bash
./gradlew run             # on the JVM
./gradlew runDev          # against the development server of frontend/, with hot reload
./gradlew nativeCompile   # the executable, with GRAALVM_HOME set
./gradlew packageAll      # the packages of this platform, in build/lwjwae/dist
```

## Documentation

The [lwjwae wiki](https://github.com/fakeivchenko/lwjwae/wiki/Home) has the rest:

- [Gradle plugin](https://github.com/fakeivchenko/lwjwae/wiki/Gradle-plugin): what the plugin does, the short form of a build script,
  the types that cross the bridge, the packages and what each needs, and the tasks.
- [Gradle plugin configuration](https://github.com/fakeivchenko/lwjwae/wiki/Gradle-plugin-configuration): every setting of the
  `lwjwae {}` block, with its default.
- The tutorial, from [setting up the project](https://github.com/fakeivchenko/lwjwae/wiki/1.-Set-up-the-project) to
  [the icon and the name](https://github.com/fakeivchenko/lwjwae/wiki/9.-Give-the-application-an-icon-and-a-name),
  [the native executable](https://github.com/fakeivchenko/lwjwae/wiki/10.-Build-a-native-executable), and
  [the packages](https://github.com/fakeivchenko/lwjwae/wiki/11.-Package-the-application).

## Development

```bash
./gradlew build
```

The tests apply the plugin in real builds through Gradle TestKit. The package tests build real
packages from a stand-in executable; the ones for Linux run on Linux only, and the AppImage test
downloads `appimagetool` once into the Gradle cache.

## Releases

Every push to `release` runs the tests, computes a version from the commit messages (Conventional
Commits), publishes the plugin and its marker artifact to the Maven repository, and creates a tag
and a GitHub release, the same way the library does.

## License

Apache License 2.0. See [LICENSE](LICENSE).

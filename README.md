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
- **Windows.** A GUI subsystem executable, so a double-click opens the window and no console, with
  an icon rendered from one PNG file and a version block for the properties dialog, compiled by
  `rc.exe` from the Windows SDK.
- **macOS.** An `Info.plist` embedded into the executable with the bundle identifier that the
  helper processes of WebKit need, and the name that the menu bar and the Dock show.
- **Packages.** A `.deb` and an AppImage on Linux, an `.app` bundle and a `.dmg` on macOS, an
  `.msi` installer on Windows, each from one task, with the icon and the metadata above.

## Usage

### 1. Point Gradle at the repository

The plugin and the library live in `https://repo.ivchenko.dev/releases`. In `settings.gradle.kts`:

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

rootProject.name = "my-app"
```

### 2. Apply the plugin

In `build.gradle.kts`:

```kotlin
plugins {
    id("application")
    id("dev.ivchenko.lwjwae").version("VERSION")
}

application {
    mainClass = "com.example.Main"
}
```

That is the whole build script for a working application. The plugin adds `lwjwae-core` to
compile against and, at runtime, the backend of the operating system that runs the build. The
library version comes with the plugin, so there is nothing to keep in sync.

### 3. Write the application

Put the page in `src/main/resources`, and the Java entry point next to it:

```
src/main/
├── java/com/example/Main.java
└── resources/app/index.html
```

```java
package com.example;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;

public class Main {
  public static void main(String[] arguments) {
    try (Application application = Application.create()) {
      Window window = application.open(WindowParameters.of("My app", 1024, 768));
      window.bind("greet", name -> "Hello, " + name);
      window.loadResource("app/index.html");
      window.show();
      application.run();
    }
  }
}
```

```html
<!DOCTYPE html>
<html>
<body>
<h1 id="greeting">…</h1>
<script>
  window.greet("world").then((text) => {
    document.getElementById("greeting").textContent = text;
  });
</script>
</body>
</html>
```

### 4. Run it on the JVM

```bash
./gradlew run
```

The plugin passes `--enable-native-access=ALL-UNNAMED` to the JVM, which the FFM API needs; you
don't add it yourself.

### 5. Build the executable

Install a GraalVM JDK 25 and name it in `GRAALVM_HOME`. On Windows, open an *x64 Native Tools
Command Prompt* or run `vcvars64.bat` first, so that the C toolchain and `rc.exe` are on the
`PATH`. Then:

```bash
./gradlew nativeCompile
```

The executable is in `build/native/nativeCompile/`, named after the project: `my-app`, or
`my-app.exe` on Windows. It carries the page, needs no JVM, and starts in a fraction of a second.
The plugin puts every file of `src/main/resources` into the image, so the page loads from the
executable as it does from the JAR file.
On Windows it opens no console window; on macOS it identifies itself to the system as
`GROUP.my-app`.

### 6. Give it an icon and a name

Put one square PNG, 256 pixels or larger, at `src/main/icons/app.png`, where the plugin finds it
without configuration, and name the application:

```kotlin
lwjwae {
    displayName = "My app"
    vendor = "Example <hello@example.com>"
}
```

The icon goes to the executable, the window, and every package; `icon = file("…")` points
elsewhere. The display name goes to the Linux menu, the Details tab of the Windows properties dialog, the
installer, and the macOS menu bar and Dock; the vendor, without its address, is the company of the
Windows version block and the manufacturer of the installer, and, whole, the maintainer of the
Debian package.

### 7. Add a codec for the typed bridge

`bind(name, Class, handler)`, `emit(name, Object)`, and typed events need a codec. Pick one and the
plugin adds it at the matching version:

```kotlin
lwjwae {
    jackson()   // or gson(), jsonb(), jsonb("org.apache.johnzon:johnzon-jsonb:2.0.2")
}
```

The types that
cross the bridge need reflection metadata in the native image. Mark the outermost ones with
`@BridgeType`, and the annotation processor that the plugin adds, `lwjwae-processor`, writes the
metadata for them and for every type that they hold:

```java
@BridgeType
public record Outline(String name, List<Point> points) {}
```

The plugin adds the processor from lwjwae 0.8.0, the first release that has it; with an older
`version`, write the metadata yourself in
`src/main/resources/META-INF/native-image/GROUP/NAME/reachability-metadata.json`.

### 8. Package it for users

Turn every format on and build the ones that this system can, all at once:

```kotlin
lwjwae {
    packaging.all()
}
```

or name the formats, with their settings where you have any:

```kotlin
lwjwae {
    packaging {
        homepage = "https://example.com"
        deb()
        msi { perUser = false }
    }
}
```

```bash
./gradlew packageAll
```

`packageAll` builds what the current operating system can and puts it in `build/lwjwae/dist`:

| On | You get | Needs |
|---|---|---|
| Linux | `my-app_1.0.0_amd64.deb`: the executable in `/usr/bin`, a menu entry, the icon; depends on the GTK and WebKitGTK packages | nothing |
| Linux | `my-app-1.0.0-x86_64.AppImage`: one file that runs on any Linux with WebKitGTK installed | `appimagetool` and the AppImage runtime, pinned releases downloaded once into the Gradle cache and checked by SHA-256 |
| macOS | `My app.app` and `My app-1.0.0.dmg` | `hdiutil`, part of macOS; `codesign` when `macos.signingIdentity` is set |
| Windows | `my-app-1.0.0.msi`: installs per user without administrator rights, with a Start menu shortcut, and shows up in Settings | the WiX Toolset, installed once into the Gradle cache with `dotnet tool install`, so the .NET SDK |

The `description` of the project becomes the one-line description of the package, the `version`
its version. Each format also has its own task, `packageDeb`, `packageAppImage`, `packageApp`,
`packageDmg`, `packageMsi`, which runs whether the format is on or not.

### 9. Ship one JAR file for every platform

A native image is built per platform, but a JAR file can carry every backend:

```kotlin
lwjwae {
    backends = Backends.ALL
}
```

Each backend checks the operating system and its libraries before it loads anything, so the ones
that don't apply step aside.

## Configuration reference

Every setting is optional. The example lists all of them with their defaults:

```kotlin
lwjwae {
    managed = true                        // false: add nothing, declare the dependencies and the processor yourself
    version = "0.7.1"                     // lwjwae-core and the backends; default: the plugin's
    codecsVersion = "0.1.3"               // the codec modules; default: the plugin's
    backends = Backends.CURRENT_PLATFORM  // or ALL, NONE
    linuxBackend = LinuxBackend.GTK3      // or GTK4: GTK 4 and WebKitGTK 6.0
    codec = Codec.NONE                    // or JACKSON, GSON, JSONB; shortcuts: jackson(), gson(), jsonb()
    jsonbProvider = "org.eclipse:yasson"  // the implementation that comes with JSONB, group:artifact
    jsonbProviderVersion = "3.0.4"        // and its version; both at once: jsonb("group:artifact:version")

    imageName = "my-app"                  // the executable; default: the project name
    displayName = "My app"                // what people see; default: imageName
    vendor = "Example <hello@example.com>"   // the company, the manufacturer, the Debian maintainer
    icon = file("app.png")                // one square PNG, 256 px or larger; default: src/main/icons/app.png, if there
    embedResources = true                 // the files of src/main/resources go into the image
    optimizeForSize = true                // -Os
    maxHeapSize = "64m"                   // -R:MaxHeapSize; "" leaves it unset
    toolchainDetection = false            // true: find a GraalVM through Gradle toolchains instead of GRAALVM_HOME
    buildArgs.add("--verbose")            // appended to the native-image command line

    windows {
        icon = file("custom.ico")         // a ready-made .ico instead of the one rendered from icon
        iconSizes = listOf(16, 24, 32, 48, 64, 128, 256)
        console = false                   // true: keep a console window
        fileDescription = "My app"        // default: displayName
        productName = "My product"        // default: displayName
        companyName = "Example and Co"    // default: vendor, without its address
        copyright = "(c) Example"
        version = "1.2.3"                 // default: the project version
        resourceScript = file("custom.rc")   // replaces the generated script
    }

    macos {
        bundleIdentifier = "com.example.myapp"   // default: GROUP.NAME, other characters as -
        bundleName = "My app"             // default: displayName; also names the .app
        version = "1.2.3"                 // default: the project version
        infoPlist = file("Info.plist")    // replaces the generated property list
        signingIdentity = "Developer ID Application: Example (TEAMID)"   // codesign the bundle; unset: unsigned
    }

    packaging {
        all()                             // every format on; one by one: deb(), appImage(), dmg(), msi()
        description = "One line about the app"   // default: the project description, or displayName
        homepage = "https://example.com"
        categories = listOf("Utility")    // the Linux menu categories

        deb {                             // a block turns the format on; enabled = false turns it off
            packageName = "my-app"        // default: imageName, lowercased
            maintainer = "Example <hello@example.com>"   // required by the format; default: vendor, if it has an address
            depends = listOf("libgtk-3-0t64 | libgtk-3-0", "libwebkit2gtk-4.1-0")   // default: the packages of linuxBackend
            section = "utils"
            priority = "optional"
            version = "1.2.3"             // default: the project version
        }
        appImage {
            fileName = "my-app"           // default: imageName
            tool = file("appimagetool-x86_64.AppImage")   // instead of the downloaded one
        }
        dmg {
            volumeName = "My app"         // default: macos.bundleName
        }
        msi {
            perUser = true                // false: Program Files, asks for elevation
            upgradeCode = "GUID"          // keep stable; default: derived from group and name
            manufacturer = "Example"      // default: vendor, without its address, or displayName
            productName = "My app"        // default: displayName
            wixVersion = "6.0.2"          // installed when no wix is on the PATH
            tool = file("wix.exe")        // instead of the PATH or the cache
            fileName = "my-app"           // default: imageName
        }
    }
}
```

`graalvmNative {}` of the Native Build Tools plugin stays available for anything not listed.

`Backends`, `LinuxBackend`, and `Codec` live in `dev.ivchenko.lwjwae.gradle`. `CURRENT_PLATFORM`
selects `lwjwae-windows`, `lwjwae-macos`, or the backend of `linuxBackend` by the operating system
that runs the build, which is the right backend for a native image built on that machine. On Linux
that's `lwjwae-gtk` by default, or `lwjwae-gtk4` for distributions that ship WebKitGTK 6.0 without
4.1; the `Depends` of the `.deb` follow the choice. `ALL` puts all four backends on the classpath
for a JAR file that runs anywhere; where both WebKitGTK versions are installed, GTK 3 wins.

Everything is wired lazily, through providers, so the `lwjwae {}` block can come after the
`plugins {}` block, as it does above.

## Tasks

| Task                            | Runs on                                       | Output                          |
|---------------------------------|-----------------------------------------------|---------------------------------|
| `generateWindowsIcon`           | any platform, when `icon` is set              | `build/lwjwae/windows/app.ico`  |
| `generateWindowsResourceScript` | any platform                                  | `build/lwjwae/windows/app.rc`   |
| `compileWindowsResources`       | Windows, before `nativeCompile`               | `build/lwjwae/windows/app.res`  |
| `generateInfoPlist`             | any platform; before `nativeCompile` on macOS | `build/lwjwae/macos/Info.plist` |
| `packageDeb`                    | Linux                                         | `build/lwjwae/dist/*.deb`       |
| `packageAppImage`               | Linux                                         | `build/lwjwae/dist/*.AppImage`  |
| `packageApp`                    | any platform; signs on macOS only             | `build/lwjwae/dist/*.app`       |
| `packageDmg`                    | macOS                                         | `build/lwjwae/dist/*.dmg`       |
| `packageMsi`                    | Windows                                       | `build/lwjwae/dist/*.msi`       |
| `packageAll`                    | the enabled packages of the current platform  | all of the above that apply     |

The icon is resource ID 1 of the executable, which the Windows backend loads for its windows. The
`.res` file and the property list go to the linker through `-H:NativeLinkerOption`.

The `.deb` is written by the plugin itself, as an `ar` archive of `control.tar.gz` and
`data.tar.gz`, so it needs no `dpkg`. The AppImage bundles the executable, the desktop entry, and
the icon, but not WebKitGTK: the file stays small and rendering uses the WebKit of the system, which
gets its security updates from the distribution. The MSI comes from one generated WiX source with a
`MajorUpgrade`, so a newer installer replaces an older installation; the upgrade code is derived
from the project group and name and must not change for the life of the application.

## Layout

The code is split by platform; each package holds the extension blocks, the tasks, and one
`*Configuration` class that applies the defaults and registers the tasks of that platform.

| Package                         | Contents                                                                                     |
|---------------------------------|----------------------------------------------------------------------------------------------|
| `dev.ivchenko.lwjwae.gradle`    | `LwjwaePlugin`, which puts the parts together; the `lwjwae {}` and `packaging {}` blocks; the dependencies; the native image; `LwjwaeLayout`, where every file goes. |
| `...gradle.windows`             | The icon, the resource script, `rc.exe`, and the MSI.                                        |
| `...gradle.macos`               | The `Info.plist`, the `.app` bundle, and the disk image.                                     |
| `...gradle.linux`               | The `.deb`, the AppImage, and the desktop entry they share.                                  |
| `...gradle.util`                | Icons in every format, the platform of the build, tools on the `PATH`, checked downloads, XML text. |

The tests apply the plugin in real builds through Gradle TestKit. The plugin tests read the
configured dependencies and arguments without resolving anything, so they need no network. The
package tests build real packages from a stand-in executable; the ones for Linux run on Linux only,
and the AppImage test downloads `appimagetool` once into the Gradle cache.

```bash
./gradlew build
```

## Releases

Every push to `release` runs the tests, computes a version from the commit messages (Conventional
Commits), publishes the plugin and its marker artifact to the Maven repository, and creates a tag
and a GitHub release, the same way the library does.

## License

Apache License 2.0. See [LICENSE](LICENSE).

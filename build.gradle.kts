import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok").version("9.5.0")
    id("com.diffplug.spotless").version("8.2.1")
}

group = "dev.ivchenko.lwjwae"
version = providers.gradleProperty("projectVersion").getOrElse("0.0.0-dev")
description = "Gradle plugin for lwjwae applications: the dependencies, and a GraalVM native image with the icon, version, and bundle metadata of each platform."

val lwjwaeVersion = providers.gradleProperty("lwjwaeVersion")
val lwjwaeCodecsVersion = providers.gradleProperty("lwjwaeCodecsVersion")

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

lombok {
    version = "1.18.48"
}

spotless {
    java {
        googleJavaFormat("1.33.0").reflowLongStrings()
        formatAnnotations()
    }
}

checkstyle {
    toolVersion = "11.1.0"
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

dependencies {
    // GraalVM Native Build Tools: applied and configured by this plugin
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.11.3")

    // ar and tar for the .deb package
    implementation("org.apache.commons:commons-compress:1.28.0")
    // xz for the Arch Linux package, in Java: commons-compress uses it when it's there
    implementation("org.tukaani:xz:1.10")

    // JUnit, and TestKit to run the plugin in a real build
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
    testImplementation("org.apache.commons:commons-compress:1.28.0")
}

gradlePlugin {
    plugins {
        create("lwjwae") {
            id = "dev.ivchenko.lwjwae"
            implementationClass = "dev.ivchenko.lwjwae.gradle.LwjwaePlugin"
            displayName = "lwjwae"
            description = project.description
        }
    }
}

// The versions from gradle.properties go into the JAR file, where the plugin reads its defaults.
val defaultVersions = tasks.register<WriteProperties>("defaultVersions") {
    description = "Writes the default library and codec versions into a resource."
    destinationFile = layout.buildDirectory.file("generated/versions/dev/ivchenko/lwjwae/gradle/versions.properties")
    property("lwjwae", lwjwaeVersion)
    property("lwjwae-codecs", lwjwaeCodecsVersion)
}

sourceSets.main {
    resources.srcDir(defaultVersions.map { layout.buildDirectory.dir("generated/versions") })
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://github.com/fakeivchenko/lwjwae-gradle-plugin")
            inceptionYear.set("2026")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("fakeivchenko")
                    name.set("Anton Ivchenko")
                    email.set("fakeivchenko@gmail.com")
                    url.set("https://github.com/fakeivchenko")
                }
            }
            scm {
                url.set("https://github.com/fakeivchenko/lwjwae-gradle-plugin")
                connection.set("scm:git:https://github.com/fakeivchenko/lwjwae-gradle-plugin.git")
                developerConnection.set("scm:git:git@github.com:fakeivchenko/lwjwae-gradle-plugin.git")
            }
            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/fakeivchenko/lwjwae-gradle-plugin/issues")
            }
            ciManagement {
                system.set("GitHub Actions")
                url.set("https://github.com/fakeivchenko/lwjwae-gradle-plugin/actions")
            }
        }
    }
    repositories {
        maven {
            name = "reposilite"
            url = uri("https://repo.ivchenko.dev/releases")
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
        }
    }
}

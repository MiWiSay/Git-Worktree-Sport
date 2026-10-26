import org.gradle.api.tasks.bundling.Zip

plugins {
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.miwi"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val defaultAndroidStudioPath = when {
    System.getProperty("os.name").lowercase().contains("windows") -> "C:/Program Files/Android/Android Studio Panda 3"
    System.getProperty("os.name").lowercase().contains("mac") -> "/Applications/Android Studio.app/Contents"
    else -> "/opt/android-studio"
}
val androidStudioPath = providers.gradleProperty("androidStudioPath")
    .orElse(providers.environmentVariable("ANDROID_STUDIO_PATH"))
    .orElse(defaultAndroidStudioPath)
    .get()

dependencies {
    intellijPlatform {
        local(androidStudioPath)
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Git Worktree Sport"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "262.9437.185"

        }
    }
    pluginVerification {
        ides {
            local(file(androidStudioPath))
        }
    }
}

val exportOverview = tasks.register<Sync>("exportOverview") {
    from(layout.projectDirectory.dir("docs/overview"))
    exclude("make-media.java")
    into(layout.projectDirectory.dir("outputs/Overview"))
}

tasks.withType<Zip>().configureEach {
    if (name == "buildPlugin") {
        destinationDirectory.set(layout.projectDirectory.dir("outputs"))
        dependsOn(exportOverview)
    }
}

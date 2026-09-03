import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

plugins {
    base
}

val media3Artifacts = configurations.create("media3Artifacts") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.ANDROID)
        )
    }
}

dependencies {
    media3Artifacts(libs.media3.muxer) {
        exclude(group = "androidx.annotation")
        exclude(group = "org.jetbrains.kotlin")
    }
}

val extractedClasses = layout.buildDirectory.dir("media3-classes")
val extractMedia3Classes = tasks.register("extractMedia3Classes") {
    inputs.files(media3Artifacts)
    outputs.dir(extractedClasses)

    doLast {
        val destination = extractedClasses.get().asFile
        delete(destination)
        destination.mkdirs()

        media3Artifacts.files.sortedBy(File::getName).forEach { artifact ->
            if (artifact.extension == "aar") {
                ZipFile(artifact).use { archive ->
                    val classesEntry = archive.getEntry("classes.jar")
                        ?: error("${artifact.name} does not contain classes.jar")
                    val classesJar = temporaryDir.resolve("${artifact.nameWithoutExtension}.jar")
                    archive.getInputStream(classesEntry).use { input ->
                        Files.copy(
                            input,
                            classesJar.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    copy {
                        from(zipTree(classesJar))
                        into(destination)
                    }
                }
            } else if (artifact.extension == "jar") {
                copy {
                    from(zipTree(artifact))
                    into(destination)
                }
            }
        }
    }
}

val shadowMedia3 = tasks.register<ShadowJar>("shadowMedia3") {
    dependsOn(extractMedia3Classes)
    from(extractedClasses)
    archiveBaseName.set("media3-isolated")
    archiveClassifier.set("")
    relocate(
        "androidx.media3",
        "app.morphe.extension.crimera.internal.media3"
    )
    relocate(
        "com.google",
        "app.morphe.extension.crimera.internal.google"
    )
}

configurations.create("shadowedMedia3") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(shadowMedia3)
}

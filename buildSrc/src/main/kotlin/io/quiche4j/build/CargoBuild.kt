package io.quiche4j.build

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
abstract class CargoBuild : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val cargoToml: RegularFileProperty

  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val cargoLock: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val rustSources: ConfigurableFileCollection

  @get:Input
  abstract val profile: Property<String>

  @get:OutputDirectory
  abstract val targetDir: DirectoryProperty

  @get:Inject
  abstract val execOps: ExecOperations

  @TaskAction
  fun run() {
    val workDir = cargoToml.get().asFile.parentFile
    val target = targetDir.get().asFile
    target.mkdirs()
    execOps.exec {
      workingDir = workDir
      commandLine(
        "cargo", "build",
        "--lib",
        "--${profile.get()}",
        "--target-dir", target.absolutePath,
      )
    }
  }
}

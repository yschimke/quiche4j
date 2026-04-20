package io.quiche4j.build

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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

/**
 * Cross-compiles the quiche4j-jni Rust crate for one or more Android ABIs using cargo-ndk.
 * Produces .so files under `<targetDir>/<abi>/libquiche_jni.so` matching Android's standard
 * jniLibs layout, which is also what NativeUtils expects when the `android-<abi>` platform
 * directory is used on the classpath.
 *
 * Requires:
 *   * cargo-ndk installed (`cargo install cargo-ndk`)
 *   * the Android NDK installed (path passed via [ndkHome])
 *   * the target rustup triples installed (e.g. `rustup target add aarch64-linux-android`)
 */
@CacheableTask
abstract class CargoNdkBuild : DefaultTask() {
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

  /**
   * Android ABIs to build. Valid values include `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
   * Keep the list small by default — each ABI is ~4-5 MiB.
   */
  @get:Input
  abstract val abis: ListProperty<String>

  /** Minimum SDK (API level) to target. 21 matches `android-test`'s current minSdk. */
  @get:Input
  abstract val platformLevel: Property<Int>

  /** Path to the Android NDK (e.g. `$ANDROID_HOME/ndk/30.0.14904198`). */
  @get:Input
  abstract val ndkHome: Property<String>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Inject
  abstract val execOps: ExecOperations

  @TaskAction
  fun run() {
    val workDir = cargoToml.get().asFile.parentFile
    val out = outputDir.get().asFile
    out.mkdirs()

    val abiList = abis.get()
    check(abiList.isNotEmpty()) { "At least one Android ABI must be configured." }

    val args = mutableListOf("ndk")
    for (abi in abiList) {
      args += "-t"
      args += abi
    }
    args += "--platform"
    args += platformLevel.get().toString()
    args += "-o"
    args += out.absolutePath
    args += "build"
    args += "--lib"
    args += "--release"

    execOps.exec {
      workingDir = workDir
      environment("ANDROID_NDK_HOME", ndkHome.get())
      commandLine(listOf("cargo") + args)
    }
  }
}

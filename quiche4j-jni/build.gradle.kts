import io.quiche4j.build.CargoBuild
import io.quiche4j.build.CargoNdkBuild
import java.io.File

plugins {
  `java-library`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

// Detect host platform at configuration time (config-cache safe via providers).
val osNameProvider = providers.systemProperty("os.name").map { it.lowercase() }
val osArchProvider = providers.systemProperty("os.arch").map { it.lowercase() }

fun detectPlatformDir(osName: String, osArch: String): String {
  val os = when {
    osName.contains("linux") -> "linux"
    osName.contains("mac") || osName.contains("darwin") -> "osx"
    osName.contains("win") -> "windows"
    else -> error("Unsupported OS: $osName")
  }
  val arch = when (osArch) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> error("Unsupported arch: $osArch")
  }
  return "$os-$arch"
}

fun detectLibExt(osName: String): String = when {
  osName.contains("linux") -> "so"
  osName.contains("mac") || osName.contains("darwin") -> "dylib"
  osName.contains("win") -> "dll"
  else -> error("Unsupported OS: $osName")
}

val platformDir = osNameProvider.zip(osArchProvider, ::detectPlatformDir).get()
val libExt = osNameProvider.map(::detectLibExt).get()

val cargoBuild = tasks.register<CargoBuild>("cargoBuild") {
  description = "Compiles the quiche-jni Rust crate via cargo."
  group = "build"
  cargoToml.set(layout.projectDirectory.file("Cargo.toml"))
  cargoLock.set(layout.projectDirectory.file("Cargo.lock"))
  rustSources.from(fileTree(layout.projectDirectory.dir("src")).matching { include("**/*.rs") })
  profile.set("release")
  targetDir.set(layout.buildDirectory.dir("cargo"))
}

// Stage the compiled native library under native-libs/<platform>/ so consumers can
// loadEmbeddedLibrary() it off the classpath exactly the way NativeUtils expects.
val stageNativeLib = tasks.register<Copy>("stageNativeLib") {
  description = "Stages the compiled native library into the JAR resource layout."
  group = "build"
  from(cargoBuild.flatMap { it.targetDir.dir("release") }) {
    include("*.so", "*.dylib", "*.dll")
  }
  into(layout.buildDirectory.dir("native-staged/native-libs/$platformDir"))
}

// Optional: cross-compile for Android ABIs via cargo-ndk. Opt-in because it requires the NDK
// + rustup targets. Enable by setting -PandroidAbis=arm64-v8a,x86_64 (or any subset).
// On enabled builds the Android .so files are packaged into the same JAR under
// `native-libs/android-<abi>/` and picked up by NativeUtils at runtime when running on Android.
val androidAbis: String? = providers.gradleProperty("androidAbis").orNull
val detectedNdkHome: String =
  providers.environmentVariable("ANDROID_NDK_HOME").orNull
    ?: providers.environmentVariable("ANDROID_HOME").orNull?.let { sdk ->
      File("$sdk/ndk").takeIf { it.isDirectory }?.listFiles()?.maxByOrNull { it.name }?.absolutePath
    } ?: ""
val androidMinSdk: Int = providers.gradleProperty("androidMinSdk").map { it.toInt() }.orElse(21).get()

val cargoBuildAndroid =
  tasks.register<CargoNdkBuild>("cargoBuildAndroid") {
    description = "Cross-compiles quiche-jni for Android ABIs (set -PandroidAbis=...)."
    group = "build"
    cargoToml.set(layout.projectDirectory.file("Cargo.toml"))
    cargoLock.set(layout.projectDirectory.file("Cargo.lock"))
    rustSources.from(fileTree(layout.projectDirectory.dir("src")).matching { include("**/*.rs") })
    abis.set(androidAbis?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
    platformLevel.set(androidMinSdk)
    ndkHome.set(detectedNdkHome)
    // Output the ABI-keyed .so files directly in Android's expected APK layout. AGP extracts
    // anything under `lib/<abi>/` in a JAR dependency into the final APK's jniLibs.
    outputDir.set(layout.buildDirectory.dir("native-staged-android/lib"))
    enabled = !androidAbis.isNullOrBlank() && detectedNdkHome.isNotEmpty()
  }

tasks.named<Jar>("jar") {
  dependsOn(stageNativeLib)
  from(layout.buildDirectory.dir("native-staged"))
  if (!androidAbis.isNullOrBlank()) {
    dependsOn(cargoBuildAndroid)
    // Pull only libquiche_jni.so from each ABI directory cargo-ndk wrote; skip stray helper
    // shared objects that cargo-ndk emits alongside the target artifact.
    from(layout.buildDirectory.dir("native-staged-android")) {
      include("lib/*/libquiche_jni.so")
    }
  }
  manifest {
    attributes(
      "Quiche4j-Jni-Platform" to platformDir,
      "Quiche4j-Jni-LibName" to "libquiche_jni.$libExt",
    )
  }
}

tasks.register<Delete>("cleanCargo") {
  delete(layout.buildDirectory.dir("cargo"))
}

tasks.named("clean") {
  dependsOn("cleanCargo")
}

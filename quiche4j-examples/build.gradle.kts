plugins {
  `java-library`
  application
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(project(":quiche4j-core"))
  implementation("io.netty:netty-all:4.1.118.Final")
}

application {
  // Override with -PmainClass= to run the server or netty client.
  mainClass.set(providers.gradleProperty("mainClass").orElse("io.quiche4j.examples.Http3Client"))
}

tasks.named<JavaExec>("run") {
  // Mirror the shell scripts: forward quiche log level via env var.
  environment("QUICHE4J_JNI_LOG", providers.environmentVariable("QUICHE4J_JNI_LOG").orElse("info").get())
}

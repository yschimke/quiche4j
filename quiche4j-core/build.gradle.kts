plugins {
  `java-library`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  withSourcesJar()
}

dependencies {
  api(project(":quiche4j-jni"))
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}

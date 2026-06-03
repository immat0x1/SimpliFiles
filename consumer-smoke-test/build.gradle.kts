plugins {
    kotlin("jvm") version "2.3.21"
    `java-library`
}

val simplifilesVersion = providers.fileContents(layout.projectDirectory.file("../VERSION"))
    .asText
    .map { it.trim() }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.immat0x1:simplifiles:${simplifilesVersion.get()}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

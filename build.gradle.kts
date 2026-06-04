plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
    `java-library`
}

val projectVersion = providers.fileContents(layout.projectDirectory.file("VERSION"))
    .asText
    .map { it.trim() }

group = "io.github.immat0x1"
version = projectVersion.get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=8")
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
    options.release.set(8)
}

val mainSourceSet = sourceSets.named("main").get()
val jmhSourceSet = sourceSets.create("jmh") {
    java.srcDir("src/jmh/java")
    compileClasspath += mainSourceSet.output
    runtimeClasspath += output + compileClasspath
}

dependencies {
    "jmhAnnotationProcessor"(libs.jmh.generator.annprocess)
    "jmhImplementation"(libs.jmh.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("validateProjectVersion") {
    val versionFile = layout.projectDirectory.file("VERSION")

    inputs.file(versionFile)

    doLast {
        val versionValue = versionFile.asFile.readText().trim()
        val semverPattern = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(-[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?" +
                "(\\+[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?$",
        )

        check(semverPattern.matches(versionValue)) {
            "VERSION must follow SemVer, for example 0.1.0 or 0.1.0-SNAPSHOT."
        }
    }
}

tasks.named("check") {
    dependsOn("validateProjectVersion")
}

fun JavaExec.configureJmhTask(
    resultFileName: String,
    benchmarkArgs: List<String>,
) {
    group = "verification"
    dependsOn(tasks.named("jmhClasses"))
    mainClass.set("org.openjdk.jmh.Main")
    classpath = jmhSourceSet.runtimeClasspath

    val resultFile = layout.buildDirectory.file("reports/jmh/$resultFileName")
    doFirst {
        resultFile.get().asFile.parentFile.mkdirs()
    }

    args(benchmarkArgs)
    args("-rf", "json")
    args("-rff", resultFile.get().asFile.absolutePath)

    providers.gradleProperty("jmhIncludes").orNull?.let { includePattern ->
        args(includePattern)
    }
}

tasks.register<JavaExec>("jmhQuick") {
    description = "Runs a short JMH smoke benchmark suite."
    configureJmhTask(
        resultFileName = "quick-results.json",
        benchmarkArgs = listOf(
            "-wi",
            "1",
            "-i",
            "1",
            "-f",
            "1",
            "-w",
            "1s",
            "-r",
            "1s",
            "-tu",
            "ms",
        ),
    )
}

tasks.register<JavaExec>("jmh") {
    description = "Runs the JMH benchmark suite."
    configureJmhTask(
        resultFileName = "results.json",
        benchmarkArgs = listOf(
            "-wi",
            "3",
            "-i",
            "5",
            "-f",
            "1",
            "-w",
            "3s",
            "-r",
            "3s",
            "-tu",
            "ms",
        ),
    )
}

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        artifactId = "simplifiles",
        version = version.toString(),
    )

    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("SimpliFiles")
        description.set("Safe and convenient file and archive toolkit for Java and Kotlin.")
        url.set("https://github.com/immat0x1/SimpliFiles")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("immat0x1")
                name.set("immat0x1")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/immat0x1/SimpliFiles.git")
            developerConnection.set("scm:git:ssh://git@github.com/immat0x1/SimpliFiles.git")
            url.set("https://github.com/immat0x1/SimpliFiles")
        }

        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/immat0x1/SimpliFiles/issues")
        }
    }
}

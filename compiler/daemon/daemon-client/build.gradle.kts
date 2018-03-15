import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

description = "Kotlin Daemon Client"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

val nativePlatformVariants: List<String> by rootProject.extra

dependencies {
    compileOnly(project(":compiler:util"))
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":compiler:daemon-common"))
    compileOnly(project(":kotlin-reflect-api"))
    compileOnly(commonDep("net.rubygrapefruit", "native-platform"))
    compileOnly(intellijDep()) { includeIntellijCoreJarDependencies(project) }

    embeddedComponents(project(":compiler:daemon-common")) { isTransitive = false }
    embeddedComponents(commonDep("net.rubygrapefruit", "native-platform"))
    nativePlatformVariants.forEach {
        embeddedComponents(commonDep("net.rubygrapefruit", "native-platform", "-$it"))
    }
    compile(projectDist(":kotlin-reflect"))
    compile(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) { isTransitive = false }
    compile(commonDep("io.ktor", "ktor-network")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

noDefaultJar()

runtimeJar(task<ShadowJar>("shadowJar")) {
    from(the<JavaPluginConvention>().sourceSets.getByName("main").output)
    fromEmbeddedComponents()
}

sourcesJar()
javadocJar()

dist()

ideaPlugin()

publish()

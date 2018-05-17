import com.sun.javafx.scene.CameraHelper.project

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

dependencies {
    compile(project(":core:descriptors"))
    compile(project(":core:descriptors.jvm"))
    compile(project(":compiler:util"))
    compile(project(":compiler:cli-common"))
    compile(projectDist(":kotlin-stdlib"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    compileOnly(intellijDep()) { includeIntellijCoreJarDependencies(project) }
    compile(projectDist(":kotlin-reflect"))
    compile(project(":kotlin-reflect-api"))
    compile(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) { isTransitive = false }
    compile(commonDep("io.ktor", "ktor-network")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

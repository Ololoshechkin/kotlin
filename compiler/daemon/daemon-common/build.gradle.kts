import com.sun.javafx.scene.CameraHelper.project

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

repositories {
    mavenCentral()
    maven {
        setUrl("http://dl.bintray.com/kotlin/ktor")
    }
}

dependencies {
    compile(project(":core:descriptors"))
    compile(project(":core:descriptors.jvm"))
    compile(project(":compiler:util"))
    compile(project(":compiler:cli-common"))
    compile(projectDist(":kotlin-stdlib"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    compileOnly(intellijDep()) { includeIntellijCoreJarDependencies(project) }
    compile(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) { isTransitive = false }
    compile("io.ktor:ktor-network:0.9.1-alpha-10")
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

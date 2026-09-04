plugins {
    kotlin("jvm") version "2.4.10"
    application
    antlr
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

configurations.api {
    setExtendsFrom(extendsFrom.filterNot { it.name == "antlr" })
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "com.alsaril.bf.MainKt"
}

val antlrPackage = "com.alsaril.bf"

tasks.generateGrammarSource {
    outputDirectory = file(
        "${project.layout.buildDirectory.asFile.get()}/generated/sources/main/java/antlr/" +
            antlrPackage.replace('.', '/')
    )
    arguments = listOf("-visitor", "-package", antlrPackage)
}
sourceSets {
    main {
        java {
            srcDir(tasks.generateGrammarSource)
        }
    }
}
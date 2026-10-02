plugins {
    application
}

dependencies {
    implementation(project(":codegen"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    testImplementation(testFixtures(project(":codegen")))
}

application {
    mainClass = "com.alsaril.math.MainKt"
}

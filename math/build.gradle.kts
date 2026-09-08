plugins {
    application
}

dependencies {
    implementation(project(":codegen"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
}

application {
    mainClass = "com.alsaril.math.MainKt"
}

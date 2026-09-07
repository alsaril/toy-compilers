plugins {
    application
}

dependencies {
    implementation(project(":codegen"))
}

application {
    mainClass = "com.alsaril.bf.MainKt"
}

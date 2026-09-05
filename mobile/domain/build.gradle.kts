plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // `api`: domain's public types (RequestRecord, use case signatures) expose shared model classes,
    // so every consumer needs them on its compile classpath.
    api(project(":shared"))
    implementation(libs.koin.core)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

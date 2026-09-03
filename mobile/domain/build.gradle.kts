plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.koin.core)
    
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    `java-library`
}

dependencies {
    // Provided at runtime by Paper; shaded into the Geyser extension.
    compileOnlyApi(libs.gson)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

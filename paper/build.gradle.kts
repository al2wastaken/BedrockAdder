plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.floodgate.api)
    compileOnly(libs.geyser.api)
    // ItemsAdder is a paid plugin and is not on any public repository.
    // Put your licensed jar at libs/ItemsAdder-<version>.jar (it is git-ignored).
    compileOnly(fileTree(rootProject.file("libs")) { include("ItemsAdder*.jar") })
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }

    // Ship the Geyser extension inside the plugin; it installs itself into Geyser-Spigot/extensions.
    val extensionJar = project(":geyser-extension").tasks.named<Jar>("shadowJar")
    dependsOn(extensionJar)
    from(extensionJar.flatMap { it.archiveFile }) {
        rename { "BedrockAdderExtension.jar" }
    }
}

tasks.shadowJar {
    archiveBaseName.set("BedrockAdder")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

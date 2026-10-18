plugins {
    id("net.fabricmc.fabric-loom") version "1.19.1"
}

group = "org.kkaemok.shimmer"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    compileOnly("com.terraformersmc:modmenu:21.0.0")
    if (providers.gradleProperty("withModMenu").orElse("false").get().toBoolean()) {
        runtimeOnly("com.terraformersmc:modmenu:21.0.0")
    }
    // Mod shader resources must be exposed to vanilla ResourceManager. Bundle only these
    // two small Fabric API modules, not the full API distribution.
    include(implementation("net.fabricmc.fabric-api:fabric-api-base:2.0.6+fcdff87f5d")!!)
    include(implementation("net.fabricmc.fabric-api:fabric-resource-loader-v1:3.1.0+5af7744b5d")!!)
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

loom {
    runs {
        named("client") {
            programArgs("--offlineDeveloperMode", "--graphicsBackend", providers.gradleProperty("shimmerBackend").orElse("OPENGL").get())
            vmArg("--enable-native-access=ALL-UNNAMED")
        }
    }
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("generateTestPack") {
    group = "verification"
    description = "Generate original labeled RGBE fixtures, including HDR highlight steps"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "org.kkaemok.shimmer.GenerateTestPack"
    args(layout.buildDirectory.dir("test-resourcepack").get().asFile.absolutePath)
}

tasks.register<Zip>("projectZip") {
    group = "distribution"
    description = "Package the standalone project, excluding local game files and personal packs"
    archiveFileName.set("Shimmer-${project.version}-project.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.projectDirectory) {
        include("src/**", "gradle/**", "gradlew", "gradlew.bat", "*.gradle.kts", "gradle.properties", "README.md", ".gitignore", ".gitattributes")
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

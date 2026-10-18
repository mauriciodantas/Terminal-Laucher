// The command bar engine: parsing, suggestions and the rules behind each command. It does not
// depend on any launcher, so commands can grow here without touching the apps that share it, and
// its rules are tested on the JVM.
//
// What differs between those apps (a theme, a widget, the launcher's folders...) lives in the
// `command-host/` folder at the root of each one, which this module compiles together with its own
// code (see CommandHost). `command-host/host.gradle.kts`, when it exists, adds what that code needs.
plugins {
    alias(libs.plugins.android.library)
}

val host = rootProject.file("command-host")

android {
    namespace = "app.lawnchair.command"

    sourceSets {
        getByName("main") {
            java.directories.add("src")
            kotlin.directories.add("src")
            res.directories.add("res")
            manifest.srcFile("AndroidManifest.xml")
            if (host.resolve("src").exists()) {
                java.directories.add(host.resolve("src").path)
                kotlin.directories.add(host.resolve("src").path)
            }
            if (host.resolve("res").exists()) res.directories.add(host.resolve("res").path)
        }
        getByName("test") {
            java.directories.add("tests/src")
            kotlin.directories.add("tests/src")
            if (host.resolve("tests/src").exists()) {
                java.directories.add(host.resolve("tests/src").path)
                kotlin.directories.add(host.resolve("tests/src").path)
            }
            if (host.resolve("tests/resources").exists()) resources.directories.add(host.resolve("tests/resources").path)
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    // Draws the QR codes of "qr".
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    // The JVM tests run outside Android, whose org.json is only a stub there.
    testImplementation("org.json:json:20240303")
}

if (host.resolve("host.gradle.kts").exists()) apply(from = host.resolve("host.gradle.kts"))

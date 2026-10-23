import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The model is bundled in the APK so dictation works offline, but it is downloaded at build time
// (and checked against a pinned hash) instead of being committed to git.
val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
val modelSha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
val modelAssetsDir = layout.buildDirectory.dir("generated/vosk-assets")

val downloadVoskModel by tasks.registering {
    val zipFile = layout.buildDirectory.file("downloads/" + modelUrl.substringAfterLast('/'))
    inputs.property("sha256", modelSha256)
    outputs.dir(modelAssetsDir)
    doLast {
        val zip = zipFile.get().asFile
        if (!zip.exists() || sha256(zip.readBytes()) != modelSha256) {
            zip.parentFile.mkdirs()
            URI(modelUrl).toURL().openStream().use { input -> zip.outputStream().use(input::copyTo) }
            val actual = sha256(zip.readBytes())
            check(actual == modelSha256) { "Vosk model checksum mismatch: $actual" }
        }
        // Unpack into assets/vosk-model/, dropping the archive's top-level folder.
        val target = modelAssetsDir.get().asFile.resolve("vosk-model")
        target.deleteRecursively()
        ZipInputStream(zip.inputStream()).use { entries ->
            generateSequence { entries.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                val relative = entry.name.substringAfter('/')
                val out = target.resolve(relative).normalize()
                check(out.startsWith(target)) { "Bad zip entry ${entry.name}" }
                out.parentFile.mkdirs()
                out.outputStream().use(entries::copyTo)
            }
        }
    }
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

android {
    namespace = "dev.remotedictation.stt.vosk"
    compileSdk = 35
    defaultConfig.minSdk = 26
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    sourceSets["main"].assets.srcDir(modelAssetsDir)
}

tasks.named("preBuild") { dependsOn(downloadVoskModel) }

dependencies {
    api(project(":core"))
    implementation(libs.vosk.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // Vosk needs JNA's Android packaging (native libs inside the AAR), not the desktop jar.
    implementation(libs.jna) { artifact { type = "aar" } }
}

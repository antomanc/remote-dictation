import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// whisper.cpp is compiled from a pinned release and the model is bundled in the APK; both are
// downloaded at build time and checked against pinned hashes instead of being committed to git.
val whisperVersion = "1.9.5"
val whisperUrl = "https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v$whisperVersion.tar.gz"
val whisperSha256 = "ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720"
val modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.en-q8_0.bin"
val modelSha256 = "a4d4a0768075e13cfd7e19df3ae2dbc4a68d37d36a7dad45e8410c9a34f8c87e"

val whisperSrcDir = layout.buildDirectory.dir("whisper.cpp")
val modelAssetsDir = layout.buildDirectory.dir("generated/whisper-assets")

val downloadWhisperSource by tasks.registering {
    val archive = layout.buildDirectory.file("downloads/whisper.cpp-$whisperVersion.tar.gz")
    inputs.property("sha256", whisperSha256)
    outputs.file(archive)
    doLast { download(whisperUrl, archive.get().asFile, whisperSha256) }
}

val unpackWhisperSource by tasks.registering(Sync::class) {
    dependsOn(downloadWhisperSource)
    from(tarTree(resources.gzip(downloadWhisperSource.map { it.outputs.files.singleFile }))) {
        // Drop the archive's top-level folder.
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(whisperSrcDir)
}

val downloadWhisperModel by tasks.registering {
    inputs.property("sha256", modelSha256)
    outputs.dir(modelAssetsDir)
    doLast { download(modelUrl, modelAssetsDir.get().asFile.resolve("whisper-model.bin"), modelSha256) }
}

fun download(url: String, target: File, expectedSha256: String) {
    if (target.exists() && sha256(target) == expectedSha256) return
    target.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> target.outputStream().use(input::copyTo) }
    val actual = sha256(target)
    check(actual == expectedSha256) { "Checksum mismatch for $url: $actual" }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

android {
    namespace = "dev.remotedictation.stt.whisper"
    compileSdk = 35
    ndkVersion = "27.3.13750724"
    defaultConfig {
        minSdk = 26
        ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        externalNativeBuild.cmake {
            arguments += listOf("-DWHISPER_SRC=${whisperSrcDir.get().asFile.absolutePath}", "-DANDROID_STL=c++_static")
        }
    }
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.31.6"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    sourceSets["main"].assets.srcDir(modelAssetsDir)
}

tasks.named("preBuild") { dependsOn(unpackWhisperSource, downloadWhisperModel) }
tasks.configureEach {
    if (name.startsWith("configureCMake")) dependsOn(unpackWhisperSource)
}

dependencies {
    api(project(":core"))
    testImplementation(libs.junit)
}

package atropos.android.build

object AndroidSideloadPipelineConfig {
    const val APPLICATION_ID = "com.atropos.android"
    const val MIN_SDK = 26
    const val TARGET_SDK = 35
    const val VERSION_NAME = "1.0.0"

    fun generateReleaseFilename(commitHash: String): String =
        "atropos-v$VERSION_NAME-$commitHash-release.apk"

    fun generateCiWorkflowYaml(): String = """
        name: Android Sideload Release
        on:
          push:
            tags:
              - 'v*'
        jobs:
          build-apk:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  distribution: 'temurin'
                  java-version: '17'
              - name: Build Signed Release APK
                run: ./gradlew :app:assembleRelease
              - name: Compute Checksum
                run: sha256sum app/build/outputs/apk/release/*.apk > checksums.txt
              - name: Publish Sideload Release
                uses: softprops/action-gh-release@v2
                with:
                  files: |
                    app/build/outputs/apk/release/*.apk
                    checksums.txt
    """.trimIndent()
}

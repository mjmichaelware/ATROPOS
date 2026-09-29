# Sideload APK Pipeline (F-AND-011)

This document describes the CI/CD pipeline for building, signing, and distributing the ATROPOS Android APK for sideload installation.

## Pipeline Overview

The pipeline consists of three stages:
1. **Build** - Compile debug and release APKs
2. **Sign** - Sign release APK with production keystore
3. **Distribute** - Upload to GitHub Releases with metadata

## GitHub Actions Workflow

```yaml
# .github/workflows/android-sideload.yml
name: Android Sideload Pipeline

on:
  push:
    tags:
      - 'v*'  # Trigger on version tags
  workflow_dispatch:
    inputs:
      variant:
        description: 'Build variant'
        required: true
        default: 'release'
        type: choice
        options:
          - release
          - debug

env:
  GRADLE_VERSION: '8.5'
  JAVA_VERSION: '17'

jobs:
  build:
    name: Build APK
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ env.JAVA_VERSION }}
          cache: gradle

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v3
        with:
          gradle-version: ${{ env.GRADLE_VERSION }}

      - name: Build APK
        run: |
          ./gradlew :app:assemble${{ github.event.inputs.variant == 'release' && 'Release' || 'Debug' }}
        env:
          ANDROID_SDK_ROOT: ${{ runner.env.ANDROID_SDK_ROOT }}

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: atropos-${{ github.event.inputs.variant }}-apk
          path: app/build/outputs/apk/${{ github.event.inputs.variant }}/*.apk
          retention-days: 30

  sign:
    name: Sign Release APK
    needs: build
    if: github.event.inputs.variant == 'release'
    runs-on: ubuntu-latest
    permissions:
      contents: write
      id-token: write
    steps:
      - name: Download APK
        uses: actions/download-artifact@v4
        with:
          name: atropos-release-apk
          path: artifacts

      - name: Sign APK
        id: sign
        uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: artifacts
          signingKeyBase64: ${{ secrets.ANDROID_SIGNING_KEY_BASE64 }}
          alias: ${{ secrets.ANDROID_KEY_ALIAS }}
          keyStorePassword: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          keyPassword: ${{ secrets.ANDROID_KEY_PASSWORD }}

      - name: Verify Signature
        run: |
          apksigner verify --print-certs artifacts/atropos-release-signed.apk

      - name: Upload Signed APK
        uses: actions/upload-artifact@v4
        with:
          name: atropos-release-signed
          path: artifacts/atropos-release-signed.apk
          retention-days: 90

  release:
    name: Create GitHub Release
    needs: [build, sign]
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Download Artifacts
        uses: actions/download-artifact@v4
        with:
          path: artifacts
          merge-multiple: true

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            artifacts/atropos-release-signed.apk
            artifacts/atropos-debug.apk
          generate_release_notes: true
          draft: false
          prerelease: false
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  verify:
    name: Verify Installation
    needs: release
    runs-on: ubuntu-latest
    steps:
      - name: Download Release APK
        uses: actions/download-artifact@v4
        with:
          name: atropos-release-signed
          path: artifacts

      - name: Verify APK
        run: |
          # Verify APK can be installed
          aapt dump badging artifacts/atropos-release-signed.apk
          
          # Verify signature
          apksigner verify --print-certs artifacts/atropos-release-signed.apk
          
          # Verify minimum SDK
          aapt dump badging artifacts/atropos-release-signed.apk | grep "sdkVersion"
          
          # Verify target SDK
          aapt dump badging artifacts/atropos-release-signed.apk | grep "targetSdkVersion"
```

## Required Secrets

The following secrets must be configured in the GitHub repository:

| Secret | Description |
|--------|-------------|
| `ANDROID_SIGNING_KEY_BASE64` | Base64-encoded keystore file |
| `ANDROID_KEY_ALIAS` | Key alias in keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_PASSWORD` | Key password |

## Keystore Generation

```bash
# Generate a new keystore (run once, store securely)
keytool -genkeypair \
  -alias atropos-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore atropos-release.keystore \
  -storepass <password> \
  -keypass <password> \
  -dname "CN=ATROPOS, OU=Engineering, O=ATROPOS, L=Unknown, ST=Unknown, C=US"

# Convert to base64 for GitHub secret
base64 -w0 atropos-release.keystore | tee ANDROID_SIGNING_KEY_BASE64.txt
```

## Sideload Installation

Operators can install the APK via:

### ADB (Recommended)
```bash
# Download from GitHub Releases
wget https://github.com/ATROPOS/ATROPOS/releases/download/v1.0.0/atropos-release-signed.apk

# Install via ADB
adb install atropos-release-signed.apk

# Or update existing
adb install -r atropos-release-signed.apk
```

### Direct Install (Termux)
```bash
# In Termux on Android
pkg install android-tools
wget https://github.com/ATROPOS/ATROPOS/releases/download/v1.0.0/atropos-release-signed.apk
adb install atropos-release-signed.apk
```

### Manual Install
1. Download APK from GitHub Releases page
2. Transfer to device
3. Enable "Install unknown apps" for the file manager/browser
4. Tap APK to install

## Verification Checklist

Before releasing, verify:

- [ ] APK installs on API 24+ (Android 7.0+)
- [ ] APK signature verifies with `apksigner verify`
- [ ] App launches and connects to bridge on `127.0.0.1:8787`
- [ ] Composer input works (IME, 44dp targets)
- [ ] Conversation stream renders correctly
- [ ] Approval cards appear and function
- [ ] Offline queue persists and drains on reconnect
- [ ] File tree sheet loads project structure
- [ ] Visual components render without crashes

## Versioning

Follow semantic versioning:
- `v1.0.0` - Major release
- `v1.1.0` - Minor feature release
- `v1.0.1` - Patch/bugfix release

Tags must match `v*` pattern to trigger release pipeline.

## Rollback Procedure

If a release has critical issues:

1. Create a hotfix branch from the tag
2. Fix the issue
3. Tag as `v1.0.1` (patch version)
4. Pipeline will build and release automatically
5. Previous version remains available in GitHub Releases history

## Security Notes

- Keystore must be stored only in GitHub Secrets (never in repo)
- Keystore password should be rotated annually
- APKs are signed with v2 (APK Signature Scheme v2) and v3 signatures
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
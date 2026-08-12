#!/usr/bin/env bash
# Install the ATROPOS APK on this device using on-device wireless debugging.
#
#   bash scripts/adb-install.sh [path-to.apk]
#
# adb runs on the phone itself here, so there is no USB host: the device is
# reached over loopback via Android's Wireless debugging. The step that trips
# people is that pairing and connecting use *different ports*, shown on two
# different screens, and the pairing port changes every time the dialog opens.
#
# Why bother instead of tapping the APK: the GUI installer reports every
# failure as "app wasn't installed". adb prints the actual reason —
# INSTALL_FAILED_UPDATE_INCOMPATIBLE, INSTALL_PARSE_FAILED_NO_CERTIFICATES,
# INSTALL_FAILED_INSUFFICIENT_STORAGE — which is the difference between
# guessing and knowing.
set -euo pipefail

APK="${1:-$HOME/apkcheck/atropos-android-release-unsigned-signed.apk}"
PKG="com.atropos.android.app"

command -v adb >/dev/null 2>&1 || {
    echo "adb not found. Install it:  pkg install android-tools" >&2; exit 1; }

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
echo "APK: $APK"
echo "size: $(du -h "$APK" | cut -f1)"
echo

cat <<'SETUP'
On the phone, leave Termux open in split screen or switch back and forth:

  1. Settings -> About phone -> tap "Build number" 7 times   (if Developer
     options is not already enabled)
  2. Settings -> System -> Developer options -> Wireless debugging -> ON
  3. Inside Wireless debugging, tap "Pair device with pairing code"

That dialog shows:   IP address & Port   and a 6-digit code.
Keep it open — it expires when closed.

SETUP

read -r -p "Pairing PORT from the dialog (the 5-digit number after the colon): " PAIR_PORT
read -r -p "6-digit pairing CODE: " PAIR_CODE
echo

echo "pairing..."
adb start-server >/dev/null 2>&1 || true
if ! adb pair "127.0.0.1:${PAIR_PORT}" "$PAIR_CODE"; then
    echo >&2
    echo "Pairing failed. Usual causes:" >&2
    echo "  - the dialog was closed (the port changes each time it opens)" >&2
    echo "  - the connect port was used instead of the pairing port" >&2
    echo "  - the code expired; reopen the dialog and rerun" >&2
    exit 1
fi
echo

cat <<'CONNECT'
Now close the pairing dialog and look at the main Wireless debugging screen.
It shows "IP address & Port" with a DIFFERENT port. That is the connect port.

CONNECT

read -r -p "Connect PORT from the main Wireless debugging screen: " CONN_PORT
echo

echo "connecting..."
adb connect "127.0.0.1:${CONN_PORT}"
adb wait-for-device
echo
echo "devices:"
adb devices
echo

# Any previously installed copy signed with a different key blocks the install.
# Android reports that as INSTALL_FAILED_UPDATE_INCOMPATIBLE, and through the
# GUI it is indistinguishable from a corrupt file.
echo "existing atropos packages on this device:"
EXISTING="$(adb shell pm list packages 2>/dev/null | sed 's/^package://' | grep -i atropos || true)"
if [ -z "$EXISTING" ]; then
    echo "  (none)"
else
    printf '  %s\n' $EXISTING
    echo
    read -r -p "Uninstall these before installing? [y/N] " ans
    case "${ans:-N}" in
        [yY]*)
            for p in $EXISTING; do
                echo "  uninstalling $p"
                adb uninstall "$p" || echo "  (failed, continuing)"
            done
            ;;
        *) echo "  left in place — a signature mismatch will block the install" ;;
    esac
fi
echo

echo "installing..."
if adb install -r "$APK"; then
    echo
    echo "INSTALLED."
    adb shell pm list packages 2>/dev/null | grep -i atropos || true
    echo
    echo "launch it:"
    echo "  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1"
else
    echo >&2
    echo "Install failed. What the common codes mean:" >&2
    echo "  INSTALL_FAILED_UPDATE_INCOMPATIBLE  an existing copy is signed with a" >&2
    echo "      different key. Uninstall it (rerun and answer y above)." >&2
    echo "  INSTALL_PARSE_FAILED_NO_CERTIFICATES  the APK is unsigned. Use the" >&2
    echo "      signed artifact, not atropos-android-unsigned." >&2
    echo "  INSTALL_FAILED_INSUFFICIENT_STORAGE  free space and retry." >&2
    echo "  INSTALL_FAILED_OLDER_SDK  minSdk is above this device's Android version." >&2
    exit 1
fi

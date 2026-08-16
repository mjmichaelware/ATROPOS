#!/usr/bin/env bash
set -euo pipefail

# Produce distribution metadata and a Debian payload from an already-built
# artifact. This script never builds or installs ATROPOS; callers supply the
# verified JAR explicitly.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-$ROOT/build/libs/ATROPOS.jar}"
OUT="${2:-$ROOT/build/installers}"

test -f "$JAR"
mkdir -p "$OUT"

VERSION="${ATROPOS_VERSION:-0.1.0}"
ARCH="${ATROPOS_DEB_ARCH:-all}"
NAME="atropos"
DEB_ROOT="$OUT/deb/${NAME}_${VERSION}_${ARCH}"
mkdir -p "$DEB_ROOT/DEBIAN" "$DEB_ROOT/usr/share/atropos" "$DEB_ROOT/usr/bin"
install -m 0644 "$JAR" "$DEB_ROOT/usr/share/atropos/atropos.jar"

cat > "$DEB_ROOT/DEBIAN/control" <<EOF
Package: $NAME
Version: $VERSION
Section: utils
Priority: optional
Architecture: $ARCH
Maintainer: ATROPOS maintainers
Description: ATROPOS deterministic software engineering engine
EOF
cat > "$DEB_ROOT/usr/bin/atropos" <<'EOF'
#!/usr/bin/env sh
exec java -jar /usr/share/atropos/atropos.jar "$@"
EOF
chmod 0755 "$DEB_ROOT/usr/bin/atropos"

DEB="$OUT/${NAME}_${VERSION}_${ARCH}.deb"
if command -v dpkg-deb >/dev/null 2>&1; then
    dpkg-deb --build "$DEB_ROOT" "$DEB" >/dev/null
else
    # Keep the deterministic payload available on Termux and other hosts
    # without dpkg; CI or a Debian runner can build the final archive.
    tar -C "$DEB_ROOT" -czf "$DEB.tar.gz" .
fi

cat > "$OUT/atropos.rb" <<EOF
class Atropos < Formula
  desc "Deterministic software engineering engine"
  homepage "https://github.com/mjmichaelware/ATROPOS"
  version "$VERSION"
  url "file://$JAR"
  def install
    libexec.install "ATROPOS.jar"
    (bin/"atropos").write <<~SH
      #!/bin/sh
      exec java -jar #{libexec}/ATROPOS.jar "\\$@"
    SH
  end
end
EOF

cat > "$OUT/atropos-scoop.json" <<EOF
{
  "version": "$VERSION",
  "description": "Deterministic software engineering engine",
  "homepage": "https://github.com/mjmichaelware/ATROPOS",
  "license": "AGPL-3.0-only",
  "url": "file://$JAR",
  "bin": [["ATROPOS.jar", "atropos"]],
  "checkver": { "url": "https://github.com/mjmichaelware/ATROPOS/releases" }
}
EOF

printf '%s\n' \
  'ATROPOS_INSTALLER_METADATA_READY' \
  "jar=$JAR" \
  "output=$OUT" \
  "version=$VERSION"

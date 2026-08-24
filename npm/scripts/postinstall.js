#!/usr/bin/env node
/**
 * Fetches the ATROPOS jar at install time.
 *
 * The jar is downloaded rather than bundled. npm packages are mirrored,
 * cached and vendored in places nobody tracks, and an 8.5 MB binary inside one
 * gets copied into every lockfile-pinned install forever. Fetching from the
 * release the build published keeps one copy with one checksum, and makes the
 * npm package a thin pointer at it rather than a second distribution channel
 * that can drift.
 *
 * No dependencies on purpose: this runs on a phone, at install time, possibly
 * on a metered connection. Node 18's built-in fetch is enough.
 */

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const REPO = process.env.ATROPOS_REPO || "mjmichaelware/ATROPOS";
const VERSION = process.env.ATROPOS_VERSION || "latest";
const BASE = `https://github.com/${REPO}/releases/download/${VERSION}`;
const DEST_DIR = path.join(__dirname, "..", "vendor");
const DEST = path.join(DEST_DIR, "ATROPOS.jar");

async function get(url) {
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} for ${url}`);
  }
  return Buffer.from(await response.arrayBuffer());
}

async function main() {
  // An offline install is a normal thing on the target device. Failing the
  // whole npm install for it would be worse than deferring: the launcher
  // checks for the jar and says what to do, so the package installs and the
  // fetch can happen later.
  if (process.env.ATROPOS_SKIP_DOWNLOAD === "1") {
    console.log("ATROPOS: download skipped (ATROPOS_SKIP_DOWNLOAD=1).");
    return;
  }

  console.log(`ATROPOS: fetching jar (${VERSION}) ...`);
  const jar = await get(`${BASE}/ATROPOS.jar`);

  // Verified against the hash the build published. A jar is executable code,
  // and "it downloaded" is not the same as "it is what was built".
  const expectedDocument = await get(`${BASE}/ATROPOS.jar.sha256`);
  const expected = expectedDocument.toString().trim().split(/\s+/)[0].toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(expected)) {
    throw new Error("published checksum is missing or malformed; nothing was installed.");
  }
  const actual = crypto.createHash("sha256").update(jar).digest("hex");
  if (actual !== expected) {
    throw new Error(
      `checksum mismatch\n  expected ${expected}\n  actual   ${actual}\n` +
        "Nothing was installed."
    );
  }

  fs.mkdirSync(DEST_DIR, { recursive: true });
  fs.writeFileSync(DEST, jar);
  console.log(`ATROPOS: installed ${DEST}`);
}

main().catch((error) => {
  console.error(`ATROPOS: ${error.message}`);
  console.error(
    "The package is installed but has no jar yet. Retry with:\n" +
      "  npm rebuild @mjmichaelware/atropos\n" +
      "or set ATROPOS_JAR to a jar you already have."
  );
  process.exit(1);
});

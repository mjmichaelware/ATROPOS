from pathlib import Path
import json
import tomllib


ROOT = Path(__file__).resolve().parents[1]

REQUIRED = (
    "LICENSE",
    "NOTICE",
    "THIRD_PARTY_NOTICES.md",
    "LICENSES/Apache-2.0.txt",
    "docs/legal/LICENSING.md",
    "docs/legal/DEPENDENCY_LICENSE_POLICY.md",
    "docs/legal/ASSET_LICENSE_POLICY.md",
)


def main() -> int:
    missing = [
        path
        for path in REQUIRED
        if not (ROOT / path).is_file()
    ]

    if missing:
        print("LICENSE CHECK FAILED")

        for path in missing:
            print(f"- missing: {path}")

        return 1

    license_text = (
        ROOT / "LICENSE"
    ).read_text(encoding="utf-8")

    if "Apache License" not in license_text:
        print("LICENSE CHECK FAILED")
        print("- LICENSE is not Apache-2.0")
        return 1

    pyproject = tomllib.loads(
        (ROOT / "pyproject.toml").read_text(
            encoding="utf-8"
        )
    )
    dependencies = pyproject.get(
        "project",
        {},
    ).get("dependencies", [])

    if "pypdf==4.3.1" not in dependencies:
        print("LICENSE CHECK FAILED")
        print("- pyproject.toml must pin pypdf==4.3.1")
        return 1

    notices = (
        ROOT / "THIRD_PARTY_NOTICES.md"
    ).read_text(encoding="utf-8")

    for required_notice in (
        "pypdf 4.3.1",
        "BSD-3-Clause",
        "https://github.com/py-pdf/pypdf",
        "Next.js 16.2.10",
        "React 19.2.7",
        "@supabase/supabase-js 2.108.0",
        "@tanstack/react-query 5.101.2",
        "elkjs 0.11.1",
        "EPL-2.0",
        "axe-core 4.12.1",
        "MPL-2.0",
    ):
        if required_notice not in notices:
            print("LICENSE CHECK FAILED")
            print(
                "- THIRD_PARTY_NOTICES.md missing: "
                + required_notice
            )
            return 1

    web_package = ROOT / "apps" / "web" / "package.json"
    web_lock = ROOT / "apps" / "web" / "package-lock.json"

    if not web_package.is_file() or not web_lock.is_file():
        print("LICENSE CHECK FAILED")
        print("- apps/web package.json and package-lock.json are required")
        return 1

    package = json.loads(web_package.read_text(encoding="utf-8"))
    lock = json.loads(web_lock.read_text(encoding="utf-8"))

    direct = {
        **package.get("dependencies", {}),
        **package.get("devDependencies", {}),
    }

    for name, version in direct.items():
        if not isinstance(version, str) or not version:
            print("LICENSE CHECK FAILED")
            print(f"- invalid version for {name}")
            return 1

        if version[0] in "^~" or version in {"latest", "*"}:
            print("LICENSE CHECK FAILED")
            print(f"- dependency must be exact: {name}")
            return 1

        if any(
            version.startswith(prefix)
            for prefix in (
                "git",
                "github:",
                "file:",
                "http:",
                "https:",
            )
        ):
            print("LICENSE CHECK FAILED")
            print(f"- dependency source is not allowed: {name}")
            return 1

    root_package = lock.get("packages", {}).get("", {})
    locked_direct = {
        **root_package.get("dependencies", {}),
        **root_package.get("devDependencies", {}),
    }

    if locked_direct != direct:
        print("LICENSE CHECK FAILED")
        print("- package-lock direct dependencies do not match package.json")
        return 1

    allowed = {
        "Apache-2.0",
        "MIT",
        "BSD-2-Clause",
        "BSD-3-Clause",
        "ISC",
        "0BSD",
        "EPL-2.0",
        "MPL-2.0",
    }

    for name in direct:
        metadata = lock.get("packages", {}).get(f"node_modules/{name}", {})
        license_name = metadata.get("license")
        if license_name not in allowed:
            print("LICENSE CHECK FAILED")
            print(f"- unsupported or unknown direct license for {name}: {license_name}")
            return 1

    print("LICENSE CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

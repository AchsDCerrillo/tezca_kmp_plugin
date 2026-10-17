#!/usr/bin/env python3
"""Render the start-kmp templates into a project (Python 3 standard library only).

    python3 -I scaffold.py --mode app --dest . --base-package com.acme.gym --app-name KratosGym \
        --app-id com.acme.gym --base-url-prod api.acme.com/ --base-url-dev dev.api.acme.com/

What it does:
  1. Renders templates/base + templates/<mode> into a staging folder, replacing placeholders in
     file contents and paths.
     In SDK mode the core/<module>/src/<sourceSet> trees are merged into the single SDK module
     (core/testing goes to commonTest) and `/* sdk:internal */ public` becomes `internal`.
  2. Copies every staged file that does not exist in --dest yet.
  3. Files named <name>.append are merged line by line into <name> (only missing lines are added),
     e.g. gradle.properties.
  4. Never overwrites: files that already exist are listed as CONFLICT with the staged path, so
     the caller (the agent) merges them by hand. --force overwrites instead (after a .bak copy).

It prints a JSON report on stdout: {"staging": ..., "created": [...], "conflicts": [...], "identical": [...]}.
"""
from __future__ import annotations

import argparse
import json
import plistlib
import re
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEMPLATES = HERE.parent / "assets" / "templates"
SDK_INTERNAL = re.compile(r"/\* sdk:internal \*/ public\b")
SDK_INTERNAL_MARK = re.compile(r"/\* sdk:internal \*/ ")
BINARY_SUFFIXES = {".png", ".jpg", ".jar", ".webp", ".ico"}


def pascal(name: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in re.split(r"[-_ ]+", name) if part)


def placeholders(args: argparse.Namespace) -> dict[str, str]:
    pkg = args.base_package
    app_id = args.app_id or pkg
    sdk_name = args.sdk_name or pascal(args.app_name) + "Sdk"
    facade = args.sdk_facade or pascal(args.app_name)
    if facade == sdk_name:
        raise SystemExit(f"--sdk-facade ({facade}) must differ from --sdk-name: Swift breaks when a type has its module's name")
    prod = args.base_url_prod.rstrip("/") + "/"
    dev = (args.base_url_dev or args.base_url_prod).rstrip("/") + "/"
    strip = lambda url: re.sub(r"^https?://", "", url)  # noqa: E731
    return {
        "__PKG_PATH__": pkg.replace(".", "/"),
        "__PKG__": pkg,
        "__APP_ID_PATH__": app_id.replace(".", "/"),
        "__APP_ID__": app_id,
        "__APP_NAME__": args.app_name,
        "__PROJECT_NAME__": args.project_name or args.app_name,
        "__SDK_ARTIFACT__": re.sub(r"(?<!^)(?=[A-Z])", "-", sdk_name).lower(),
        "__SDK_NAME__": sdk_name,
        "__SDK_FACADE__": facade,
        "__SDK_MODULE__": args.sdk_module,
        "__IOS_MIN__": args.ios_min,
        "__BASE_URL_PROD_HOST__": strip(prod),
        "__BASE_URL_DEV_HOST__": strip(dev),
        "__BASE_URL_PROD__": "https://" + strip(prod),
        "__BASE_URL_DEV__": "https://" + strip(dev),
        "__GITHUB_OWNER__": args.github_owner,
    }


def substitute(text: str, values: dict[str, str]) -> str:
    # Longest keys first so __PKG_PATH__ wins over __PKG__.
    for key in sorted(values, key=len, reverse=True):
        text = text.replace(key, values[key])
    return text


def sdk_target(rel: Path, sdk_module: str) -> Path | None:
    """Map base/core/<module>/src/<sourceSet>/... into the SDK module."""
    parts = rel.parts
    if len(parts) >= 4 and parts[0] == "core" and parts[2] == "src":
        module, source_set = parts[1], parts[3]
        if module == "testing" and source_set == "commonMain":
            source_set = "commonTest"
        return Path(sdk_module, "src", source_set, *parts[4:])
    return rel


def render(args: argparse.Namespace, staging: Path) -> None:
    values = placeholders(args)
    roots = [TEMPLATES / "base", TEMPLATES / args.mode]
    for root in roots:
        for src in sorted(p for p in root.rglob("*") if p.is_file()):
            if src.name == ".DS_Store":
                continue
            rel = src.relative_to(root)
            if args.mode == "sdk" and root.name == "base":
                rel = sdk_target(rel, args.sdk_module)
            out = staging / substitute(str(rel), values)
            out.parent.mkdir(parents=True, exist_ok=True)
            if src.suffix in BINARY_SUFFIXES:
                shutil.copy2(src, out)
                continue
            text = substitute(src.read_text(encoding="utf-8"), values)
            if args.mode == "sdk":
                text = SDK_INTERNAL.sub("internal", text)
            text = SDK_INTERNAL_MARK.sub("", text)
            out.write_text(text, encoding="utf-8")
            if src.stat().st_mode & 0o111:
                out.chmod(0o755)


def _same_text(a: Path, b: Path) -> bool:
    """Equal except for trailing whitespace / final newline (the wizard omits final newlines)."""
    try:
        norm = lambda p: "\n".join(line.rstrip() for line in p.read_text(encoding="utf-8").strip().splitlines())  # noqa: E731
        return norm(a) == norm(b)
    except UnicodeDecodeError:
        return False


def install(staging: Path, dest: Path, force: bool, dry_run: bool) -> dict[str, list[str]]:
    report: dict[str, list[str]] = {"created": [], "conflicts": [], "identical": [], "overwritten": [], "appended": []}
    for staged in sorted(p for p in staging.rglob("*") if p.is_file()):
        rel = staged.relative_to(staging)
        target = dest / rel
        if staged.suffix == ".append":
            target = target.with_suffix("")
            existing = target.read_text(encoding="utf-8").splitlines() if target.exists() else []
            keys = {line.split("=", 1)[0].strip() for line in existing if "=" in line}
            missing = [
                line for line in staged.read_text(encoding="utf-8").splitlines()
                if line.strip() and line not in existing and ("=" not in line or line.split("=", 1)[0].strip() not in keys)
            ]
            if missing and not dry_run:
                # A blank line separates the appended block from what was there before.
                with target.open("a", encoding="utf-8") as fh:
                    lead = ("\n" if existing and existing[-1].strip() else "") + ("\n" if existing else "")
                    fh.write(lead + "\n".join(missing) + "\n")
            if missing:
                report["appended"].append(f"{target.relative_to(dest)}: {', '.join(missing)}")
            continue
        if target.exists():
            if target.read_bytes() == staged.read_bytes() or _same_text(target, staged):
                report["identical"].append(str(rel))
            elif force:
                if not dry_run:
                    shutil.copy2(target, target.with_name(target.name + ".bak"))
                    shutil.copy2(staged, target)
                report["overwritten"].append(str(rel))
            else:
                report["conflicts"].append(str(rel))
            continue
        if not dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(staged, target)
        report["created"].append(str(rel))
    return report


def ios_deployment_target(dest: Path, ios_min: str, dry_run: bool, report: dict[str, list[str]]) -> None:
    """App mode: make the Xcode project's minimum iOS version match --ios-min (every configuration)."""
    pbx = dest / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj"
    if not pbx.exists():
        report.setdefault("manual", []).append(f"iosApp.xcodeproj not found: set IPHONEOS_DEPLOYMENT_TARGET = {ios_min} by hand")
        return
    text = pbx.read_text(encoding="utf-8")
    new = re.sub(r"IPHONEOS_DEPLOYMENT_TARGET = [0-9.]+;", f"IPHONEOS_DEPLOYMENT_TARGET = {ios_min};", text)
    if new != text:
        if not dry_run:
            pbx.write_text(new, encoding="utf-8")
        report["appended"].append(f"iosApp/iosApp.xcodeproj/project.pbxproj: IPHONEOS_DEPLOYMENT_TARGET = {ios_min}")


def ios_info_plist(dest: Path, dry_run: bool, report: dict[str, list[str]]) -> None:
    """App mode: expose the BASE_URL build setting to Swift through Info.plist."""
    plist = dest / "iosApp" / "iosApp" / "Info.plist"
    if not plist.exists():
        report.setdefault("manual", []).append("iosApp/iosApp/Info.plist not found: add BASE_URL = $(BASE_URL) to the app's Info.plist")
        return
    with plist.open("rb") as fh:
        data = plistlib.load(fh)
    if data.get("BASE_URL") == "$(BASE_URL)":
        return
    data["BASE_URL"] = "$(BASE_URL)"
    if not dry_run:
        with plist.open("wb") as fh:
            plistlib.dump(data, fh)
    report["appended"].append("iosApp/iosApp/Info.plist: BASE_URL = $(BASE_URL)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", choices=["app", "sdk"], required=True)
    parser.add_argument("--dest", required=True, type=Path)
    parser.add_argument("--base-package", required=True, help="e.g. com.acme.gym")
    parser.add_argument("--app-name", required=True, help="Display / project name, e.g. KratosGym")
    parser.add_argument("--project-name", help="rootProject.name (defaults to --app-name)")
    parser.add_argument("--app-id", help="Android applicationId (app mode, defaults to --base-package)")
    parser.add_argument("--sdk-name", help="SDK facade + XCFramework name (sdk mode, default <AppName>Sdk)")
    parser.add_argument("--sdk-facade", help="Public Kotlin entry object (sdk mode, default <AppName>; must differ from --sdk-name)")
    parser.add_argument("--sdk-module", default="shared", help="SDK Gradle module dir (sdk mode)")
    parser.add_argument("--ios-min", default="16.0")
    parser.add_argument("--base-url-prod", default="api.example.com/")
    parser.add_argument("--base-url-dev")
    parser.add_argument("--github-owner", default="OWNER")
    parser.add_argument("--staging", type=Path, help="Keep the rendered tree here (default: temp dir)")
    parser.add_argument("--force", action="store_true", help="Overwrite conflicting files (writes .bak)")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", args.base_package):
        parser.error("--base-package must look like com.company.app (lowercase, dot separated)")
    dest = args.dest.expanduser().resolve()
    if not dest.is_dir():
        parser.error(f"--dest {dest} is not a directory")

    staging = args.staging or Path(tempfile.mkdtemp(prefix="tezca-kmp-"))
    if staging.exists() and any(staging.iterdir()):
        shutil.rmtree(staging)
    staging.mkdir(parents=True, exist_ok=True)

    render(args, staging)
    report = install(staging, dest, args.force, args.dry_run)
    if args.mode == "app":
        ios_info_plist(dest, args.dry_run, report)
        ios_deployment_target(dest, args.ios_min, args.dry_run, report)
    print(json.dumps({"staging": str(staging), "dry_run": args.dry_run, **report}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Create a feature (Python 3 standard library only) and register it everywhere it must be known.

    python3 -I new_feature.py --project . --name user-profile [--dry-run]

App mode   -> module :feature:<name> (kmp.feature convention plugin) + registration in
              settings.gradle.kts, :shared dependencies, Koin featureModules and AppNavHost.
SDK mode   -> package <base>.<pkg> inside the SDK module + registration in SdkKoin featureModules
              and a `<feature>` accessor on the SDK facade object.

Registrations are inserted right below the `// tezca-kmp:<anchor>` marker comments that start-kmp
generated. A missing marker is reported (the agent then edits that file by hand); nothing is
overwritten. Prints a JSON report.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEMPLATES = HERE.parent / "assets" / "templates"


def words(name: str) -> list[str]:
    return [w for w in re.split(r"[-_ ]+", name.strip()) if w]


def names(raw: str) -> dict[str, str]:
    parts = [w.lower() for w in words(raw)]
    if not parts or not re.fullmatch(r"[a-z][a-z0-9]*", parts[0]) or not all(re.fullmatch(r"[a-z0-9]+", p) for p in parts):
        raise SystemExit(f"Invalid feature name '{raw}': use letters/digits separated by dashes, e.g. user-profile")
    pascal = "".join(p.capitalize() for p in parts)
    return {
        "__FEATURE__": "-".join(parts),                      # module dir / Gradle name: user-profile
        "__FPKG__": "".join(parts),                          # package segment: userprofile
        "__Feature__": pascal,                               # class prefix: UserProfile
        "__feature__": pascal[:1].lower() + pascal[1:],      # val / fun prefix: userProfile
        "__feature_snake__": "_".join(parts),                # resource keys: user_profile
        "__Feature_Title__": " ".join(p.capitalize() for p in parts),
    }


def load_config(project: Path) -> dict:
    cfg_file = project / ".tezca-kmp.json"
    if cfg_file.exists():
        return json.loads(cfg_file.read_text(encoding="utf-8"))
    props = (project / "gradle.properties").read_text(encoding="utf-8") if (project / "gradle.properties").exists() else ""
    match = re.search(r"^kmp\.basePackage=(.+)$", props, re.M)
    if not match:
        raise SystemExit("No .tezca-kmp.json and no kmp.basePackage in gradle.properties: run start-kmp first.")
    return {"mode": "app", "basePackage": match.group(1).strip(), "rootModule": ":shared", "features": []}


def substitute(text: str, values: dict[str, str]) -> str:
    for key in sorted(values, key=len, reverse=True):
        text = text.replace(key, values[key])
    return text


def insert_after_marker(path: Path, anchor: str, lines: list[str], dry_run: bool, report: dict) -> None:
    """Insert `lines` after the line holding `// tezca-kmp:<anchor>`, keeping its indentation."""
    rel = str(path)
    if not path.exists():
        report["manual"].append(f"{rel}: file missing (expected marker tezca-kmp:{anchor})")
        return
    text = path.read_text(encoding="utf-8")
    todo = [line for line in lines if line.strip() not in {l.strip() for l in text.splitlines()}]
    if not todo:
        report["already_registered"].append(f"{rel} [{anchor}]")
        return
    out, done = [], False
    for line in text.splitlines(keepends=True):
        out.append(line)
        if not done and f"tezca-kmp:{anchor}" in line:
            indent = re.match(r"\s*", line).group(0)
            out.extend(f"{indent}{new}\n" for new in todo)
            done = True
    if not done:
        report["manual"].append(f"{rel}: marker // tezca-kmp:{anchor} not found; add {todo} by hand")
        return
    if not dry_run:
        path.write_text("".join(out), encoding="utf-8")
    report["edited"].append(f"{rel} [{anchor}] += {todo}")


def add_import(path: Path, import_line: str, dry_run: bool, report: dict) -> None:
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    if import_line in text:
        return
    lines = text.splitlines(keepends=True)
    imports = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if imports:
        # Keep the import block sorted: insert before the first import that sorts after ours.
        pos = next((i for i in imports if lines[i].strip() > import_line), imports[-1] + 1)
    else:
        pos = next(i + 1 for i, l in enumerate(lines) if l.startswith("package "))
    lines.insert(pos, import_line + "\n")
    if not dry_run:
        path.write_text("".join(lines), encoding="utf-8")
    report["edited"].append(f"{path} += {import_line}")


def kotlin_dir(project: Path, module: str, source_set: str, package: str) -> Path:
    return project / module.strip(":").replace(":", "/") / "src" / source_set / "kotlin" / package.replace(".", "/")


def accessor(gradle_name: str) -> str:
    """Typesafe project accessor segment: user-profile -> userProfile."""
    head, *rest = gradle_name.split("-")
    return head + "".join(p.capitalize() for p in rest)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--project", type=Path, default=Path("."))
    parser.add_argument("--name", required=True, help="Feature name, e.g. onboarding or user-profile")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    project = args.project.expanduser().resolve()
    cfg = load_config(project)
    mode = cfg.get("mode", "app")
    pkg = cfg["basePackage"]
    values = names(args.name)
    sdk_module = cfg.get("rootModule", ":shared").strip(":")
    values.update({
        "__PKG_PATH__": pkg.replace(".", "/"),
        "__PKG__": pkg,
        "__SDK_MODULE__": sdk_module,
        "__SDK_NAME__": cfg.get("sdkName") or "Sdk",
        "__SDK_FACADE__": cfg.get("sdkFacade") or cfg.get("sdkName") or "Sdk",
    })
    report: dict = {"mode": mode, "feature": values["__FEATURE__"], "created": [], "skipped_existing": [],
                    "edited": [], "already_registered": [], "manual": [], "dry_run": args.dry_run}

    # 1. Files from templates
    root = TEMPLATES / mode
    for src in sorted(p for p in root.rglob("*") if p.is_file() and p.name != ".DS_Store"):
        rel = Path(substitute(str(src.relative_to(root)), values))
        target = project / rel
        if target.exists():
            report["skipped_existing"].append(str(rel))
            continue
        if not args.dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(substitute(src.read_text(encoding="utf-8"), values), encoding="utf-8")
        report["created"].append(str(rel))

    feature, fpkg, camel = values["__FEATURE__"], values["__FPKG__"], values["__feature__"]
    root_module = cfg.get("rootModule", ":shared")

    # 2. Registrations
    if mode == "app":
        insert_after_marker(project / "settings.gradle.kts", "features", [f'include(":feature:{feature}")'], args.dry_run, report)
        insert_after_marker(project / root_module.strip(":") / "build.gradle.kts", "feature-dependencies",
                            [f"implementation(projects.feature.{accessor(feature)})"], args.dry_run, report)
        koin_init = kotlin_dir(project, root_module, "commonMain", pkg) / "di" / "KoinInit.kt"
        add_import(koin_init, f"import {pkg}.feature.{fpkg}.di.{camel}Module", args.dry_run, report)
        insert_after_marker(koin_init, "feature-modules", [f"{camel}Module,"], args.dry_run, report)
        nav_host = kotlin_dir(project, root_module, "commonMain", pkg) / "navigation" / "AppNavHost.kt"
        add_import(nav_host, f"import {pkg}.feature.{fpkg}.navigation.{camel}Graph", args.dry_run, report)
        insert_after_marker(nav_host, "feature-graphs", [f"{camel}Graph(navController = navController)"], args.dry_run, report)
    else:
        sdk_koin = kotlin_dir(project, root_module, "commonMain", pkg) / "di" / "SdkKoin.kt"
        add_import(sdk_koin, f"import {pkg}.{fpkg}.di.{camel}Module", args.dry_run, report)
        insert_after_marker(sdk_koin, "feature-modules", [f"{camel}Module,"], args.dry_run, report)
        facade_name = cfg.get("sdkFacade") or cfg.get("sdkName")
        facade = kotlin_dir(project, root_module, "commonMain", pkg) / f"{facade_name}.kt" if facade_name else None
        if facade is None or not facade.exists():
            report["manual"].append("SDK facade object not found: add the feature accessor by hand")
        else:
            add_import(facade, f"import {pkg}.{fpkg}.{values['__Feature__']}Feature", args.dry_run, report)
            insert_after_marker(facade, "sdk-features",
                                [f"public val {camel}: {values['__Feature__']}Feature get() = {values['__Feature__']}Feature(SdkKoin.koin)"],
                                args.dry_run, report)

    # 3. Project state
    cfg_file = project / ".tezca-kmp.json"
    if cfg_file.exists() and not args.dry_run:
        features = cfg.setdefault("features", [])
        if feature not in features:
            features.append(feature)
            cfg_file.write_text(json.dumps(cfg, indent=2) + "\n", encoding="utf-8")
            report["edited"].append(f".tezca-kmp.json features += {feature}")

    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())

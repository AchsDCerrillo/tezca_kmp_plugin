#!/usr/bin/env bash
# Publishes the XCFramework for Swift Package Manager:
#   1. zips the release XCFramework and uploads it to the GitHub release <tag> of this repo
#   2. renders swiftpackage/Package.swift.template with the asset URL + checksum
#   3. commits Package.swift to the SPM repository ($SPM_REPO) and tags it <version>
# Usage: scripts/publish-spm.sh <version> <tag> [--prerelease]
# Env: GH_TOKEN (this repo), SPM_REPO (owner/name), SPM_REPO_TOKEN (push access to SPM_REPO)
set -euo pipefail

VERSION="$1"; TAG="$2"; PRERELEASE="${3:-}"
NAME="__SDK_NAME__"
XCF_DIR="__SDK_MODULE__/build/XCFrameworks/release/$NAME.xcframework"
ZIP="build/$NAME-$VERSION.xcframework.zip"

[ -d "$XCF_DIR" ] || { echo "Missing $XCF_DIR; run ./gradlew :__SDK_MODULE__:assemble${NAME}ReleaseXCFramework first" >&2; exit 1; }
mkdir -p build
(cd "$(dirname "$XCF_DIR")" && zip -qry "$OLDPWD/$ZIP" "$NAME.xcframework")
CHECKSUM="$(swift package compute-checksum "$ZIP")"

if ! gh release view "$TAG" >/dev/null 2>&1; then
  gh release create "$TAG" --title "$TAG" --notes "Automated build $VERSION" ${PRERELEASE:+--prerelease} --target "$(git rev-parse HEAD)"
fi
gh release upload "$TAG" "$ZIP" --clobber
URL="https://github.com/${GITHUB_REPOSITORY}/releases/download/$TAG/$(basename "$ZIP")"

WORK="$(mktemp -d)"
git clone --depth 1 "https://x-access-token:${SPM_REPO_TOKEN}@github.com/${SPM_REPO}.git" "$WORK/spm"
sed -e "s#{{XCFRAMEWORK_URL}}#$URL#" -e "s#{{XCFRAMEWORK_CHECKSUM}}#$CHECKSUM#" swiftpackage/Package.swift.template > "$WORK/spm/Package.swift"
cd "$WORK/spm"
git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"
git add Package.swift
git commit -m "$NAME $VERSION" || echo "Package.swift unchanged"
git tag -f "$VERSION"
git push origin HEAD
git push -f origin "refs/tags/$VERSION"
echo "Published $NAME $VERSION -> $SPM_REPO ($URL, checksum $CHECKSUM)"

# Coloring repository workflow cleanup

Target: `topgun1111/coloring` on `main`, inspected 2026-11-21.

## Keep
- `.github/workflows/android-apk.yml`: Nightmare debug APK CI
- `.github/workflows/dit-engine.yml`: native DiT engine build
- `.github/workflows/publish-models.yml`: model publishing

## Delete
- `.github/workflows/ci.yml`: duplicate legacy APK workflow
- `.github/workflows/guide.yml`: unwanted documentation workflow
- `.github/workflows/release.yml`: legacy release workflow expects missing `version.properties` and mismatched signing/build process

**ZIP extraction cannot delete files.** Use GitHub's file browser to delete these three paths individually, or extract this ZIP into a local clone and run `bash cleanup-workflows.sh` from the repository root, then `git commit -m "ci: remove obsolete workflows" && git push`.

The ZIP includes an unchanged snapshot of the currently active `android-apk.yml` for reference, not a newly verified build configuration. No native inference binary is added; AI generation still requires the proper backend. No workflow has been executed or modified on GitHub by this ZIP.

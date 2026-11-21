Extract into repository root. Replaces app/build.gradle.kts and adds .github/workflows/android-apk.yml.
Push to GitHub -> Actions -> Android APK (CI) -> download artifact.
CI versions are unique per run without committing a version change. Official release version remains 1.6.321 / code 628.
Debug APK uses Android debug signing: it generally cannot update an existing release-signed installation. Uninstalling the old app may delete app data and downloaded models; back up first.
The repository does not include its external native inference backend; CI cannot provide functional AI rendering until that binary is staged. Foxlet AAR is built from source by the included script. This workflow has NOT been executed here.

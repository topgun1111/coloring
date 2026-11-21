from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
path = root / '.github/workflows/android-apk.yml'
if not path.is_file():
    sys.exit(f'Workflow not found: {path}')
s = path.read_text()
if 'Run unit tests with full Robolectric diagnostics' in s:
    sys.exit('Already patched; no changes made.')
old = '''      - name: Run unit tests
        run: ./gradlew :app:testDebugUnitTest --stacktrace -PciVersionCode="$CI_VERSION_CODE" -PciVersionName="$CI_VERSION_NAME"
'''
new = '''      - name: Run unit tests with full Robolectric diagnostics
        shell: bash
        run: |
          set -o pipefail
          mkdir -p ci-diagnostics
          cat > ci-diagnostics/test-logging.init.gradle <<'EOF'
          allprojects {
              tasks.withType(Test).configureEach {
                  testLogging {
                      events "failed", "skipped"
                      exceptionFormat "full"
                      showExceptions true
                      showCauses true
                      showStackTraces true
                  }
                  systemProperty "robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2"
                  systemProperty "robolectric.dependency.repo.id", "central"
              }
          }
          EOF
          ./gradlew :app:testDebugUnitTest --stacktrace --info \\
            --init-script ci-diagnostics/test-logging.init.gradle \\
            -PciVersionCode="$CI_VERSION_CODE" -PciVersionName="$CI_VERSION_NAME" \\
            2>&1 | tee ci-diagnostics/unit-tests.log
      - name: Diagnose Robolectric artifact resolution
        if: failure()
        shell: bash
        run: |
          mkdir -p ci-diagnostics
          {
            echo '=== Robolectric / android-all errors ==='
            grep -Ein -C 8 'MavenArtifactFetcher|android-all|robolectric|FileNotFoundException|HTTP 404|Could not (get|find|resolve)' ci-diagnostics/unit-tests.log | tail -n 500 || true
            echo '=== Maven Central reachability (not proof of artifact availability) ==='
            curl --location --silent --show-error --output /dev/null --write-out 'Maven Central HTTP %{http_code}\\n' --max-time 20 https://repo.maven.apache.org/maven2/org/robolectric/android-all/maven-metadata.xml || true
          } | tee ci-diagnostics/robolectric-summary.txt
'''
if s.count(old) != 1:
    sys.exit('Expected original test step not found exactly once; repository left unchanged.')
needle = '''            app/build/test-results/
'''
if s.count(needle) != 1:
    sys.exit('Expected diagnostics upload step not found exactly once; repository left unchanged.')
s = s.replace(old, new).replace(needle, needle + '            ci-diagnostics/\n')
path.write_text(s)
print(f'Patched: {path}')

from pathlib import Path
import sys
p=Path(sys.argv[1] if len(sys.argv)>1 else '.')/'.github/workflows/android-apk.yml'
if not p.exists(): sys.exit('Workflow not found')
s=p.read_text()
needle='      - name: Compile Kotlin and Android resources\n'
if 'Fetch verified QuickJS sources' in s: sys.exit('Already patched')
if s.count(needle)!=1: sys.exit('Unexpected workflow structure')
insert='''      - name: Fetch verified QuickJS sources
        shell: bash
        run: |
          set -euo pipefail
          tag=v0.16.2
          expected=97c80625b26775a4c7ca618c004d4ea24cf99cbf867e4eba78bd927a8b23d106
          mkdir -p ci-diagnostics app/src/main/cpp/third_party/quickjs
          archive="$(mktemp --suffix=.tar.gz)"
          trap 'rm -f "$archive"' EXIT
          curl --fail --location --retry 3 --silent --show-error \\
            "https://github.com/quickjs-ng/quickjs/archive/refs/tags/${tag}.tar.gz" \\
            --output "$archive"
          echo "$expected  $archive" | sha256sum --check --status || {
            echo '::error::QuickJS archive SHA256 mismatch'; exit 1;
          }
          tar -xzf "$archive" -C app/src/main/cpp/third_party/quickjs --strip-components=1
          for source in quickjs.c quickjs.h libregexp.c libunicode.c dtoa.c; do
            test -s "app/src/main/cpp/third_party/quickjs/$source" || {
              echo "::error::QuickJS source missing: $source"; exit 1;
            }
          done
          echo "$tag" > app/src/main/cpp/third_party/quickjs/VERSION
      - name: Verify QAIRT QNN headers
        shell: bash
        run: |
          set -euo pipefail
          header=app/src/main/cpp/include/QNN/QnnBackend.h
          if [[ -s "$header" ]]; then
            echo 'QNN headers available.'
          else
            echo '::error::QNN headers are absent. The project requires QAIRT SDK 2.49.0.260730. Supply its include/QNN directory through an authorized SDK installation before native compilation.'
            exit 1
          fi
'''
s=s.replace(needle,insert+needle,1)
p.write_text(s)
print('Updated',p)

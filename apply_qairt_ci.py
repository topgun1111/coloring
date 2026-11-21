from pathlib import Path
import sys
p=Path(sys.argv[1] if len(sys.argv)>1 else '.')/'.github/workflows/android-apk.yml'
if not p.is_file(): sys.exit('Workflow not found')
s=p.read_text()
marker='      - name: Compile Kotlin and Android resources\n'
if 'Fetch QAIRT QNN headers' in s: sys.exit('Already installed')
if s.count(marker)!=1: sys.exit('Expected workflow insertion point not found')
step='''      - name: Fetch QAIRT QNN headers (pinned 2.49.0.260730)
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p ci-diagnostics
          python3 - <<'PYCODE'
          import pathlib, urllib.request, zipfile, tempfile, shutil
          version = "2.49.0.260730"
          url = ("https://softwarecenter.qualcomm.com/api/download/software/sdks/"
                 f"Qualcomm_AI_Runtime_Community/All/{version}/v{version}.zip")
          dest = pathlib.Path("app/src/main/cpp/include/QNN")
          dest.mkdir(parents=True, exist_ok=True)
          archive = pathlib.Path(tempfile.gettempdir()) / f"qairt-{version}.zip"
          try:
              request = urllib.request.Request(url, headers={"User-Agent": "GitHub-Actions-QAIRT-Build/1.0"})
              with urllib.request.urlopen(request, timeout=180) as response, archive.open("wb") as output:
                  shutil.copyfileobj(response, output, length=1024 * 1024)
              print(f"QAIRT download: {archive.stat().st_size} bytes", flush=True)
              with zipfile.ZipFile(archive) as z:
                  prefix = f"qairt/{version}/include/QNN/"
                  entries = [i for i in z.infolist() if i.filename.startswith(prefix) and not i.is_dir()]
                  if not entries: raise RuntimeError(f"No headers under {prefix}")
                  for info in entries:
                      relative = pathlib.PurePosixPath(info.filename[len(prefix):])
                      if not relative.parts or '..' in relative.parts or relative.is_absolute():
                          raise RuntimeError('Unsafe ZIP path')
                      target = dest.joinpath(*relative.parts)
                      target.parent.mkdir(parents=True, exist_ok=True)
                      with z.open(info) as source, target.open('wb') as output:
                          shutil.copyfileobj(source, output)
              for name in ('QnnBackend.h', 'QnnCommon.h', 'QnnSdkBuildId.h'):
                  if not (dest / name).is_file(): raise RuntimeError(f'Missing {name}')
              print(f"QNN headers extracted: {len(entries)} files", flush=True)
          except Exception as exc:
              print(f"::error::QAIRT SDK download or extraction failed: {exc}", flush=True)
              raise
          finally:
              archive.unlink(missing_ok=True)
          PYCODE
'''
p.write_text(s.replace(marker,step+marker,1))
print('Updated',p)

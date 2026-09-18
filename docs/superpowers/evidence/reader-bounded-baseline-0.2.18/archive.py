"""Archive only a completed isolated control; never copies the fixture/profile or runtime."""
import hashlib
import json
import pathlib
import shutil

source = pathlib.Path(__file__).resolve().parent
repository = source.parents[1]
target = repository / "docs/superpowers/evidence/reader-bounded-baseline-0.2.18"
result = json.loads((source / "full/result.json").read_text(encoding="utf-8-sig"))
assert result["accepted"] and result["exitCode"] == 0 and result["samplerExitCode"] == 0
assert result["soak"]["elapsedSeconds"] >= 1800
for name in ("stderr.log", "sampler.stderr"):
    assert (source / "full" / name).stat().st_size == 0
configuration = json.loads((source / "configuration.json").read_text(encoding="utf-8"))
app = pathlib.Path(configuration["executable"]).parent / "app"
for filename, key in (("mihondesk.cfg", "configurationSha256"), ("desktop-app-*.jar", "appJarSha256"), ("reader-core-*.jar", "coreJarSha256")):
    matches = list(app.glob(filename))
    assert len(matches) == 1
    assert hashlib.sha256(matches[0].read_bytes()).hexdigest() == configuration[key]
target.mkdir(parents=True, exist_ok=True)
(target / ".gitattributes").write_text("* -text -whitespace\n", encoding="utf-8")
for name in ("configuration.json", "archive.py"):
    shutil.copyfile(source / name, target / name)
shutil.copyfile(app / "mihondesk.cfg", target / "mihondesk.cfg")
for item in (source / "full").iterdir():
    if item.is_file():
        shutil.copyfile(item, target / item.name)
shutil.copyfile(repository / "build/reader-soak-0218/full/analysis.json", target / "baseline-analysis.json")
for name in ("summarize-reader-soak.ps1", "plot-reader-soak.py"):
    shutil.copyfile(repository / "scripts" / name, target / name)
manifest = target / "SHA256SUMS.txt"
manifest.write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(target).as_posix()}\n" for p in sorted(target.rglob("*")) if p.is_file() and p != manifest), encoding="utf-8")
print(f"Archived {len(manifest.read_text().splitlines())} hashes to {target}")

"""Verify packaged portable upgrades against isolated synthetic profiles; stdlib only."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import zipfile


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def database_state(path):
    connection = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)
    try:
        assert connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
        tables = connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
        rows = {table: sorted(connection.execute('SELECT * FROM "' + table.replace('"', '""') + '"').fetchall(), key=repr) for (table,) in tables}
        return connection.execute("PRAGMA user_version").fetchone()[0], rows
    finally:
        connection.close()


def data_hashes(directory):
    return {str(path.relative_to(directory)): sha256(path) for path in directory.rglob("*") if path.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--zip", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    image = arguments.image.resolve(strict=True)
    archive = arguments.zip.resolve(strict=True)
    output = arguments.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    shell = Path(os.environ["WINDIR"]) / "System32/WindowsPowerShell/v1.0/powershell.exe"
    expected_hash = sha256(archive)
    updater = output / "mihondesk-updater.ps1"
    with zipfile.ZipFile(archive) as package:
        updater.write_bytes(package.read("mihondesk/mihondesk-updater.ps1"))
    assert sha256(updater) == sha256(Path(__file__).with_name("mihondesk-updater.ps1"))
    events = []

    def run(label, command, expected=0, timeout=180):
        started = time.monotonic()
        result = subprocess.run(command, capture_output=True, timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
        (output / f"{label}.stdout.txt").write_bytes(result.stdout)
        (output / f"{label}.stderr.txt").write_bytes(result.stderr)
        events.append(dict(label=label, exit_code=result.returncode, seconds=round(time.monotonic() - started, 3)))
        assert result.returncode == expected, (label, result.returncode, result.stdout.decode("utf-8", errors="replace"), result.stderr.decode("utf-8", errors="replace"))
        print(label, result.returncode, flush=True)
        return result

    for scenario in ("success", "rollback"):
        case = output / scenario
        target = case / "portable 中文's app"
        shutil.copytree(image, target)
        (target / ".portable").write_text("", encoding="utf-8")
        (target / "original-installation.txt").write_text("retain original program", encoding="utf-8")
        executable = target / "mihondesk.exe"
        profile = target / "data"
        run(f"{scenario}-seed", [str(executable), f"--data-dir={profile}", "--list-library-json"])
        database = profile / "database/library.db"
        connection = sqlite3.connect(database)
        try:
            connection.executescript("""
                DROP TABLE local_chapter_storage;
                DROP TABLE library_metadata;
                PRAGMA user_version=1;
                INSERT INTO manga(source_id,url,title) VALUES(7,'/upgrade','Preserved manga');
                INSERT INTO chapter(manga_id,url,name,read,last_page_read,bookmark)
                    VALUES(1,'/upgrade/1','Preserved chapter',1,3,1);
                INSERT INTO category(name,sort_order) VALUES('Preserved category',1);
                INSERT INTO manga_category(manga_id,category_id) VALUES(1,1);
                INSERT INTO history(chapter_id,last_read,read_duration) VALUES(1,123456789,42);
            """)
            if scenario == "rollback":
                connection.execute("CREATE TABLE library_metadata(name TEXT PRIMARY KEY, invalid_column TEXT)")
            connection.commit()
        finally:
            connection.close()
        (profile / "synthetic-private-note.txt").write_text("synthetic session and configuration sentinel", encoding="utf-8")
        # Retain the credential-profile identity created by the seed launch. Replacing
        # the entire preferences file would intentionally trigger identity repair.
        with (profile / "preferences.properties").open("a", encoding="ascii") as preferences:
            preferences.write("\nlanguage=zh-CN\n")
        (profile / "media/local/keep").mkdir(parents=True)
        (profile / "media/local/keep/notes.txt").write_text("synthetic media sentinel", encoding="utf-8")
        before_state = database_state(database)
        before_files = data_hashes(profile)
        result = run(f"{scenario}-update", [str(shell), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", str(updater), "-ZipPath", str(archive), "-TargetDir", str(target),
                    "-ExpectedSha256", expected_hash, "-NoRestart"], expected=0 if scenario == "success" else 1)
        assert not list(case.glob(".mihon-update-*.json")), "Recovery journal remains after completed transaction"
        assert not (target / ".mihon-update-in-progress").exists()
        if scenario == "success":
            assert not (target / "original-installation.txt").exists()
            backups = list(case.glob(".mihon-rollback-*"))
            assert len(backups) == 1
            assert (backups[0] / "original-installation.txt").read_text() == "retain original program"
            assert data_hashes(backups[0] / "data") == before_files, "Original profile copy changed"
            assert database_state(database)[0] == 3
            points = list((profile / "database/migration-backups").glob("*.db"))
            assert len(points) == 1 and database_state(points[0]) == before_state
            for relative, digest in before_files.items():
                if relative.startswith("database" + os.sep) or relative == ".mihon-profile.lock":
                    continue
                assert sha256(profile / relative) == digest, ("Profile file changed", relative)
            run("success-reopen", [str(executable), f"--data-dir={profile}", "--list-library-json"])
        else:
            assert b"failed validation" in result.stderr
            assert (target / "original-installation.txt").read_text() == "retain original program"
            assert data_hashes(profile) == before_files, "Rollback did not restore exact original profile bytes"
            assert database_state(database) == before_state
            failed = list(case.glob(".mihon-failed-*"))
            assert len(failed) == 1 and (failed[0] / "data/synthetic-private-note.txt").exists()
        assert not list(case.glob(".mihon-stage-*")), "Completed operation leaked staging"

    app_jar = next((image / "app").glob("desktop-app-*.jar"))
    with zipfile.ZipFile(app_jar) as package:
        build_info = package.read("mihon-build-info.properties").decode()
    report = dict(status="PASS", fixture="synthetic schema v1 profiles", image=str(image),
                  archive=str(archive), archive_sha256=expected_hash, updater_sha256=sha256(updater),
                  app_jar_sha256=sha256(app_jar), build_info=build_info, events=events)
    (output / "result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print("PASS", output / "result.json", flush=True)


if __name__ == "__main__":
    main()

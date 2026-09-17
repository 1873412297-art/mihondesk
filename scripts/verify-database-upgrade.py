"""Exercise the actual packaged EXE against isolated synthetic old-schema profiles.

Usage: python -X utf8 scripts/verify-database-upgrade.py --exe <mihondesk.exe>
       --output <new evidence directory>
Never opens the installed/default profile or overwrites an existing evidence folder.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_database(path):
    with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as connection:
        assert connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
        assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        tables = connection.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).fetchall()
        content = {}
        for (table,) in tables:
            name = table.replace('"', '""')
            content[table] = sorted(connection.execute(f'SELECT * FROM "{name}"').fetchall(), key=repr)
        return version, content


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--exe", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    executable = arguments.exe.resolve(strict=True)
    output = arguments.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    events = []

    def launch(label, profile, expected=0, category=None):
        started = time.monotonic()
        process = subprocess.run(
            [str(executable), "--portable", f"--data-dir={profile}", "--list-library-json"],
            capture_output=True, timeout=60,
        )
        stdout = process.stdout.decode("utf-8", errors="replace")
        stderr = process.stderr.decode("utf-8", errors="replace")
        (output / f"{label}.stdout.txt").write_text(stdout, encoding="utf-8")
        (output / f"{label}.stderr.txt").write_text(stderr, encoding="utf-8")
        event = dict(label=label, returncode=process.returncode, seconds=round(time.monotonic() - started, 3))
        events.append(event)
        assert process.returncode == expected, (event, stdout, stderr)
        if category:
            assert json.loads(stdout)["category"] == category, stdout
            assert "private" not in stdout
        print(label, process.returncode, flush=True)
        return stdout

    def database(profile):
        return profile / "database/library.db"

    def snapshots(profile):
        return sorted((profile / "database/migration-backups").glob("*.db"))

    seed = output / "seed-private"
    launch("fresh", seed)
    current_version = read_database(database(seed))[0]
    assert current_version == 3, "Adapt this explicit v1-to-v3 fixture before using another schema"
    assert snapshots(seed) == []
    with sqlite3.connect(database(seed)) as connection:
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
            INSERT INTO local_manga_entry VALUES(1,'C:/downloads/original','',1);
            INSERT INTO local_chapter_asset VALUES(1,'Chapter 1','DIRECTORY',123,1);
        """)
    seed_state = read_database(database(seed))

    def clone(name):
        profile = output / name
        database(profile).parent.mkdir(parents=True)
        shutil.copy2(database(seed), database(profile))
        return profile

    upgraded = clone("reader's private 数据")
    writer = sqlite3.connect(database(upgraded))
    try:
        writer.execute("PRAGMA journal_mode=WAL")
        writer.execute("PRAGMA wal_autocheckpoint=0")
        writer.execute("UPDATE manga SET title='Committed WAL 数据' WHERE id=1")
        writer.commit()
        assert Path(str(database(upgraded)) + "-wal").stat().st_size > 0
        before = read_database(database(upgraded))
        launch("wal-upgrade", upgraded)
        points = snapshots(upgraded)
        assert len(points) == 1
        assert read_database(points[0]) == before
        assert not Path(str(points[0]) + "-wal").exists()
        upgraded_state = read_database(database(upgraded))
        assert upgraded_state[0] == current_version
        assert upgraded_state[1]["local_chapter_storage"] == [(1, "C:/downloads/original")]
        launch("current-reopen", upgraded)
        assert snapshots(upgraded) == points
        assert read_database(database(upgraded)) == upgraded_state
    finally:
        writer.close()

    restored = output / "restored-private"
    database(restored).parent.mkdir(parents=True)
    shutil.copy2(points[0], database(restored))
    launch("snapshot-restored-upgrade", restored)
    assert read_database(database(restored)) == upgraded_state

    blocked = clone("blocked-private")
    blocker = blocked / "database/migration-backups"
    blocker.write_text("occupied", encoding="utf-8")
    launch("snapshot-unavailable", blocked, 1, "DATABASE_SNAPSHOT_FAILED")
    assert read_database(database(blocked)) == seed_state
    assert blocker.read_text(encoding="utf-8") == "occupied"

    failed = clone("migration-failure-private")
    with sqlite3.connect(database(failed)) as connection:
        connection.execute("CREATE TABLE library_metadata(name TEXT PRIMARY KEY, invalid_column TEXT)")
    old_state = read_database(database(failed))
    launch("migration-failed", failed, 1, "DATABASE_MIGRATION_FAILED")
    assert read_database(database(failed)) == old_state
    previous = snapshots(failed)
    assert len(previous) == 1
    assert read_database(previous[0]) == old_state
    previous_hash = digest(previous[0])
    with sqlite3.connect(database(failed)) as connection:
        connection.execute("DROP TABLE library_metadata")
    launch("migration-retry", failed)
    assert read_database(database(failed))[0] == current_version
    assert len(snapshots(failed)) == 2
    assert digest(previous[0]) == previous_hash

    app = executable.parent / "app"
    app_jar = next(app.glob("desktop-app-*.jar"))
    data_jar = next(app.glob("desktop-library-data-*.jar"))
    with zipfile.ZipFile(app_jar) as archive:
        build_info = archive.read("mihon-build-info.properties").decode()
    report = dict(
        status="PASS", fixture="synthetic v1-to-v3", schema_version=current_version,
        exe=str(executable), exe_sha256=digest(executable),
        app_jar=str(app_jar), app_jar_sha256=digest(app_jar),
        data_jar=str(data_jar), data_jar_sha256=digest(data_jar), build_info=build_info,
        events=events, snapshot=str(points[0]), snapshot_sha256=digest(points[0]),
        snapshot_table_count=len(before[1]),
    )
    (output / "result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print("PASS", output / "result.json", flush=True)


if __name__ == "__main__":
    main()

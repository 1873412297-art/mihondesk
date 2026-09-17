"""Plot completed reader-soak evidence without mixing heap and process accounting."""

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8-sig").splitlines() if line.strip()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    result = read_json(args.directory / "result.json")
    if not result.get("accepted"):
        raise SystemExit("The workload must finish successfully before plotting its accepted result")
    reader = read_rows(args.directory / "reader-samples.jsonl")
    memory_paths = list(args.directory.glob("process-memory-*.jsonl"))
    if len(memory_paths) != 1:
        raise SystemExit("Expected one process memory stream")
    memory = read_rows(memory_paths[0])
    if not reader or not memory:
        raise SystemExit("Memory evidence is empty")
    reader_time = [row["elapsedSeconds"] / 60 for row in reader]
    process_time = [row["elapsedSeconds"] / 60 for row in memory]
    mib = 1024 * 1024
    fig, axes = plt.subplots(3, 1, figsize=(11, 8), sharex=True, layout="constrained")
    axes[0].plot(process_time, [row["workingSetBytes"] / mib for row in memory], label="Process tree working set")
    axes[0].plot(process_time, [row["privateBytes"] / mib for row in memory], label="Process tree private bytes", alpha=0.8)
    axes[0].set_ylabel("MiB")
    axes[0].legend(loc="upper right")
    axes[1].plot(reader_time, [row["heapUsedBytes"] / mib for row in reader], label="JVM heap used")
    axes[1].plot(reader_time, [row["heapCommittedBytes"] / mib for row in reader], label="JVM heap committed", alpha=0.6)
    axes[1].set_ylabel("MiB")
    axes[1].legend(loc="upper right")
    axes[2].plot(reader_time, [row["cycles"] for row in reader], color="#258559", label="Completed active workload cycles")
    axes[2].set_ylabel("Cycles")
    axes[2].set_xlabel("Elapsed minutes (each sampler's own launch clock)")
    axes[2].legend(loc="upper left")
    for ax in axes:
        ax.grid(alpha=0.2)
        ax.set_ylim(bottom=0)
    fig.suptitle("Packaged reader soak — headless decoding and session activity", fontsize=14)
    fig.text(0.5, -0.02, "No Compose frame measurement. Heap is sampled between cycles; process memory includes native allocations.",
             ha="center", fontsize=9)
    fig.savefig(args.directory / "memory-curve.png", dpi=160, bbox_inches="tight")
    fig.savefig(args.directory / "memory-curve.svg", bbox_inches="tight")
    plt.close(fig)


if __name__ == "__main__":
    main()

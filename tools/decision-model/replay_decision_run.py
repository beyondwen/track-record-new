import argparse
import json
from pathlib import Path

from train_decision_models import load_rows, sigmoid


class ReplaySmoother:
    def __init__(self, threshold_config):
        self.start_threshold = threshold_config["start_threshold"]
        self.stop_threshold = threshold_config["stop_threshold"]
        self.start_trigger_count = threshold_config["start_trigger_count"]
        self.stop_trigger_count = threshold_config["stop_trigger_count"]
        self.start_protection_millis = threshold_config["start_protection_millis"]
        self.minimum_recording_millis = threshold_config["minimum_recording_millis"]
        self.start_hits = 0
        self.stop_hits = 0
        self.last_start_at = None

    def consume(self, start_score, stop_score, is_recording, now_millis):
        if not is_recording:
            self.stop_hits = 0
            self.start_hits = self.start_hits + 1 if start_score >= self.start_threshold else 0
            if self.start_hits >= self.start_trigger_count:
                self.start_hits = 0
                self.last_start_at = now_millis
                return "START"
            return "HOLD"

        self.start_hits = 0
        if self.last_start_at is not None:
            protection_elapsed = now_millis - self.last_start_at
            if protection_elapsed < self.start_protection_millis:
                self.stop_hits = 0
                return "HOLD"
            if protection_elapsed < self.minimum_recording_millis:
                self.stop_hits = 0
                return "HOLD"
        self.stop_hits = self.stop_hits + 1 if stop_score >= self.stop_threshold else 0
        if self.stop_hits >= self.stop_trigger_count:
            self.stop_hits = 0
            return "STOP"
        return "HOLD"


def score_row(model, row):
    raw_score = model["bias"]
    feature_map = row.get("features") if isinstance(row.get("features"), dict) else {}
    for index, feature_name in enumerate(model["feature_order"]):
        raw_value = float(row.get(feature_name, feature_map.get(feature_name, 0.0)))
        mean = model["means"][index]
        scale = model["scales"][index] or 1.0
        normalized = (raw_value - mean) / scale
        raw_score += normalized * model["weights"][index]
    return sigmoid(raw_score)


def replay_rows(rows, start_model, stop_model, threshold_config):
    smoother = ReplaySmoother(threshold_config)
    for row in rows:
        start_score = score_row(start_model, row)
        stop_score = score_row(stop_model, row)
        final_decision = smoother.consume(
            start_score=start_score,
            stop_score=stop_score,
            is_recording=bool(row.get("is_recording", row.get("isRecording", False))),
            now_millis=int(row.get("timestampMillis", 0)),
        )
        yield {
            **row,
            "start_score": start_score,
            "stop_score": stop_score,
            "final_decision": final_decision,
        }


def parse_args():
    parser = argparse.ArgumentParser(description="按窗口回放开始 / 结束决策分数与最终决策。")
    parser.add_argument("--rows", required=True, help="输入样本文件，支持 .csv / .json / .jsonl")
    parser.add_argument("--models-dir", required=True, help="模型导出目录")
    return parser.parse_args()


def load_json(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def main():
    args = parse_args()
    models_dir = Path(args.models_dir)
    rows = load_rows(args.rows)
    start_model = load_json(models_dir / "start_model.json")
    stop_model = load_json(models_dir / "stop_model.json")
    threshold_config = load_json(models_dir / "threshold_config.json")

    for record in replay_rows(rows, start_model, stop_model, threshold_config):
        print(json.dumps(record, ensure_ascii=False))


if __name__ == "__main__":
    main()

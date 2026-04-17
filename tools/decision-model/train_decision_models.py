import argparse
import csv
import json
import math
from pathlib import Path


FEATURE_ORDER = ["steps_30s", "speed_avg_30s"]
DEFAULT_BUNDLE_FILENAME = "decision-model-bundle.json"
DEFAULT_THRESHOLD_CONFIG = {
    "start_threshold": 0.8,
    "stop_threshold": 0.9,
    "start_trigger_count": 2,
    "stop_trigger_count": 4,
    "start_protection_millis": 180_000,
    "minimum_recording_millis": 120_000,
}


def sigmoid(value):
    clamped = max(min(value, 30.0), -30.0)
    return 1.0 / (1.0 + math.exp(-clamped))


def build_outputs(rows, output_dir):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    training_rows = build_training_rows(rows)
    if not training_rows:
        raise ValueError("No labeled rows available for training.")

    start_model = fit_logistic(training_rows, FEATURE_ORDER, "start_target")
    stop_model = fit_logistic(training_rows, FEATURE_ORDER, "stop_target")
    feature_config = {
        "feature_order": FEATURE_ORDER,
        "missing_value": 0.0,
        "version": 1,
    }
    threshold_config = dict(DEFAULT_THRESHOLD_CONFIG)
    bundle = {
        "version": 1,
        "start_model": start_model,
        "stop_model": stop_model,
        "feature_config": feature_config,
        "threshold_config": threshold_config,
    }

    write_json(output_dir / "start_model.json", start_model)
    write_json(output_dir / "stop_model.json", stop_model)
    write_json(output_dir / "feature_config.json", feature_config)
    write_json(output_dir / "threshold_config.json", threshold_config)
    write_json(output_dir / DEFAULT_BUNDLE_FILENAME, bundle)

    return {
        "start_model": start_model,
        "stop_model": stop_model,
        "feature_config": feature_config,
        "threshold_config": threshold_config,
        "bundle": bundle,
        "training_rows": training_rows,
    }


def fit_logistic(rows, feature_order, target_field, epochs=600, learning_rate=0.2):
    means = []
    scales = []
    normalized_rows = []

    for feature_name in feature_order:
        values = [float(row.get(feature_name, 0.0)) for row in rows]
        mean = sum(values) / len(values) if values else 0.0
        variance = sum((value - mean) ** 2 for value in values) / len(values) if values else 0.0
        scale = math.sqrt(variance) if variance > 1e-9 else 1.0
        means.append(mean)
        scales.append(scale)

    for row in rows:
        features = []
        for index, feature_name in enumerate(feature_order):
            raw_value = float(row.get(feature_name, 0.0))
            features.append((raw_value - means[index]) / scales[index])
        normalized_rows.append(
            {
                "features": features,
                "target": float(row.get(target_field, 0.0)),
            }
        )

    bias = 0.0
    weights = [0.0 for _ in feature_order]
    row_count = max(len(normalized_rows), 1)

    for _ in range(epochs):
        bias_gradient = 0.0
        weight_gradients = [0.0 for _ in feature_order]

        for row in normalized_rows:
            raw_score = bias
            for index, value in enumerate(row["features"]):
                raw_score += weights[index] * value
            prediction = sigmoid(raw_score)
            error = prediction - row["target"]
            bias_gradient += error
            for index, value in enumerate(row["features"]):
                weight_gradients[index] += error * value

        bias -= learning_rate * (bias_gradient / row_count)
        for index in range(len(weights)):
            weights[index] -= learning_rate * (weight_gradients[index] / row_count)

    return {
        "bias": bias,
        "feature_order": list(feature_order),
        "weights": weights,
        "means": means,
        "scales": scales,
        "target_field": target_field,
        "sample_count": len(rows),
    }


def write_json(path, payload):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def build_training_rows(rows):
    labeled_rows = []
    for row in rows:
        flattened = flatten_row(row)
        labeled = label_manual_boundary_row(flattened)
        if labeled is not None:
            labeled_rows.append(labeled)
            continue

        if "start_target" in flattened and "stop_target" in flattened:
            labeled_rows.append(flattened)
            continue

        labeled = label_row(flattened)
        if labeled is not None:
            labeled_rows.append(labeled)
    return labeled_rows


def flatten_row(row):
    flattened = dict(row)
    features = flattened.pop("features", None)
    if isinstance(features, dict):
        for key, value in features.items():
            flattened.setdefault(key, value)
    if "isRecording" in flattened and "is_recording" not in flattened:
        flattened["is_recording"] = 1 if flattened["isRecording"] else 0
    return coerce_row_types(flattened)


def label_row(row):
    feedback_label = row.get("feedbackLabel") or row.get("feedback_label")
    final_decision = row.get("finalDecision") or row.get("final_decision")
    if not feedback_label or not final_decision:
        return None

    labeled = dict(row)
    if feedback_label == "CORRECT":
        if final_decision == "START":
            labeled["start_target"] = 1
            labeled["stop_target"] = 0
            return labeled
        if final_decision == "STOP":
            labeled["start_target"] = 0
            labeled["stop_target"] = 1
            return labeled
        return None

    if feedback_label == "START_TOO_EARLY" and final_decision == "START":
        labeled["start_target"] = 0
        labeled["stop_target"] = 0
        return labeled

    if feedback_label == "STOP_TOO_EARLY" and final_decision == "STOP":
        labeled["start_target"] = 0
        labeled["stop_target"] = 0
        return labeled

    return None


def label_manual_boundary_row(row):
    start_source = row.get("startSource") or row.get("start_source")
    stop_source = row.get("stopSource") or row.get("stop_source")
    manual_start_at = row.get("manualStartAt") or row.get("manual_start_at")
    manual_stop_at = row.get("manualStopAt") or row.get("manual_stop_at")
    timestamp = row.get("timestampMillis") or row.get("timestamp_millis")

    if start_source != "MANUAL" or stop_source != "MANUAL":
        return None
    if manual_start_at in (None, 0.0) or manual_stop_at in (None, 0.0) or timestamp in (None, 0.0):
        return None

    labeled = dict(row)
    if manual_start_at - 30_000 <= timestamp <= manual_start_at + 60_000:
        labeled["start_target"] = 1
        labeled["stop_target"] = 0
        return labeled

    if manual_stop_at - 30_000 <= timestamp <= manual_stop_at + 60_000:
        labeled["start_target"] = 0
        labeled["stop_target"] = 1
        return labeled

    return None


def load_rows(path):
    path = Path(path)
    if path.suffix.lower() == ".jsonl":
        return load_jsonl_rows(path)
    if path.suffix.lower() == ".json":
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    return load_csv_rows(path)


def load_jsonl_rows(path):
    rows = []
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def load_csv_rows(path):
    with open(path, "r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        return [coerce_row_types(row) for row in reader]


def coerce_row_types(row):
    coerced = {}
    for key, value in row.items():
        if value in (None, ""):
            coerced[key] = 0.0
            continue
        try:
            numeric = float(value)
        except ValueError:
            coerced[key] = value
            continue
        coerced[key] = int(numeric) if numeric.is_integer() else numeric
    return coerced


def parse_args():
    parser = argparse.ArgumentParser(description="训练开始 / 结束记录决策模型。")
    parser.add_argument("--input", required=True, help="训练样本文件，支持 .csv / .json / .jsonl")
    parser.add_argument("--output-dir", required=True, help="模型输出目录")
    return parser.parse_args()


def main():
    args = parse_args()
    rows = load_rows(args.input)
    outputs = build_outputs(rows, args.output_dir)
    print(
        json.dumps(
            {
                "status": "ok",
                "sample_count": len(outputs["training_rows"]),
                "feature_order": outputs["feature_config"]["feature_order"],
                "output_dir": str(Path(args.output_dir).resolve()),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()

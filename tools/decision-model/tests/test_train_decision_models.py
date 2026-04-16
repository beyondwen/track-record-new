from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from train_decision_models import build_outputs
from train_decision_models import build_training_rows
from replay_decision_run import replay_rows


class TrainDecisionModelsTest(unittest.TestCase):

    def test_build_outputs_generates_two_model_json_files(self):
        rows = [
            {"steps_30s": 4.0, "speed_avg_30s": 1.6, "start_target": 1, "stop_target": 0},
            {"steps_30s": 0.0, "speed_avg_30s": 0.1, "start_target": 0, "stop_target": 1},
        ]

        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir)
            outputs = build_outputs(rows, output_dir)

            self.assertTrue((output_dir / "start_model.json").exists())
            self.assertTrue((output_dir / "stop_model.json").exists())
            self.assertTrue((output_dir / "decision-model-bundle.json").exists())
            self.assertEqual(
                ["steps_30s", "speed_avg_30s"],
                outputs["feature_config"]["feature_order"],
            )

    def test_build_training_rows_maps_feedback_labels_to_targets(self):
        rows = [
            {
                "timestampMillis": 30_000,
                "finalDecision": "START",
                "feedbackLabel": "CORRECT",
                "features": {"steps_30s": 4.0, "speed_avg_30s": 1.6},
            },
            {
                "timestampMillis": 60_000,
                "finalDecision": "STOP",
                "feedbackLabel": "STOP_TOO_EARLY",
                "features": {"steps_30s": 0.0, "speed_avg_30s": 0.1},
            },
            {
                "timestampMillis": 90_000,
                "finalDecision": "START",
                "feedbackLabel": "START_TOO_LATE",
                "features": {"steps_30s": 3.0, "speed_avg_30s": 1.2},
            },
        ]

        labeled = build_training_rows(rows)

        self.assertEqual(2, len(labeled))
        self.assertEqual(1, labeled[0]["start_target"])
        self.assertEqual(0, labeled[0]["stop_target"])
        self.assertEqual(0, labeled[1]["start_target"])
        self.assertEqual(0, labeled[1]["stop_target"])

    def test_replay_rows_emits_scores_and_final_decision(self):
        rows = [
            {
                "timestampMillis": 30_000,
                "steps_30s": 4.0,
                "speed_avg_30s": 1.6,
                "start_target": 1,
                "stop_target": 0,
                "is_recording": 0,
            },
            {
                "timestampMillis": 60_000,
                "steps_30s": 0.0,
                "speed_avg_30s": 0.1,
                "start_target": 0,
                "stop_target": 1,
                "is_recording": 1,
            },
        ]

        with tempfile.TemporaryDirectory() as temp_dir:
            outputs = build_outputs(rows, Path(temp_dir))
            replayed = list(
                replay_rows(
                    rows=rows,
                    start_model=outputs["start_model"],
                    stop_model=outputs["stop_model"],
                    threshold_config=outputs["threshold_config"],
                )
            )

        self.assertEqual(2, len(replayed))
        self.assertIn("start_score", replayed[0])
        self.assertIn("stop_score", replayed[0])
        self.assertIn("final_decision", replayed[0])


if __name__ == "__main__":
    unittest.main()

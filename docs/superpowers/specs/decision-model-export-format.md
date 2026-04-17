# 决策模型导出格式

## 文档目的

本文定义开始 / 结束记录决策模型在 WSL 训练脚本与 Android 端运行时之间的导出契约，避免训练侧和 Kotlin 推理侧各自理解一套字段。

当前对应工具：

- `tools/decision-model/train_decision_models.py`
- `tools/decision-model/replay_decision_run.py`

当前过渡阶段，训练样本输入还会额外携带手动闭环打标元数据：

- `recordId`
- `startSource`
- `stopSource`
- `manualStartAt`
- `manualStopAt`

当 `startSource = MANUAL` 且 `stopSource = MANUAL` 时，训练脚本按以下窗口打标签：

- `start` 正样本窗口：`manualStartAt - 30000` 到 `manualStartAt + 60000`
- `stop` 正样本窗口：`manualStopAt - 30000` 到 `manualStopAt + 60000`

窗口外样本在当前阶段不强制视为负样本。

## 文件清单

训练脚本输出目录至少包含以下 5 个文件：

- `start_model.json`
- `stop_model.json`
- `feature_config.json`
- `threshold_config.json`
- `decision-model-bundle.json`

其中前 4 个文件继续保留，便于训练、回放和单文件调试；`decision-model-bundle.json` 作为 Android 端导入使用的单文件包。

## start_model.json

开始模型与结束模型结构一致，字段约束如下：

```json
{
  "bias": -0.42,
  "feature_order": ["steps_30s", "speed_avg_30s"],
  "weights": [0.18, 0.73],
  "means": [1.2, 0.45],
  "scales": [2.1, 0.33],
  "target_field": "start_target",
  "sample_count": 128
}
```

字段说明：

- `bias`：模型偏置项。
- `feature_order`：特征顺序，Android 端必须按此顺序取值。
- `weights`：与 `feature_order` 一一对应的权重数组。
- `means`：训练时使用的均值数组。
- `scales`：训练时使用的标准差数组；若某个特征无波动，导出值必须回退为 `1.0`，避免推理侧除零。
- `target_field`：训练目标字段名，仅用于追踪导出来源。
- `sample_count`：参与训练的样本数。

约束：

- `feature_order`、`weights`、`means`、`scales` 长度必须完全一致。
- `feature_order` 顺序一旦变化，必须同时更新 Android 端加载的配置版本。

## stop_model.json

结构与 `start_model.json` 相同，仅 `target_field` 为 `stop_target`。

## feature_config.json

```json
{
  "feature_order": ["steps_30s", "speed_avg_30s"],
  "missing_value": 0.0,
  "version": 1
}
```

字段说明：

- `feature_order`：统一特征顺序。
- `missing_value`：缺失特征默认回退值。
- `version`：特征配置版本号。

## threshold_config.json

```json
{
  "start_threshold": 0.8,
  "stop_threshold": 0.9,
  "start_trigger_count": 2,
  "stop_trigger_count": 4,
  "start_protection_millis": 180000,
  "minimum_recording_millis": 120000
}
```

字段说明：

- `start_threshold`：开始模型触发阈值。
- `stop_threshold`：结束模型触发阈值。
- `start_trigger_count`：开始连续命中次数。
- `stop_trigger_count`：结束连续命中次数。
- `start_protection_millis`：开始后保护期。
- `minimum_recording_millis`：最短记录时长。

## decision-model-bundle.json

```json
{
  "version": 1,
  "start_model": {
    "bias": -0.42,
    "feature_order": ["steps_30s", "speed_avg_30s"],
    "weights": [0.18, 0.73],
    "means": [1.2, 0.45],
    "scales": [2.1, 0.33],
    "target_field": "start_target",
    "sample_count": 128
  },
  "stop_model": {
    "bias": 0.37,
    "feature_order": ["steps_30s", "speed_avg_30s"],
    "weights": [-0.15, -0.81],
    "means": [1.2, 0.45],
    "scales": [2.1, 0.33],
    "target_field": "stop_target",
    "sample_count": 128
  },
  "feature_config": {
    "feature_order": ["steps_30s", "speed_avg_30s"],
    "missing_value": 0.0,
    "version": 1
  },
  "threshold_config": {
    "start_threshold": 0.8,
    "stop_threshold": 0.9,
    "start_trigger_count": 2,
    "stop_trigger_count": 4,
    "start_protection_millis": 180000,
    "minimum_recording_millis": 120000
  }
}
```

字段说明：

- `version`：bundle 结构版本号。
- `start_model`：内容与 `start_model.json` 一致。
- `stop_model`：内容与 `stop_model.json` 一致。
- `feature_config`：内容与 `feature_config.json` 一致。
- `threshold_config`：内容与 `threshold_config.json` 一致。

## 兼容性约定

- 训练脚本新增字段时，Android 端应保持向后兼容，未识别字段允许忽略。
- 训练脚本删除或重命名现有字段时，必须同步更新 Android 端模型加载逻辑。
- 若 `feature_order` 发生变化，建议同时更新设计稿和训练样本导出说明，避免回放工具读取旧模型时出现错位。

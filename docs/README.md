# WeModern 文档

本目录记录已发布能力、实现边界、修复决策、版本变更和仍在探索的方向。它是代码之外了解当前产品状态的入口；`PRODUCT.md` 仍是产品需求的权威来源。

## 阅读顺序

| 文档 | 用途 |
| --- | --- |
| [设计系统](../DESIGN.md) | 当前 Compose 界面的视觉 token、Material You 语义色、组件、动效与设计护栏；机器可读扩展见 [DESIGN.json](../DESIGN.json) |
| [当前实现](current-implementation.md) | 已交付功能、关键实现、约束、已知边界与验证要求 |
| [版本变更记录](changelog.md) | 按版本和日期汇总功能、修复与构建变更 |
| [功能探索](explorations/README.md) | 尚未交付能力的调查结论、依赖和下一步 |
| [设计](design/) | 已采用或待参考的界面与架构设计材料 |
| [计划](plans/) | 历史及进行中的实施计划；计划不代表功能已发布 |

## 维护规则

完成任何功能实现或问题修复时，必须在同一变更中：

1. 更新 `current-implementation.md` 中受影响的行为、实现方案、限制或验证项。
2. 向 `changelog.md` 追加版本记录；修复须写清问题和解决方案。
3. 对尚未实现的想法、外部依赖或可行性结论，在 `explorations/` 新建或更新文档，并同步维护索引。

详细的强制要求见仓库根目录 [AGENTS.md](../AGENTS.md)。

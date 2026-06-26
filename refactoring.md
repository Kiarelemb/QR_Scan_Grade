# Refactoring Plan

## 背景

项目已完成第一轮 `grading` 包重命名，核心代码现在位于：

```text
src/main/java/sg/qr/kiarelemb/exam
```

当前继续进行第二轮重构，目标不是改变业务行为，而是把模板识别、答卷校准、客观题识别之间可以复用的底层能力抽出来，避免工具类继续膨胀。

需要特别注意：后续每次完成结构性重构时，都要同步更新根目录 `introduction.md`，让之后接手的 AI 助手能根据最新包结构、类职责和数据流理解项目。

## 重构原则

1. 保持模板检测、答卷识别、评分结果不变。
2. 优先抽取跨流程共用的底层能力，而不是让答卷识别依赖模板检测类。
3. `TemplateLayoutDetector` 只负责模板布局推断，不参与每张考生答卷的识别。
4. 答卷识别应继续复用模板中保存的 `SheetLayout`，再通过校准和填涂读取完成识别。
5. 每轮重构后至少执行：

```bash
mvn -q -DskipTests compile
git diff --check
```

6. 涉及模板检测时，继续用 `res/AnswerSheets.pdf` 验证：

```text
choiceQuestions=50
choiceColsPerRow=[4, 4, 2]
choiceQuestionsPerCol=[5, 5, 5, 5, 5, 5, 5, 5, 5, 5]
```

## 当前识别流程

### 模板创建流程

```text
NewTemplateDialog
  -> TemplateLayoutDetector.detectFromTemplate(...)
  -> TemplateLayoutDetectorUtils / ChoiceLayoutAnalyzer
  -> AnswerRegionBuilder
  -> SheetTemplateFileStore.saveTemplate(...)
```

`TemplateLayoutDetector` 的职责是从空白模板图中推断：

- 准考证号区域
- 选择题区域
- 填空题区域
- 每题选项框坐标
- 题目数量和选项数

### 答卷识别流程

```text
ObjectiveReviewPanel.loadCurrentAnswer()
  -> SheetImagePreprocessor.preprocess(answerFile)
  -> SheetCalibrator.calibrate(binary, template, answerSheet)
      -> RegistrationMarkDetector.detectMarkBounds(...)
      -> CoordinateTransform.from(...)
  -> ObjectiveAnswerGrader.grade(binary, currentAnswerSheet, labels)
      -> BubbleMarkReader.getFillRatio(...)
```

答卷阶段不重新运行 `TemplateLayoutDetector`。它读取已保存模板，校准坐标，再根据模板中的题目区域读取填涂。

## 已完成重构

### 1. 抽取通用几何工具

新增：

```text
src/main/java/sg/qr/kiarelemb/exam/geometry/SheetGeometryUtils.java
```

当前包含：

- `buildCornerMat(...)`
- `sortCorners(...)`
- `clusterValues(...)`
- `medianInt(...)`
- `dominantCount(...)`
- `closestColumnIndexes(...)`

已使用位置：

- `TemplateLayoutDetector`
- `TemplateLayoutDetectorUtils`
- `SheetImagePreprocessor`

目的：避免模板识别和答卷预处理各自维护角点排序、透视矩阵、聚类和众数逻辑。

### 2. 抽取二值图区域分析工具

新增：

```text
src/main/java/sg/qr/kiarelemb/exam/processing/BinaryRegionAnalyzer.java
```

当前包含：

- `clamp(Rect, int, int)`
- `inflate(Rect, int)`
- `shifted(Rect, int, int)`
- `whiteRatio(Mat, Rect)`
- `bestWhiteRatioNear(Mat, Rect, int, int, int)`

已使用位置：

- `BubbleMarkReader`
- `ObjectiveAnswerGrader`
- `SheetCalibrator`
- `SheetImagePreprocessor`

目的：统一 ROI 裁剪、白色像素比例统计、局部搜索最佳填涂率，减少重复代码和 ROI 释放风险。

### 3. 抽取选择题布局分析器

新增：

```text
src/main/java/sg/qr/kiarelemb/exam/template/detect/ChoiceLayoutAnalyzer.java
```

当前包含：

- `inferOptionGap(...)`
- `choiceColumnStarts(...)`
- `choiceColumnGroups(...)`
- 内部列组提取逻辑

已使用位置：

- `TemplateLayoutDetector`

目的：把选择题列组推断从 `TemplateLayoutDetectorUtils` 中拆出来，让模板检测主类和工具类都更小。

### 4. 瘦身 TemplateLayoutDetectorUtils

`TemplateLayoutDetectorUtils` 现在只保留模板检测专属能力：

- 气泡候选框判断
- 大区域候选框判断
- 填空区域匹配
- 模板 layout deskew

已迁出的能力：

- 通用聚类/众数/最近列匹配 -> `SheetGeometryUtils`
- 角点矩阵/角点排序 -> `SheetGeometryUtils`
- 选择题选项间距/列组推断 -> `ChoiceLayoutAnalyzer`

### 5. 抽取定位黑块检测

新增：

```text
src/main/java/sg/qr/kiarelemb/exam/processing/RegistrationMarkDetector.java
```

当前包含：

- `detectMarkBounds(Mat binary)`
- `isRegistrationMark(Mat binary, Rect bbox, double imgArea)`
- `MarkBounds`

已使用位置：

- `SheetCalibrator`

目的：把定位黑块轮廓筛选、边缘位置过滤、边界统计从 `SheetCalibrator` 中移出，让校准类专注于校准流程和 `SheetLayout` 变换。

### 6. 抽取坐标变换

新增：

```text
src/main/java/sg/qr/kiarelemb/exam/geometry/CoordinateTransform.java
```

当前包含：

- `from(RegistrationMarkDetector.MarkBounds, RegistrationMarkDetector.MarkBounds, double, double)`
- `transform(Rect)`
- `scaleX()/scaleY()/offsetX()/offsetY()`

已使用位置：

- `SheetCalibrator`

目的：把模板坐标到答卷坐标的映射逻辑提升为可复用几何能力，后续主观题裁图、预览叠加、人工评分区域校准可继续复用。

### 7. 清理 ObjectiveAnswerGrader 历史 helper

已删除：

```text
ObjectiveAnswerGrader.getWhitePixelRatio(...)
```

该方法只是 `BinaryRegionAnalyzer.whiteRatio(...)` 的薄包装，且当前没有调用点。

### 8. 已完成验证

已执行：

```bash
mvn -q -DskipTests compile
git diff --check
```

均通过。

已执行模板检测调试：

```bash
TemplateDetectionDebug res/AnswerSheets.pdf
```

关键结果：

```text
choiceQuestions=50
choiceOptionLabels=[A, B, C, D]
choiceColsPerRow=[4, 4, 2]
choiceQuestionsPerCol=[5, 5, 5, 5, 5, 5, 5, 5, 5, 5]
```

## 后续重构建议

### 1. 继续拆分图像处理职责

当前 `SheetImagePreprocessor` 仍承担较多职责：

- 读取图片
- 灰度化
- 二值化
- 纸张透视校正
- 空心方块填充

后续可考虑拆成：

```text
SheetImageReader
SheetBinarizer
SheetDeskewer
```

保守做法：先只抽 `SheetDeskewer`，因为模板检测和答卷预处理都需要 deskew。

### 2. 进一步收口 SheetCalibrator 内部职责

`SheetCalibrator` 仍包含选择题大行/小列纵向校正逻辑，可考虑继续抽成：

```text
ChoiceRegionAligner
```

这样 `SheetCalibrator` 只保留：模板二值图缓存、定位黑块检测编排、坐标变换、区域校准结果组装。

### 3. 进一步命名收口

后续可继续整理命名：

```text
TemplateLayoutDetectorUtils -> TemplateRegionDetector 或 TemplateLayoutImageUtils
SheetImagePreprocessor      -> SheetImageProcessor / SheetBinarizer
SheetCalibrator             -> AnswerSheetCalibrator
BinaryRegionAnalyzer        -> BinaryImageRegionAnalyzer
```

是否改名取决于下一轮包结构是否继续细分。

## introduction.md 同步要求

后续 AI 助手或开发者继续重构时，请同步更新根目录：

```text
introduction.md
```

至少需要更新：

1. 项目结构树。
2. 核心数据流。
3. 关键类说明。
4. 模板检测与答卷识别的职责边界。
5. 新增/删除/改名的核心类。
6. 当前已知问题和验证方式。

特别要写清楚：

- `TemplateLayoutDetector` 只用于模板创建阶段。
- 考生答卷识别不重新推断模板布局。
- 答卷识别复用 `SheetTemplate` / `SheetLayout`，经过 `SheetCalibrator` 坐标校准后由 `ObjectiveAnswerGrader` 和 `BubbleMarkReader` 读取。

这样可以避免后续 AI 助手误以为模板识别和答卷识别应该共用同一个高层 detector。

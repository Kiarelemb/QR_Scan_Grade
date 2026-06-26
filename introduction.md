# QR Scan Grade — 项目介绍

## 一、项目概述

QR Scan Grade 是一个基于 Java Swing（QRSwing 框架）+ OpenCV 的**答题卡扫描阅卷系统**。支持：

- 从 PDF/图片自动检测答题卡模板布局（准考证号、选择题、填空题区域）
- 客观题自动判分（通过气泡填涂识别）
- 主观题 OCR 识别 / 人工批阅
- 模板以 `.sg` 文件（ZIP 格式）保存和分发
- 项目进度以 `.properties` 文件持久化

**技术栈：** Java 17+, QRSwing（自研 Swing 封装框架）, OpenCV 4.7（bytedeco/javacpp-presets）, PDFBox 3.0

---

## 二、项目结构

```
src/main/java/sg/qr/kiarelemb/
├── Enter.java                          # 入口 main()
├── MainWindow.java                     # 主窗口（单例），导航控制
├── component/                          # 可复用 UI 组件
│   ├── AnswerSheetPreviewPanel.java    # 答题卡预览面板（支持翻页）
│   ├── ManualScoreReviewDialog.java    # 人工评分对话框
│   └── ...                             # 其他基础组件（Label, Button 等）
├── data/                               # 全局数据 / 配置
│   ├── Keys.java                       # 设置/配置键常量
│   └── Utils.java                      # 工具方法
├── exam/                               # ★ 核心阅卷逻辑
│   ├── geometry/
│   │   ├── SheetGeometryUtils.java     # 通用几何/聚类/角点工具
│   │   └── CoordinateTransform.java    # 基于定位标记边界的坐标变换
│   ├── template/detect/
│   │   ├── TemplateLayoutDetector.java # ★ 模板布局自动检测
│   │   ├── TemplateLayoutDetectorUtils.java # 布局检测工具方法
│   │   ├── ChoiceLayoutAnalyzer.java   # 选择题选项间距和列组推断
│   │   └── DetectedBox.java            # 检测到的矩形数据类
│   ├── model/
│   │   ├── SheetTemplate.java          # 模板数据模型
│   │   ├── SheetLayout.java            # 答题卡布局模型
│   │   ├── SheetQuestion.java          # 单道题模型（含区域 + 答案）
│   │   ├── SubjectiveAnswerRegion.java # 主观题区域定义
│   │   ├── GradingProject.java         # 项目状态持久化
│   │   └── GradingOutcome.java         # 批阅结果
│   ├── processing/
│   │   ├── SheetTemplateFileStore.java # 模板 .sg 文件序列化
│   │   │   └── TemplateSnapshot        # 模板序列化快照（DTO）
│   │   │   └── SheetLayoutSnapshot     # 布局序列化快照（DTO）
│   │   │   └── SheetQuestionSnapshot   # 题目序列化快照（DTO）
│   │   │   └── SubjectiveRegionSnapshot# 主观题区域序列化快照（DTO）
│   │   │   └── RectSnapshot            # 矩形序列化快照（DTO）
│   │   ├── AnswerRegionBuilder.java    # 从布局参数构建 SheetLayout
│   │   ├── ObjectiveAnswerGrader.java  # ★ 客观题批阅引擎
│   │   ├── BubbleMarkReader.java       # 气泡填涂率检测
│   │   ├── BinaryRegionAnalyzer.java   # 二值图 ROI / 白色像素比例工具
│   │   ├── SheetImagePreprocessor.java # 图像预处理（二值化、校正）
│   │   ├── DocumentPageLoader.java     # PDF 渲染 / 图片加载
│   │   ├── SheetCalibrator.java        # 四角标记校准
│   │   ├── RegistrationMarkDetector.java # 定位黑块检测
│   │   ├── HandwritingOcrReader.java   # 手写识别接口
│   │   ├── BaiduHandwritingOcr.java    # 百度 OCR 实现
│   │   └── GoogleVisionHandwritingOcr.java # Google Vision 实现
│   ├── scoring/                        # 分数计算模式
│   │   ├── Examinee.java               # 考生模型
│   │   ├── ScoreSection.java           # 科段模型
│   │   └── QuestionScorePolicy.java    # 题目计分策略
│   ├── results/                        # 阅卷结束阶段
│   │   ├── ResultsPanel.java           # 最终汇总界面
│   │   ├── ResultsActionButton.java     # 结果面板动作按钮基类
│   │   ├── FinishProjectButton.java    # 结束流程按钮
│   │   ├── CalculateScoresButton.java  # 计算成绩按钮
│   │   ├── ExportResultsButton.java    # 导出成绩按钮
│   │   ├── BackToReviewButton.java     # 返回校对按钮
│   │   ├── ResultsExporter.java        # 成绩导出
│   │   ├── ScoringPlan.java            # 计分方案
│   │   ├── ScorePolicy.java            # 分数规则
│   │   ├── RawScorePolicy.java         # 原始分数规则（解析中间态）
│   │   └── ScoreOutcome.java           # 分数结果
│   ├── ObjectiveReviewPanel.java       # 客观题复审批次界面
│   ├── SubjectiveOcrReviewPanel.java   # 主观题 OCR 复核界面
│   ├── ManualScoringPanel.java         # 人工批阅界面
│   ├── NewGradingProjectDialog.java    # ★ 新建批阅项目对话框
│   ├── ObjectiveReviewTextPane.java    # 客观题复核文本面板
│   └── SubjectiveReviewTextPane.java   # 主观题复核文本面板
├── start/                              # 启动界面
│   ├── StartPanel.java                 # 首页面板
│   ├── NewTemplateButton.java          # 新建模板按钮
│   ├── NewTemplateDialog.java          # ★ 新建模板对话框
│   ├── ExistingTemplateButton.java     # 已有模板入口按钮
│   ├── ExistingTemplatePanel.java      # 模板列表选择面板
│   ├── ContinueProjectButton.java      # 继续项目入口按钮
│   ├── ContinueProjectWindow.java      # 继续项目窗口
│   ├── TemplateAnalysisPane.java       # 模板分析分屏面板
│   └── StartActionButton.java          # 启动页通用按钮基类
├── setting/                            # 设置窗口
├── menu/                               # 菜单配置
└── test/                               # 测试 / 调试
    ├── TemplateDetectionDebug.java     # 模板检测调试入口
    ├── AnswerSheetTestGenerator.java   # 测试答题卡生成器
    └── Test.java                       # 手工测试
```

---

## 三、核心数据流

### 3.1 模板创建流程

```
PDF/图片
  → DocumentPageLoader.documentImages()   # PDF → PNG（300DPI）
  → NewTemplateDialog                      # 打开模板图片
    → TemplateLayoutDetector.detectFromTemplate()    # OpenCV 检测布局
      1. 透视校正（deskewForLayout）
      2. 自适应阈值二值化
      3. 轮廓检测 → 过滤矩形（面积 + 宽高比）
      4. 气泡聚类：识别准考证号列 + 选择题行列 + 填空大框
      5. 构建 SheetLayout（buildAnswerSheet → AnswerRegionBuilder.buildExamSheet）
    → SheetTemplate 保存为 .sg 文件（SheetTemplateFileStore.save）
```

### 3.2 阅卷流程

```
.sg 模板 + 答卷图片
  → SheetTemplateFileStore.load()         # 加载模板
  → GradingProject.refreshAnswerFilesFromDirectory()  # 扫描答卷
  → ObjectiveAnswerGrader.grade()         # 逐道题判分
    1. 遍历 examIdDigits → 读取准考证号
    2. 遍历 choiceQuestions → BubbleMarkReader 测填涂率 → 选出最佳选项
    3. 对比标准答案 → 判对错 / 标记不确定
  → GradingOutcome → GradingProject.putRecognizedAnswer()
  → 复核界面（ObjectiveReviewPanel / SubjectiveOcrReviewPanel / ManualScoringPanel）
  → 最终汇总（ResultsPanel）
```

---

## 四、关键类说明

### TemplateLayoutDetector（布局检测引擎）

最核心的类，负责从模板图像中自动推断答题卡布局。

**检测过程：**
1. `deskewForLayout()` — 用最大轮廓做透视校正到 2480×3507
2. `detectAllRectanglesByThreshold()` — 自适应阈值 + 轮廓检测，过滤出气泡候选矩形
3. `analyzeLayout()` — 核心推断：
   - 按尺寸筛选气泡，按列聚类识别准考证号位（10 个均匀 Y 分布的列）
   - 按行聚类寻找选择题行（必须在考号区域下方）
   - 调用 `inferChoiceColumns()` 推断列结构
4. `postProcessRects()` — 找出准考证/选择/填空大框，调用 `trimAndNormalizeChoiceLayout()` 修正
5. `buildAnswerSheet()` — 将参数转为 `SheetLayout` 模型

**关键参数：**
| 参数 | 含义 |
|------|------|
| `examIdDigits` | 准考证号位数 |
| `examStartX/Y`, `examBubbleW/H`, `examHGap/VGap` | 考号区域几何 |
| `choiceStartX/Y`, `choiceBubbleW/H`, `choiceHGap/VGap` | 选择题区域几何 |
| `choiceOptionCount` | 每题选项数（3 或 4） |
| `choiceRows`, `choiceColsPerRow[]`, `choiceColStartXs[]`, `choiceQuestionsPerCol[]` | 选择题行列结构 |
| `fillStartX/Y`, `fillBoxW/H`, `fillBlankCount` | 填空大框 |

### ObjectiveAnswerGrader（批阅引擎）

对单张答卷图像做全卷批阅。核心方法 `grade(Mat binary, SheetLayout, String[] choiceMap)`：

- **准考证号识别：** 遍历 `examIdDigits` 列，每列 10 个数字泡，用 `BubbleMarkReader.getFillRatio()` 找填涂率最高的泡 → 组合成准考证号
- **选择题判分：** 对每道 `SINGLE_CHOICE` 题，`detectBestOption()` 找出填涂率最高的选项，与标准答案对比。若最高和次高填涂率差 <10%，标记为"不确定"
- **填空题：** 当前标记为 `[待OCR]`，等待 OCR 或人工批阅

### BubbleMarkReader（气泡填涂检测）

在给定的 `Rect` 区域内，自适应搜索暗色像素比例来判断气泡是否被填涂。用局部搜索补偿拍照偏移。

### BinaryRegionAnalyzer（二值图区域分析）

封装二值图 ROI 裁剪、矩形膨胀/平移、白色像素比例统计和局部最佳填涂率搜索。当前被 `BubbleMarkReader`、`ObjectiveAnswerGrader`、`SheetCalibrator`、`SheetImagePreprocessor` 复用。

### SheetCalibrator / RegistrationMarkDetector / CoordinateTransform

`SheetCalibrator` 负责答卷坐标校准流程：读取模板二值图、检测模板与答卷定位黑块、生成坐标变换、将 `SheetLayout` 中的题目区域映射到当前答卷。

`RegistrationMarkDetector` 只负责从二值图中筛选定位黑块并返回边界；`CoordinateTransform` 位于 `exam.geometry`，负责把模板坐标按定位标记边界映射到答卷坐标。

### AnswerRegionBuilder

根据 `TemplateLayoutDetector` 推断出的参数（起始坐标、间距、行列结构、选项数），为每道题生成 `List<Rect>` 选项区域，构建完整的 `SheetLayout`。

### SheetTemplate / SheetTemplateFileStore

`SheetTemplate` 是 record，包含模板图片、`SheetLayout` 布局、大框矩形、主观题区域等。

`SheetTemplateFileStore` 负责 `.sg` 文件的序列化（ZIP 格式）：
- `save()` → 写 `template.bin`（Java 序列化）+ `image/` 下的多页图片
- `load()` → 反序列化还原为 `SheetTemplate`

序列化使用内部 DTO 类：`TemplateSnapshot`、`SheetLayoutSnapshot`、`SheetQuestionSnapshot`、`SubjectiveRegionSnapshot`、`RectSnapshot`，均标注 `serialVersionUID = 1L`。

模板格式版本演进：v1（单页）→ v3（多页图片支持）。旧版本兼容读取。

### GradingProject（项目持久化）

项目状态通过 `.properties` 文件持久化，包含：
- 模板路径、答卷目录
- 每位考生识别出的答案（`putRecognizedAnswer`）
- 人工复核修正的答案（`putReviewedAnswer`）
- 主观题答案（`putSubjectiveAnswer`）
- 人工评分（`putManualScore`）
- 学生姓名映射

### SubjectiveAnswerRegion（主观题区域）

支持三种模式：
- `OCR` — 调用 OCR 服务（`BaiduHandwritingOcr` / `GoogleVisionHandwritingOcr`）
- `MANUAL` — 人工在界面上输入评分
- `MIXED` — 先 OCR 后人工确认

支持多页：`pageIndex` 字段（0 为正面，>=1 为后续页）。

---

## 五、模板检测算法关键细节

### 5.1 气泡聚类

- 按尺寸 + 宽高比过滤小矩形（`isBubbleCandidate`）
- 按 Y 坐标聚类（`clusterValues`，容差 15px），形成行组
- 按 X 坐标聚类（容差 20px），去重
- 列的 X 坐标去重 → `clusterValues`

### 5.2 选项间距推断（`inferOptionGap`）

频率统计法：收集 [50, 115] 范围内的连续 X 间隙，按 5px 分桶，取出现频率最高的桶。若频率相同，偏好接近 85px 的值。

### 5.3 列检测（`choiceColumnGroups`）

两级策略：
1. **列间断：** 间隙 > `max(100, optionGap×2)` 处切分列组
2. **交织检测：** 若组内坐标 > 4 且存在交替小/大间隙模式，按奇偶索引拆分为两列（处理相邻三选项列气泡交织）

### 5.4 行带（Band）识别

同一大行内的问题行 Y 间距均匀；大行间有更大的 Y 间隙（> `normalRowGap × 2`）。

---

## 六、已知问题

### 6.1 气泡检测噪声[历史问题，已解决]

`detectAllRectanglesByThreshold()` 会把答题卡上的非气泡图形（如填充分隔线、字符边缘等）误检为气泡，导致行内 X 坐标出现额外噪声。这会干扰行聚类与列检测。

当前 `res/AnswerSheets.pdf` 模板检测结果已恢复为 50 题：

```text
choiceQuestions=50
choiceColsPerRow=[4, 4, 2]
choiceQuestionsPerCol=[5, 5, 5, 5, 5, 5, 5, 5, 5, 5]
```

### 6.2 三选项列与四选项列交织

模板第 1 大行中 1266（3 选项）和 1287（4 选项）起始坐标仅差 21px，选项框排序后交织排列。当前用 **贪婪前向选取**（forward-greedy）算法处理：
- 以每个气泡坐标为起点，向前搜索 optionGap 等距序列
- 验证序列内所有间隙在容差范围内
- 接受 3-4 个有效序列作为一列

### 6.3 后续带过滤

首行 5 列确定后，后续行（第 2~3 大行）的列会与首行列坐标匹配过滤，仅保留几何位置一致的列。这有效去除了后续行中的噪声列，但也会丢失少数真实列。

### 6.4 系统依赖

- 需要 JDK 17+ 编译（当前系统仅 JDK 11 JRE，需通过 `~/.jdks/openjdk-26.0.1` 运行）
- QRSwing 框架以 system-scoped JAR 引入（`~/IdeaProjects/QR_Swing/QR_Swing.jar`）
- OpenCV native 库通过 JavaCV 平台依赖自动加载

---

## 七、后续计划

### 短期

1. **继续拆分图像处理职责** — `SheetImagePreprocessor` 仍包含读取、灰度化、二值化、纸张透视校正和空心方块填充，可优先抽 `SheetDeskewer`
2. **支持每列独立选项数** — 将 `choiceOptionCount` 从模板级改为列级数组，使混合 3/4 选项模板各列正确生成选项区域
3. **列合并容差动态化** — 将列合并阈值改为基于 `optionGap` 的动态值
4. **验证最终效果** — 在 IDE 中完整走通"新建模板 → 创建项目 → 阅卷 → 出分"全流程，确认 `res/AnswerSheets.pdf` 模板可用

### 中长期

5. **主观题区域自动检测** — 基于大框检测自动识别主观题区域，减少手动定位
6. **多页模板完整流程** — 阅卷阶段的逐页校准、裁图、评分进一步完善
7. **OCR 服务优化** — 增加本地 OCR 方案（如 PaddleOCR）降低网络依赖
8. **测试覆盖** — 增加布局检测和批阅的单元测试 / 回归测试
9. **性能优化** — 1566 个潜在气泡对 2037 个轮廓做全遍历，可增加提前剪枝

---

## 八、开发备忘

### 运行方式

```bash
# 编译
JAVA_HOME=~/.jdks/openjdk-26.0.1 mvn compile

# 运行模板检测调试
JAVA_HOME=~/.jdks/openjdk-26.0.1 mvn exec:java \
  -Dexec.mainClass="sg.qr.kiarelemb.test.TemplateDetectionDebug" \
  -Dexec.classpathScope=compile

# 在 IntelliJ IDEA 中运行主程序（推荐）
# 主类: sg.qr.kiarelemb.Enter
```

### 关键配置文件

- `pom.xml` — Maven 配置（Java 17，OpenCV，PDFBox，QRSwing system dependency）
- `res/settings/window.properties` — 窗口设置
- `logs/` — 运行日志（按日期分文件）
- `tmp/pdf-images/` — PDF 渲染临时图片

### Git 提交风格

提交信息简短（如"更新"），建议后续改为语义化格式。
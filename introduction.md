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
├── grading/                            # 核心阅卷逻辑
│   ├── layout/
│   │   ├── LayoutDetector.java         # ★ 模板布局自动检测
│   │   └── DetectedRect.java           # 检测到的矩形数据类
│   ├── model/
│   │   ├── Template.java               # 模板 record
│   │   ├── AnswerSheet.java            # 答题卡布局模型
│   │   ├── Question.java               # 单道题模型（含区域 + 答案）
│   │   ├── SubjectiveRegion.java       # 主观题区域定义
│   │   ├── Project.java                # 项目状态持久化
│   │   └── GradingResult.java          # 批阅结果
│   ├── pipeline/
│   │   ├── TemplateProcessor.java      # 模板 .sg 文件序列化
│   │   ├── RegionDetector.java         # 从布局参数构建 AnswerSheet
│   │   ├── AnswerComparator.java       # ★ 客观题批阅引擎
│   │   ├── ChoiceReader.java           # 气泡填涂率检测
│   │   ├── ImagePreprocessor.java      # 图像预处理（二值化、校正）
│   │   ├── DocumentImageLoader.java    # PDF 渲染 / 图片加载
│   │   └── AnswerSheetCalibrator.java  # 四角标记校准
│   ├── end/                            # 阅卷结束阶段
│   │   ├── ProjectEnd.java             # 最终汇总界面
│   │   └── ScorePlan/ScoreResult/...   # 分数规则、导出
│   ├── qsmethod/                       # 分数计算模式
│   │   ├── Student.java, Section.java  # 学生/科段模型
│   │   └── QuestionScoring.java        # 题目计分逻辑
│   ├── ProjectReviewPanel.java         # 客观题复审批次界面
│   ├── ProjectSubjectiveReviewPanel.java # 主观题 OCR 复核界面
│   └── ProjectManualReviewPanel.java   # 人工批阅界面
├── start/                              # 启动界面
│   ├── StartPanel.java                 # 首页面板
│   ├── NewTemplateBtn.java             # 新建模板按钮
│   ├── NewTmptWindow.java              # ★ 新建模板窗口
│   ├── ExistTemplateBtn.java           # 已有模板入口
│   ├── ContinueProjectBtn.java         # 继续项目入口
│   └── TmptDataSplitPane.java          # 模板分析分屏面板
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
  → DocumentImageLoader.documentImages()   # PDF → PNG（300DPI）
  → NewTmptWindow                            # 打开模板图片
    → LayoutDetector.detectFromTemplate()    # OpenCV 检测布局
      1. 透视校正（deskewForLayout）
      2. 自适应阈值二值化
      3. 轮廓检测 → 过滤矩形（面积 + 宽高比）
      4. 气泡聚类：识别准考证号列 + 选择题行列 + 填空大框
      5. 构建 AnswerSheet（buildAnswerSheet → RegionDetector.buildExamSheet）
    → Template 保存为 .sg 文件（TemplateProcessor.save）
```

### 3.2 阅卷流程

```
.sg 模板 + 答卷图片
  → TemplateProcessor.load()               # 加载模板
  → Project.refreshAnswerFilesFromDirectory()  # 扫描答卷
  → AnswerComparator.grade()               # 逐道题判分
    1. 遍历 examIdDigits → 读取准考证号
    2. 遍历 choiceQuestions → ChoiceReader 测填涂率 → 选出最佳选项
    3. 对比标准答案 → 判对错 / 标记不确定
  → GradingResult → Project.putRecognizedAnswer()
  → 复核界面（ProjectReviewPanel / ProjectSubjectiveReviewPanel / ProjectManualReviewPanel）
  → 最终汇总（ProjectEnd）
```

---

## 四、关键类说明

### LayoutDetector（布局检测引擎）

最核心的类，负责从模板图像中自动推断答题卡布局。

**检测过程：**
1. `deskewForLayout()` — 用最大轮廓做透视校正到 2480×3507
2. `detectAllRectanglesByThreshold()` — 自适应阈值 + 轮廓检测，过滤出气泡候选矩形
3. `analyzeLayout()` — 核心推断：
   - 按尺寸筛选气泡，按列聚类识别准考证号位（10 个均匀 Y 分布的列）
   - 按行聚类寻找选择题行（必须在考号区域下方）
   - 调用 `inferChoiceColumns()` 推断列结构
4. `postProcessRects()` — 找出准考证/选择/填空大框，调用 `trimAndNormalizeChoiceLayout()` 修正
5. `buildAnswerSheet()` — 将参数转为 `AnswerSheet` 模型

**关键参数：**
| 参数 | 含义 |
|------|------|
| `examIdDigits` | 准考证号位数 |
| `examStartX/Y`, `examBubbleW/H`, `examHGap/VGap` | 考号区域几何 |
| `choiceStartX/Y`, `choiceBubbleW/H`, `choiceHGap/VGap` | 选择题区域几何 |
| `choiceOptionCount` | 每题选项数（3 或 4） |
| `choiceRows`, `choiceColsPerRow[]`, `choiceColStartXs[]`, `choiceQuestionsPerCol[]` | 选择题行列结构 |
| `fillStartX/Y`, `fillBoxW/H`, `fillBlankCount` | 填空大框 |

### AnswerComparator（批阅引擎）

对单张答卷图像做全卷批阅。核心方法 `grade(Mat binary, AnswerSheet, String[] choiceMap)`：

- **准考证号识别：** 遍历 `examIdDigits` 列，每列 10 个数字泡，用 `ChoiceReader.getFillRatio()` 找填涂率最高的泡 → 组合成准考证号
- **选择题判分：** 对每道 `SINGLE_CHOICE` 题，`detectBestOption()` 找出填涂率最高的选项，与标准答案对比。若最高和次高填涂率差 <10%，标记为"不确定"
- **填空题：** 当前标记为 `[待OCR]`，等待 OCR 或人工批阅

### ChoiceReader（气泡填涂检测）

在给定的 `Rect` 区域内，自适应搜索暗色像素比例来判断气泡是否被填涂。用局部搜索补偿拍照偏移。

### RegionDetector

根据 `LayoutDetector` 推断出的参数（起始坐标、间距、行列结构、选项数），为每道题生成 `List<Rect>` 选项区域，构建完整的 `AnswerSheet`。

### Template / TemplateProcessor

`Template` 是 record，包含模板图片、`AnswerSheet` 布局、大框矩形、主观题区域等。

`TemplateProcessor` 负责 `.sg` 文件的序列化（ZIP 格式）：
- `save()` → 写 `template.bin`（Java 序列化）+ `image/` 下的多页图片
- `load()` → 反序列化还原为 `Template`

模板格式版本演进：v1（单页）→ v3（多页图片支持）。旧版本兼容读取。

### Project（项目持久化）

项目状态通过 `.properties` 文件持久化，包含：
- 模板路径、答卷目录
- 每位考生识别出的答案（`putRecognizedAnswer`）
- 人工复核修正的答案（`putReviewedAnswer`）
- 主观题答案（`putSubjectiveAnswer`）
- 人工评分（`putManualScore`）
- 学生姓名映射

### 主观题区域（SubjectiveRegion）

支持三种模式：
- `OCR` — 调用 OCR 服务（BaiduOcrRecognizer / GoogleVisionOcrRecognizer）
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

### 6.1 气泡检测噪声

`detectAllRectanglesByThreshold()` 会把答题卡上的非气泡图形（如填充分隔线、字符边缘等）误检为气泡，导致行内 X 坐标出现额外噪声。这会干扰行聚类与列检测。

当前 `res/AnswerSheets.pdf` 模板检测结果为 35 题（应为 40），差距约 5 题。主要原因为：
- 模板第 1 大行包含 5 列正确检出（361、815、1266、1287、1726）
- 第 2~3 大行的列检测不完全，经首行参考过滤后仅保留首列（361）

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

### 紧急

1. **补上 5 题差距** — 当前第 1 大行正确（5 列×5 题=25 题），第 2~3 行仅各保留 1 列（共 10 题）。差距来自后续行未检出的三选项列，可通过以下方向解决：
   - 提高后续行中列的检测质量（更好的 X 坐标聚类或容差调整）
   - 不用首行参考过滤，改用基于选项数一致性的列过滤

### 短期

2. **支持每列独立选项数** — 将 `choiceOptionCount` 从模板级改为列级数组，使混合 3/4 选项模板各列正确生成选项区域
3. **列合并容差动态化** — 将 `inferChoiceColumns()` 中硬编码的 20px 合并阈值改为基于 `optionGap` 的动态值
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

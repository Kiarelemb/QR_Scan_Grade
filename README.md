# 智能阅卷系统 (QR Scan Grade)

**版本**: v26.01  
**作者**: Kiarelemb QR  
**语言**: Java 17 | **构建**: Maven | **GUI**: Swing

一套基于计算机视觉的桌面端智能阅卷系统，支持答题卡（OMR）的自动识别、批改、手写 OCR 识别、成绩计算与导出。

---

## 功能特性

### 核心功能

- **答题卡模板创建** — 自定义答题卡模板，支持设置考号区、选择题区、填空题区等区域布局
- **图像预处理** — 灰度化、高斯模糊、二值化、透视校正，将扫描图像标准化至 2480×3507 像素
- **填涂识别（OMR）** — 通过 OpenCV 轮廓检测定位填涂区域，分析像素填充率自动识别选择题答案
- **考号识别** — 自动识别答题卡上的考号填涂
- **手写 OCR 识别** — 对接百度 OCR / Google Vision API，识别填空题手写文字（支持日语）
- **人工复核** — 选择题答案可手动修正，填空题 OCR 结果可编辑，支持人工打分
- **成绩计算** — 支持普通计分和量表计分两种模式，可配置分值规则
- **成绩导出** — 支持成绩数据的导出与报表生成

### 工作流程

```
创建模板 → 新建项目 → 图像加载 → 预处理 → 答题卡校准 → 填涂识别 → 答案比对 → 主观题 OCR → 人工复核 → 成绩计算 → 导出
```

---

## 技术栈

| 技术                          | 用途                     |
|-----------------------------|------------------------|
| **Java 17**                 | 主要开发语言                 |
| **Maven**                   | 项目构建与依赖管理              |
| **Java Swing**              | 桌面 GUI 界面              |
| **JavaCV / OpenCV 1.5.9**   | 计算机视觉（图像预处理、轮廓检测、透视变换） |
| **Apache PDFBox 3.0.3**     | PDF 文件渲染为图像            |
| **百度 OCR API**              | 手写文字识别（支持日语）           |
| **Google Cloud Vision API** | 文档文字检测                 |
| **QR-Swing**                | 自定义 Swing UI 框架        |

---

## 项目结构

```
QR_Scan_Grade/
├── pom.xml                          # Maven 构建配置
├── res/
│   ├── default_settings.properties  # 默认应用设置
│   ├── alibaba.ttf                  # 阿里巴巴普惠体
│   ├── DigitalNumbers.ttf           # 数码管字体
│   ├── ico/                         # 应用图标与启动图
│   └── settings/
│       ├── setting.properties       # 用户设置（OCR密钥、主题等）
│       └── window.properties        # 窗口位置与大小
├── src/main/java/sg/qr/kiarelemb/
│   ├── Enter.java                   # 程序入口（main 方法）
│   ├── MainWindow.java              # 主窗口
│   ├── FlashLoadingWindow.java      # 启动闪屏
│   ├── component/                   # 自定义 Swing 组件
│   ├── data/                        # 配置项与工具方法
│   ├── grading/                     # 核心阅卷逻辑
│   │   ├── model/                   # 数据模型（AnswerSheet, Question, Template 等）
│   │   ├── pipeline/                # 图像处理流水线（预处理、校准、识别、比对）
│   │   ├── end/                     # 最终评分与导出
│   │   ├── layout/                  # 答题卡布局自动检测
│   │   └── qsmethod/                # 量表计分法
│   ├── key/                         # 键盘快捷键管理
│   ├── menu/                        # 菜单项
│   ├── setting/                     # 设置对话框
│   ├── start/                       # 启动/欢迎面板
│   └── res/                         # 资源常量
└── target/                          # 编译输出
```

---

## 环境要求

- **JDK 17** 或更高版本
- **Maven 3.6+**
- **操作系统**: Windows / macOS / Linux
- **网络连接**: OCR 功能需要访问百度 OCR 或 Google Vision API

---

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd QR_Scan_Grade
```

### 2. 安装本地依赖

项目依赖自定义 UI 框架 `QR-Swing`，需要将其 JAR 安装到本地 Maven 仓库或放置在项目同级目录：

```
/home/kiarelemb/IdeaProjects/QR_Swing.jar
```

如需修改路径，编辑 `pom.xml` 中 `QR-Swing` 依赖的 `<systemPath>`。本文末尾已附上项目地址。

### 3. 编译运行

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="sg.qr.kiarelemb.Enter"
```

或在 IDE（IntelliJ IDEA）中直接运行 `Enter.java` 的 `main` 方法。

### 4. 配置 OCR（可选）

首次运行时在设置中配置 OCR 服务：

- **百度 OCR**: 需填写 API Key 和 Secret Key
- **Google Vision**: 需配置服务账号凭证

---

## 使用说明

### 创建答题卡模板

1. 启动程序后在开始面板选择"新建模板"
2. 导入答题卡图片或 PDF
3. 标记考号区、选择题区域、填空题区域
4. 设置各题正确答案及分值
5. 保存模板（.sg 文件）

### 阅卷流程

1. **新建项目** — 选择模板与答题卡文件夹，填写标准答案与学生名单
2. **选择题复核** — 查看系统识别的填涂结果，可手动修正
3. **主观题复核** — 查看 OCR 识别的手写文字，编辑修正
4. **人工打分** — 对需要人工评分的题目进行打分
5. **成绩计算** — 配置计分规则，计算最终成绩
6. **导出成绩** — 导出成绩报表

---

## 数据模型

| 类名              | 说明                 |
|-----------------|--------------------|
| `Template`      | 答题卡模板（布局、答案、分值规则）  |
| `AnswerSheet`   | 答题卡布局信息（考号位数、题目区域） |
| `Question`      | 单道题目（类型、选项区域、正确答案） |
| `GradingResult` | 批改结果（考号、各题结果、正确性）  |
| `Project`       | 阅卷项目（存储所有识别与复核状态）  |

文件格式：
- **`.sg`** — 答题卡模板文件（ZIP 格式，内含序列化数据与模板图片）
- **`.sgp`** — 阅卷项目文件（Properties 格式）

---

## 许可证

[待定]

---

## 致谢

本项目使用了以下开源项目：
- [QR_Swing](https://github.com/Kiarelemb/QR_Swing) — 自研 Java Swing 拓展框架
- [JavaCV](https://github.com/bytedeco/javacv) — Java 接口到 OpenCV
- [Apache PDFBox](https://pdfbox.apache.org/) — PDF 处理库
- 百度 OCR — 手写文字识别
- Google Cloud Vision — 文档文字检测
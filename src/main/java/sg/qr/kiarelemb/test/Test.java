package sg.qr.kiarelemb.test;

import method.qr.kiarelemb.utils.QRCodePack;
import method.qr.kiarelemb.utils.QRFileUtils;
import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRRandomUtils;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import sg.qr.kiarelemb.grading.layout.LayoutDetector;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.GradingResult;
import sg.qr.kiarelemb.grading.pipeline.AnswerComparator;
import sg.qr.kiarelemb.grading.pipeline.ChoiceReader;
import sg.qr.kiarelemb.grading.pipeline.ImagePreprocessor;
import sg.qr.kiarelemb.grading.pipeline.RegionDetector;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Test {

	private static Logger logger;
	private static final String INPUT_PATH = "F:\\ans\\answeあr_sheet_000.png";
	private static final String OUTPUT_DIR = "ans/output/";


	public static void main(String[] args) {
		String str = QRCodePack.encrypt(INPUT_PATH, "kiarelemb");
		System.out.println("str = " + str);
		System.out.println("str = " + QRCodePack.decrypt(str, "kiarelemb"));
	}

	public static void mainss(String[] args) {
		String str = "ABCD";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			int r = QRRandomUtils.getRandomInt(4);
			sb.append(str.charAt(r)).append(" ");
		}
		System.out.println(sb.toString().trim());
	}

	public static void maina(String[] args) {
		QRLoggerUtils.prefix = "sg";
		QRLoggerUtils.initLogger(Level.INFO, Level.CONFIG);
		QRLoggerUtils.classMsgMaxLength = 120;
		logger = QRLoggerUtils.getLogger(Test.class);
		AnswerSheet layout = autoDetectMode("F:\\自编资料\\小测答题卡.png");
		if (layout == null) {
			logger.warning("未检测到答题卡模型");
			return;
		}

		File inputFile = new File(INPUT_PATH);
		if (!inputFile.exists()) {
			logger.warning("文件不存在: " + INPUT_PATH);
			return;
		}

		QRFileUtils.dirCreate(OUTPUT_DIR);
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

		// ===== 1. 预处理 =====
		logger.info("===== 预处理管线 =====");
		Mat result = ImagePreprocessor.preprocess(inputFile);
		int imgW = result.cols();
		int imgH = result.rows();
		logger.info("校正后尺寸: " + imgW + " x " + imgH);

		String outPath = OUTPUT_DIR + "deskewed_" + timestamp + ".png";
		opencv_imgcodecs.imwrite(outPath, result);
		logger.info("已输出: " + outPath);

		// ===== 2. 构建答题卡布局（手动坐标模式） =====
		logger.info("===== 构建答题卡布局 =====");

		RegionDetector.calibrateByCornerMarks(result, imgW, imgH, 168, 211);

		logger.info("准考证号位数: " + layout.getExamIdDigits());
		logger.info("选择题数量: " + layout.getChoiceQuestions().size());
		logger.info("填空题数量: " + layout.getFillBlankQuestions().size());

		// ===== 3. 批改（仅选择题） =====
		logger.info("===== 批改结果 =====");
		ChoiceReader reader = new ChoiceReader(0.30);
		AnswerComparator comparator = new AnswerComparator(reader);

		GradingResult gradeResult = comparator.grade(
				result, layout,
				layout.getChoiceLabels());

		logger.info("准考证号: " + gradeResult.examineeId());
		logger.info("得分: " + gradeResult.earnedScore() + " / " + gradeResult.totalScore());

		StringBuilder answers = new StringBuilder();
		answers.append(gradeResult.examineeId()).append("\t");
		for (GradingResult.QuestionResult qr : gradeResult.questionResults()) {
			answers.append(qr.detectedAnswer()).append(" ");
		}

		logger.info(answers.toString().trim());
		logger.info("===== 完成 =====");
	}

	/**
	 * 阶段①：自动检测模式 —— 从空白模板自动推算所有坐标参数。
	 * 仅检测布局，不批改。结果打印到控制台，供手动确认或保存。
	 */
	private static AnswerSheet autoDetectMode(String filePath) {
		File templateFile = new File(filePath);  // 空白模板
		if (!templateFile.exists()) {
			logger.warning("空白模板不存在: " + templateFile.getAbsolutePath());
			logger.warning("请将未填涂的空白答题卡图片放到 ans/AnswerSheet.png");
			return null;
		}

		logger.info("===== 阶段①：自动检测空白模板布局 =====");
		logger.info("输入: " + templateFile.getAbsolutePath());

		LayoutDetector detector = LayoutDetector.detectFromTemplate(templateFile);

		// 标准答案
		String[] correctAnswers = {
				"D", "A", "B", "D", "A",  // 1-5
				"B", "D", "C", "B", "C",  // 6-10
				"C", "D", "C", "A", "B",  // 11-15
				"A", "B", "B", "D", "C",  // 16-20
				"A", "D", "B", "B", "A",  // 21-25
				"D", "A", "C", "A", "D"   // 26-30
		};

		// 构建 AnswerSheet
		return detector.buildAnswerSheet(2480, 3507, "日语答题卡", correctAnswers);
	}
}
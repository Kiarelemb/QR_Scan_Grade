package sg.qr.kiarelemb.exam.model;

import method.qr.kiarelemb.utils.QRLoggerUtils;

import java.util.*;
import java.util.logging.Logger;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className SheetLayout
 * @description TODO
 * @create 2026/5/31 14:10
 * <p>
 * 答题卡布局模型：描述一张答题卡上所有题目的位置和正确答案。
 * <p>
 * 布局可通过两种方式构建：
 * <ul>
 *   <li>手动指定坐标（适合模板固定的场景，快速验证）</li>
 *   <li>自动检测（从图像中识别表格网格，适合模板多变的场景）</li>
 * </ul>
 * <p>
 * 典型布局结构（从上到下）：
 * <ol>
 *   <li>准考证号区域 — N 位数字，每位 10 个选项框（0-9）</li>
 *   <li>选择题区域   — M 道题，每题 4 个选项框（A-D）</li>
 *   <li>填空题区域   — K 道题，每题一个手写框</li>
 * </ol>
 */
public class SheetLayout {

	private static final Logger logger = QRLoggerUtils.getLogger(SheetLayout.class);

	private final String name;
	private final int imageWidth;
	private final int imageHeight;

	/**
	 * 准考证号位数
	 */
	private final int examIdDigits;
	/**
	 * 准考证号每位数字的选项标签（0-9）
	 */
	public static final String[] DIGIT_LABELS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

	/**
	 * 选择题每题选项标签
	 */
	public static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};

	/**
	 * 所有题目（按题号排序）
	 */
	private final List<SheetQuestion> questions;

	/**
	 * 准考证号题目单独索引（方便快速访问）
	 */
	private final List<SheetQuestion> examIdQuestions;

	/**
	 * 选择题索引
	 */
	private final List<SheetQuestion> choiceQuestions;

	/**
	 * 填空题索引
	 */
	private final List<SheetQuestion> fillBlankQuestions;

	public SheetLayout(String name, int imageWidth, int imageHeight,
	                   int examIdDigits, List<SheetQuestion> questions) {
		this.name = name;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.examIdDigits = examIdDigits;
		this.questions = new ArrayList<>(questions);
		this.questions.sort(Comparator.comparingInt(SheetQuestion::number));

		this.examIdQuestions = new ArrayList<>();
		this.choiceQuestions = new ArrayList<>();
		this.fillBlankQuestions = new ArrayList<>();

		for (SheetQuestion q : questions) {
			switch (q.type()) {
				case EXAM_ID -> examIdQuestions.add(q);
				case SINGLE_CHOICE -> choiceQuestions.add(q);
				case FILL_BLANK -> fillBlankQuestions.add(q);
			}
		}
		this.examIdQuestions.sort(Comparator
				.comparingInt(SheetLayout::leftMostX)
				.thenComparingInt(SheetQuestion::number));
		this.choiceQuestions.sort(Comparator.comparingInt(SheetQuestion::number));
		this.fillBlankQuestions.sort(Comparator.comparingInt(SheetQuestion::number));
		logger.info("答题卡布局: " + name + " " + imageWidth + "x" + imageHeight + " 考号位数=" + examIdDigits + " 选择题=" + choiceQuestions.size() + " 填空题=" + fillBlankQuestions.size() + " 选项=" + getChoiceLabels().length + "种");
	}

	private static int leftMostX(SheetQuestion question) {
		if (question.optionRegions() == null || question.optionRegions().isEmpty()) {
			return Integer.MAX_VALUE;
		}
		return question.optionRegions().stream()
				.mapToInt(org.bytedeco.opencv.opencv_core.Rect::x)
				.min()
				.orElse(Integer.MAX_VALUE);
	}

	public String getName() {
		return name;
	}

	public int getImageWidth() {
		return imageWidth;
	}

	public int getImageHeight() {
		return imageHeight;
	}

	public int getExamIdDigits() {
		return examIdDigits;
	}

	public List<SheetQuestion> getQuestions() {
		return Collections.unmodifiableList(questions);
	}

	public List<SheetQuestion> getExamIdQuestions() {
		return Collections.unmodifiableList(examIdQuestions);
	}

	public List<SheetQuestion> getChoiceQuestions() {
		return Collections.unmodifiableList(choiceQuestions);
	}

	public List<SheetQuestion> getFillBlankQuestions() {
		return Collections.unmodifiableList(fillBlankQuestions);
	}

	public String[] getDigitLabels() {
		return DIGIT_LABELS;
	}

	public String[] getChoiceLabels() {
		int optionCount = choiceQuestions.stream()
				.map(SheetQuestion::optionRegions)
				.filter(Objects::nonNull)
				.mapToInt(List::size)
				.max()
				.orElse(CHOICE_LABELS.length);
		optionCount = Math.max(1, Math.min(CHOICE_LABELS.length, optionCount));
		return Arrays.copyOf(CHOICE_LABELS, optionCount);
	}
}
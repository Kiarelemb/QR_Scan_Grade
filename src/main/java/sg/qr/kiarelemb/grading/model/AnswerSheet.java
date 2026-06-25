package sg.qr.kiarelemb.grading.model;
import java.util.*;
/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className AnswerSheet
 * @description TODO
 * @create 2026/5/31 14:10
 *
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
public class AnswerSheet {

	private final String name;
	private final int imageWidth;
	private final int imageHeight;

	/** 准考证号位数 */
	private final int examIdDigits;
	/** 准考证号每位数字的选项标签（0-9） */
	public static final String[] DIGIT_LABELS = {"0","1","2","3","4","5","6","7","8","9"};

	/** 选择题每题选项标签 */
	public static final String[] CHOICE_LABELS = {"A","B","C","D"};

	/** 所有题目（按题号排序） */
	private final List<Question> questions;

	/** 准考证号题目单独索引（方便快速访问） */
	private final List<Question> examIdQuestions;

	/** 选择题索引 */
	private final List<Question> choiceQuestions;

	/** 填空题索引 */
	private final List<Question> fillBlankQuestions;

	public AnswerSheet(String name, int imageWidth, int imageHeight,
					   int examIdDigits, List<Question> questions) {
		this.name = name;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.examIdDigits = examIdDigits;
		this.questions = new ArrayList<>(questions);
		this.questions.sort(Comparator.comparingInt(Question::number));

		this.examIdQuestions = new ArrayList<>();
		this.choiceQuestions = new ArrayList<>();
		this.fillBlankQuestions = new ArrayList<>();

		for (Question q : questions) {
			switch (q.type()) {
				case EXAM_ID -> examIdQuestions.add(q);
				case SINGLE_CHOICE -> choiceQuestions.add(q);
				case FILL_BLANK -> fillBlankQuestions.add(q);
			}
		}
		this.examIdQuestions.sort(Comparator
				.comparingInt(AnswerSheet::leftMostX)
				.thenComparingInt(Question::number));
		this.choiceQuestions.sort(Comparator.comparingInt(Question::number));
		this.fillBlankQuestions.sort(Comparator.comparingInt(Question::number));
	}

	private static int leftMostX(Question question) {
		if (question.optionRegions() == null || question.optionRegions().isEmpty()) {
			return Integer.MAX_VALUE;
		}
		return question.optionRegions().stream()
				.mapToInt(org.bytedeco.opencv.opencv_core.Rect::x)
				.min()
				.orElse(Integer.MAX_VALUE);
	}

	public String getName() { return name; }
	public int getImageWidth() { return imageWidth; }
	public int getImageHeight() { return imageHeight; }
	public int getExamIdDigits() { return examIdDigits; }
	public List<Question> getQuestions() { return Collections.unmodifiableList(questions); }
	public List<Question> getExamIdQuestions() { return Collections.unmodifiableList(examIdQuestions); }
	public List<Question> getChoiceQuestions() { return Collections.unmodifiableList(choiceQuestions); }
	public List<Question> getFillBlankQuestions() { return Collections.unmodifiableList(fillBlankQuestions); }

	public String[] getDigitLabels() { return DIGIT_LABELS; }
	public String[] getChoiceLabels() {
		int optionCount = choiceQuestions.stream()
				.map(Question::optionRegions)
				.filter(Objects::nonNull)
				.mapToInt(List::size)
				.max()
				.orElse(CHOICE_LABELS.length);
		optionCount = Math.max(1, Math.min(CHOICE_LABELS.length, optionCount));
		return Arrays.copyOf(CHOICE_LABELS, optionCount);
	}
}

package sg.qr.kiarelemb.grading.model;
import java.util.*;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className GradingResult
 * @description TODO
 * @create 2026/5/31 14:10
 */
public record GradingResult(String examineeId, String sheetName, List<QuestionResult> questionResults, int totalScore,
							int earnedScore) {

	public GradingResult(String examineeId, String sheetName,
						 List<QuestionResult> questionResults,
						 int totalScore, int earnedScore) {
		this.examineeId = examineeId;
		this.sheetName = sheetName;
		this.questionResults = new ArrayList<>(questionResults);
		this.totalScore = totalScore;
		this.earnedScore = earnedScore;
	}


	@Override
	public List<QuestionResult> questionResults() {
		return Collections.unmodifiableList(questionResults);
	}


	/**
	 * 单题判定结果
	 *
	 * @param uncertain 存疑（如填涂模糊，需人工复核）
	 */
		public record QuestionResult(int questionNumber, Question.QuestionType type, String expectedAnswer,
									 String detectedAnswer, boolean correct, boolean uncertain) {
	}
}
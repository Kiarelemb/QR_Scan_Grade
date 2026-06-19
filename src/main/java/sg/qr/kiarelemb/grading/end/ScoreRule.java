package sg.qr.kiarelemb.grading.end;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScoreRule
 * @description TODO
 * @create 2026/6/18 18:32
 */
public record ScoreRule(String questionType, List<Integer> questionNumbers, BigDecimal totalScore) {
	public static ScoreRule merge(List<RawScoreRule> rawRules) {
		List<String> names = new ArrayList<>();
		Set<Integer> numbers = new LinkedHashSet<>();
		BigDecimal totalScore = BigDecimal.ZERO;
		for (RawScoreRule rawRule : rawRules) {
			if (!names.contains(rawRule.questionType())) {
				names.add(rawRule.questionType());
			}
			numbers.addAll(rawRule.questionNumbers());
			if (rawRule.totalScore() != null) {
				totalScore = rawRule.totalScore();
			}
		}
		return new ScoreRule(String.join("+", names), new ArrayList<>(numbers), totalScore);
	}
}
package sg.qr.kiarelemb.grading.end;

import sg.qr.kiarelemb.data.Utils;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScorePlan
 * @description TODO
 * @create 2026/6/12 22:13
 */
public record ScorePlan(Map<Integer, BigDecimal> questionScores, Map<Integer, String> questionSections,
						List<String> sectionNames) {
	public ScorePlan {
		questionScores = Collections.unmodifiableMap(new LinkedHashMap<>(questionScores));
		questionSections = Collections.unmodifiableMap(new LinkedHashMap<>(questionSections));
		sectionNames = Collections.unmodifiableList(new ArrayList<>(sectionNames));
	}

	public static ScorePlan empty() {
		return new ScorePlan(new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
	}

	public BigDecimal totalScore() {
		return Utils.totalScore(questionScores);
	}
}
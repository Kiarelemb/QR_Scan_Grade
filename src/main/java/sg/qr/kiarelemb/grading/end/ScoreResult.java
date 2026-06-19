package sg.qr.kiarelemb.grading.end;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScoreResult
 * @description TODO
 * @create 2026/6/12 22:11
 */
public record ScoreResult(int rank, String examineeId, String name, BigDecimal entranceEnglishScore,
						  Map<String, BigDecimal> sectionScores, BigDecimal earnedScore,
						  BigDecimal improvementScore) {
	public ScoreResult {
		sectionScores = Collections.unmodifiableMap(new LinkedHashMap<>(sectionScores));
	}
}
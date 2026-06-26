package sg.qr.kiarelemb.exam.model;

import org.bytedeco.opencv.opencv_core.Rect;

import java.math.BigDecimal;
import java.util.Objects;

public record SubjectiveAnswerRegion(String name,
									  int startQuestion,
									  int endQuestion,
									  Rect region,
									  GradingMode mode,
									  BigDecimal maxScore,
									  int pageIndex) {
	public SubjectiveAnswerRegion {
		name = name == null || name.isBlank() ? "主观题" : name.trim();
		if (startQuestion < 1) {
			startQuestion = 1;
		}
		if (endQuestion < startQuestion) {
			endQuestion = startQuestion;
		}
		Objects.requireNonNull(region, "region");
		region = new Rect(region.x(), region.y(), region.width(), region.height());
		mode = mode == null ? GradingMode.MANUAL : mode;
		maxScore = maxScore == null ? BigDecimal.ZERO : maxScore.max(BigDecimal.ZERO);
		pageIndex = Math.max(0, pageIndex);
	}

	public SubjectiveAnswerRegion(String name,
								  int startQuestion,
								  int endQuestion,
								  Rect region,
								  GradingMode mode,
								  BigDecimal maxScore) {
		this(name, startQuestion, endQuestion, region, mode, maxScore, 0);
	}

	public boolean containsQuestion(int questionNumber) {
		return questionNumber >= startQuestion && questionNumber <= endQuestion;
	}

	public enum GradingMode {
		OCR,
		MANUAL,
		MIXED
	}
}

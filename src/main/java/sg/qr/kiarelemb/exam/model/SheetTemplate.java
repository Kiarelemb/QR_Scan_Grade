package sg.qr.kiarelemb.exam.model;

import org.bytedeco.opencv.opencv_core.Rect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SheetTemplate(String name, File pictureFile, SheetLayout answerSheet,
							 Rect examRegionRect, Rect choiceRegionRect, Rect fillBlankRegionRect,
							 String defaultScoreRules,
							 int pageCount,
							 List<SubjectiveAnswerRegion> subjectiveRegions,
							 List<File> pictureFiles) {
	public SheetTemplate {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(pictureFile, "pictureFile");
		Objects.requireNonNull(answerSheet, "answerSheet");
		defaultScoreRules = defaultScoreRules == null ? "" : defaultScoreRules;
		pageCount = Math.max(1, pageCount);
		List<SubjectiveAnswerRegion> regions = new ArrayList<>();
		if (subjectiveRegions != null) {
			regions.addAll(subjectiveRegions);
		}
		if (regions.isEmpty() && fillBlankRegionRect != null) {
			regions.add(defaultSubjectiveRegion(fillBlankRegionRect, answerSheet));
		}
		subjectiveRegions = Collections.unmodifiableList(regions);
		if (fillBlankRegionRect == null && !subjectiveRegions.isEmpty()) {
			fillBlankRegionRect = subjectiveRegions.get(0).region();
		}
		List<File> images = new ArrayList<>();
		if (pictureFiles != null) {
			for (File file : pictureFiles) {
				if (file != null) {
					images.add(file);
				}
			}
		}
		if (images.isEmpty()) {
			images.add(pictureFile);
		}
		pictureFiles = Collections.unmodifiableList(images);
	}

	public SheetTemplate(String name, File pictureFile, SheetLayout answerSheet) {
		this(name, pictureFile, answerSheet, null, null, null);
	}

	public SheetTemplate(String name, File pictureFile, SheetLayout answerSheet,
						 Rect examRegionRect, Rect choiceRegionRect, Rect fillBlankRegionRect) {
		this(name, pictureFile, answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect, "");
	}

	public SheetTemplate(String name, File pictureFile, SheetLayout answerSheet,
						 Rect examRegionRect, Rect choiceRegionRect, Rect fillBlankRegionRect,
						 String defaultScoreRules) {
		this(name, pictureFile, answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect, defaultScoreRules, 1);
	}

	public SheetTemplate(String name, File pictureFile, SheetLayout answerSheet,
						 Rect examRegionRect, Rect choiceRegionRect, Rect fillBlankRegionRect,
						 String defaultScoreRules, int pageCount) {
		this(name, pictureFile, answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect,
				defaultScoreRules, pageCount, null);
	}

	public SheetTemplate(String name, File pictureFile, SheetLayout answerSheet,
						 Rect examRegionRect, Rect choiceRegionRect, Rect fillBlankRegionRect,
						 String defaultScoreRules, int pageCount,
						 List<SubjectiveAnswerRegion> subjectiveRegions) {
		this(name, pictureFile, answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect,
				defaultScoreRules, pageCount, subjectiveRegions, null);
	}

	private static SubjectiveAnswerRegion defaultSubjectiveRegion(Rect rect, SheetLayout answerSheet) {
		int start = answerSheet.getChoiceQuestions().size() + 1;
		int count = Math.max(1, answerSheet.getFillBlankQuestions().size());
		return new SubjectiveAnswerRegion("主观题1", start, start + count - 1, rect,
				SubjectiveAnswerRegion.GradingMode.OCR, java.math.BigDecimal.ZERO);
	}
}

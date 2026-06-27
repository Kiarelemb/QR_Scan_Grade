package sg.qr.kiarelemb.test;

import sg.qr.kiarelemb.exam.template.detect.TemplateLayoutDetector;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TemplateDetectionDebug {
	private TemplateDetectionDebug() {
	}

	public static void main(String[] args) throws Exception {
		File source = new File(args.length == 0 ? "res/AnswerSheets.pdf" : args[0]);
		List<File> images = DocumentPageLoader.documentImages(source);
		TemplateLayoutDetector detector = TemplateLayoutDetector.detectFromTemplate(images.get(0));
		SheetLayout sheet = detector.buildAnswerSheet(
				detector.detectedImageW,
				detector.detectedImageH,
				source.getName(),
				new String[0]
		);
		System.out.println("images=" + images);
		System.out.println("choiceQuestions=" + sheet.getChoiceQuestions().size());
		System.out.println("choiceOptionLabels=" + Arrays.toString(sheet.getChoiceLabels()));
		System.out.println("choiceRows=" + detector.choiceRows);
		System.out.println("choiceColsPerRow=" + Arrays.toString(detector.choiceColsPerRow));
		System.out.println("choiceRowStartYs=" + Arrays.toString(detector.choiceRowStartYs));
		System.out.println("choiceColStartXs=" + Arrays.toString(detector.choiceColStartXs));
		System.out.println("choiceQuestionsPerCol=" + Arrays.toString(detector.choiceQuestionsPerCol));
		System.out.println("choiceOptionCountsPerCol=" + Arrays.toString(detector.choiceOptionCountsPerCol));
		System.out.println("choiceRegion=" + detector.choiceRegionRect);
		printChoiceCandidateRows(detector);
	}

	private static void printChoiceCandidateRows(TemplateLayoutDetector detector) {
		if (detector.choiceRegionRect == null || detector.allRects == null) {
			return;
		}
		Map<Integer, List<Integer>> rows = new LinkedHashMap<>();
		for (var rect : detector.allRects) {
			boolean inChoiceRegion = rect.cx >= detector.choiceRegionRect.x()
					&& rect.cx <= detector.choiceRegionRect.x() + detector.choiceRegionRect.width()
					&& rect.cy >= detector.choiceRegionRect.y()
					&& rect.cy <= detector.choiceRegionRect.y() + detector.choiceRegionRect.height();
			if (!inChoiceRegion || rect.w > 90 || rect.h > 90 || rect.w * rect.h < 80) {
				continue;
			}
			int row = (rect.cy / 10) * 10;
			rows.computeIfAbsent(row, ignored -> new ArrayList<>()).add(rect.cx);
		}
		System.out.println("candidateRowsInChoiceRegion=");
		for (Map.Entry<Integer, List<Integer>> entry : rows.entrySet()) {
			List<Integer> xs = entry.getValue();
			if (xs.size() < 3) {
				continue;
			}
			Collections.sort(xs);
			System.out.println(entry.getKey() + ": " + xs.size() + " " + xs);
		}
	}
}

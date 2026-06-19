package sg.qr.kiarelemb.grading.pipeline;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class RegionDetector {
	private static final Logger logger = QRLoggerUtils.getLogger(RegionDetector.class);

	// 四角校准偏移量（由校准方法计算后设置）
	public static int calibrationOffsetX = 0;
	public static int calibrationOffsetY = 0;

	/**
	 * 根据四角定位方块的实际位置，计算校准偏移量。
	 * 方块尺寸 95×58，用于补偿拍照/扫描导致的 ±10~20 像素偏差。
	 *
	 * @param binary      校正后的二值图像（2480×3507）
	 * @param imgW        图像宽度
	 * @param imgH        图像高度
	 * @param expectedTLX 预期左上角方块中心X（168）
	 * @param expectedTLY 预期左上角方块中心Y（211）
	 */
	public static void calibrateByCornerMarks(Mat binary, int imgW, int imgH,
											  int expectedTLX, int expectedTLY) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary.clone(), contours, hierarchy,
				opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

		// 方块面积 95×58 = 5510，允许范围 3000~8000
		int minArea = 3000;
		int maxArea = 8000;

		List<Point> cornerCenters = new ArrayList<>();

		for (int i = 0; i < contours.size(); i++) {
			double area = opencv_imgproc.contourArea(contours.get(i));
			if (area < minArea || area > maxArea) continue;

			Rect bbox = opencv_imgproc.boundingRect(contours.get(i));
			// 方块宽高比 95/58 ≈ 1.64，允许 1.2~2.0
			double aspect = (double) bbox.width() / bbox.height();
			if (aspect < 1.2 || aspect > 2.0) continue;

			int cx = bbox.x() + bbox.width() / 2;
			int cy = bbox.y() + bbox.height() / 2;
			cornerCenters.add(new Point(cx, cy));
		}

		if (!cornerCenters.isEmpty()) {
			// 找最接近左上角的方块
			Point bestCorner = cornerCenters.get(0);
			double bestDist = Math.hypot(bestCorner.x() - expectedTLX, bestCorner.y() - expectedTLY);
			for (Point p : cornerCenters) {
				double dist = Math.hypot(p.x() - expectedTLX, p.y() - expectedTLY);
				if (dist < bestDist) {
					bestDist = dist;
					bestCorner = p;
				}
			}

			calibrationOffsetX = bestCorner.x() - expectedTLX;
			calibrationOffsetY = bestCorner.y() - expectedTLY;
		} else {
			calibrationOffsetX = 0;
			calibrationOffsetY = 0;
		}
	}


	/**
	 * 鏋勫缓鏍囧噯绛旈鍗″竷灞€锛堝噯鑰冭瘉鍙?+ 閫夋嫨棰?+ 濉┖棰橈級
	 */
	public static AnswerSheet buildExamSheet(
			int imgW, int imgH, String sheetName,
			int examIdDigits, int examStartX, int examStartY,
			int examBubbleW, int examBubbleH, int examHGap, int examVGap,
			int choiceTotal, int choiceStartX, int choiceStartY,
			int choiceBubbleW, int choiceBubbleH, int choiceHGap, int choiceVGap,
			int choiceRows, int[] choiceColsPerRow, int[] choiceRowStartYs, int[] choiceColStartXs,
			int[] choiceQuestionsPerCol,
			int fillBlankCount, int fillStartX, int fillStartY,
			int fillBoxW, int fillBoxH,
			String[] correctAnswers) {

		List<Question> allQuestions = new ArrayList<>();

		// 搴旂敤鍥涜鏍″噯鍋忕Щ
		examStartX += calibrationOffsetX;
		examStartY += calibrationOffsetY;
		fillStartX += calibrationOffsetX;
		fillStartY += calibrationOffsetY;

		// ==================== 鍑嗚€冭瘉鍙?====================
		for (int digit = 0; digit < examIdDigits; digit++) {
			List<Rect> regions = new ArrayList<>();
			for (int val = 0; val < 10; val++) {
				int x = examStartX + digit * examHGap;       // examHGap = 涓績鍒颁腑蹇冮棿璺?
				int y = examStartY + val * examVGap;         // examVGap = 涓績鍒颁腑蹇冮棿璺?
				regions.add(new Rect(x, y, examBubbleW, examBubbleH));
			}
			allQuestions.add(new Question(-(digit + 1), Question.QuestionType.EXAM_ID, regions, ""));
		}


		// ==================== 閫夋嫨棰?====================
		int questionIndex = 0;
		int colIdx = 0;

		for (int row = 0; row < choiceRows; row++) {
			int colsInRow = choiceColsPerRow[row];
			int rowY = choiceRowStartYs[row];

			for (int c = 0; c < colsInRow; c++) {
				int startX = choiceColStartXs[colIdx];
				int questionsInCol = choiceQuestionsPerCol[colIdx];

				for (int q = 0; q < questionsInCol; q++) {
					int currentY = rowY + q * choiceVGap;

					List<Rect> regions = new ArrayList<>();
					for (int opt = 0; opt < 4; opt++) {
						int x = startX + opt * choiceHGap;
						regions.add(new Rect(x, currentY, choiceBubbleW, choiceBubbleH));
					}

					String correctAnswer = (questionIndex < correctAnswers.length) ? correctAnswers[questionIndex] : "";
					allQuestions.add(new Question(questionIndex + 1, Question.QuestionType.SINGLE_CHOICE, regions, correctAnswer));
					questionIndex++;
				}
				colIdx++;
			}
		}

		// ==================== 濉┖棰?====================
		for (int i = 0; i < fillBlankCount; i++) {
			List<Rect> regions = new ArrayList<>();
			regions.add(new Rect(fillStartX, fillStartY, fillBoxW, fillBoxH));
			allQuestions.add(new Question(choiceTotal + i + 1, Question.QuestionType.FILL_BLANK, regions, ""));
		}

		return new AnswerSheet(sheetName, imgW, imgH, examIdDigits, allQuestions);
	}
}
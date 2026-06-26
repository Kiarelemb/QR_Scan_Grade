package sg.qr.kiarelemb.exam.model;
import org.bytedeco.opencv.opencv_core.Rect;
import java.util.List;

/**
 * @param optionRegions 各选项对应的矩形区域（填充题则为手写区域）
 * @param correctAnswer 正确答案（如 "C" / "3" / "あ"）
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className SheetQuestion
 * @description TODO
 * @create 2026/5/31 14:10
 */
public record SheetQuestion(int number, SheetQuestion.QuestionType type, List<Rect> optionRegions,
                             String correctAnswer) {
	public enum QuestionType {
		/**
		 * 准考证号（填涂数字0-9）
		 */
		EXAM_ID,
		/**
		 * 单选题（A/B/C/D）
		 */
		SINGLE_CHOICE,
		/**
		 * 填空题（手写区域）
		 */
		FILL_BLANK
	}
}

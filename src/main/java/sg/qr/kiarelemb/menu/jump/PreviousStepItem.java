package sg.qr.kiarelemb.menu.jump;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.SubjectiveOcrReviewPanel;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import sg.qr.kiarelemb.menu.MenuItem;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Logger;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className PreviousStepItem
 * @description 上一步按钮
 * @create 2026/6/29 12:59
 */
public class PreviousStepItem extends MenuItem {
	private static final Logger logger = QRLoggerUtils.getLogger(PreviousStepItem.class);
	public static final PreviousStepItem PREVIOUS_STEP_ITEM = new PreviousStepItem();
	private Step step = Step.NONE;
	private GradingProject project;

	public PreviousStepItem() {
		super("上一步", Keys.QUICK_KEY_PREVIOUS_STEP);
		setEnabled(false);
	}

	public static void recordChoiceReview(GradingProject project) {
		PREVIOUS_STEP_ITEM.record(Step.CHOICE_REVIEW, project);
	}

	public static void recordSubjectiveReview(GradingProject project) {
		PREVIOUS_STEP_ITEM.record(Step.SUBJECTIVE_REVIEW, project);
	}

	public static void clear() {
		PREVIOUS_STEP_ITEM.record(Step.NONE, null);
	}

	private void record(Step step, GradingProject project) {
		this.step = step == null ? Step.NONE : step;
		this.project = this.step == Step.NONE ? null : project;
		setEnabled(this.step != Step.NONE && this.project != null);
	}

	@Override
	protected void actionEvent(ActionEvent o) {
		if (step == Step.NONE || project == null) {
			clear();
			return;
		}
		Step target = step;
		GradingProject targetProject = project;
		clear();
		if (target == Step.CHOICE_REVIEW) {
			MainWindow.INSTANCE.showProjectReview(targetProject);
			return;
		}
		try {
			SheetTemplate template = SheetTemplateFileStore.load(new File(targetProject.templateFilePath()));
			MainWindow.INSTANCE.setCenterComponent(new SubjectiveOcrReviewPanel(targetProject, template));
		} catch (Exception ex) {
			logger.warning("Cannot return to subjective review: " + ex.getMessage());
			MainWindow.INSTANCE.showProjectReview(targetProject);
		}
	}

	private enum Step {
		NONE,
		CHOICE_REVIEW,
		SUBJECTIVE_REVIEW
	}
}
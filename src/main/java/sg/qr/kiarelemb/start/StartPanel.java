package sg.qr.kiarelemb.start;

import swing.qr.kiarelemb.basic.QRPanel;

import java.awt.*;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className StartPanel
 * @description TODO
 * @create 2026/6/4 12:19
 */
public class StartPanel extends QRPanel {

	public static final StartPanel START_PANEL = new StartPanel();
	private static final int BUTTON_GAP = 20;
	private static final double BUTTON_CENTER_Y_RATIO = 0.7;

	private StartPanel() {
		setLayout(new ButtonCenterLayout());
		add(NewTemplateButton.NEW_TEMPLATE_BTN);
		add(ContinueProjectButton.CONTINUE_PROJECT_BTN);
		add(ExistingTemplateButton.EXIST_TEMPLATE_BTN);
	}

	private static final class ButtonCenterLayout implements LayoutManager {

		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				Dimension buttonsSize = buttonsSize(parent);
				return new Dimension(
						insets.left + insets.right + buttonsSize.width,
						insets.top + insets.bottom + buttonsSize.height);
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				int count = parent.getComponentCount();
				Dimension buttonsSize = buttonsSize(parent);
				int availableWidth = parent.getWidth() - insets.left - insets.right;
				int availableHeight = parent.getHeight() - insets.top - insets.bottom;
				int x = insets.left + Math.max(0, (availableWidth - buttonsSize.width) / 2);
				int y = insets.top + (int) Math.round(availableHeight * BUTTON_CENTER_Y_RATIO - buttonsSize.height / 2.0);
				y = Math.max(insets.top, Math.min(y, parent.getHeight() - insets.bottom - buttonsSize.height));

				for (int i = 0; i < count; i++) {
					Component component = parent.getComponent(i);
					if (!component.isVisible()) {
						continue;
					}
					Dimension size = component.getPreferredSize();
					int componentY = y + Math.max(0, (buttonsSize.height - size.height) / 2);
					component.setBounds(x, componentY, size.width, size.height);
					x += size.width + BUTTON_GAP;
				}
			}
		}

		private static Dimension buttonsSize(Container parent) {
			int width = 0;
			int height = 0;
			int visibleCount = 0;
			for (int i = 0; i < parent.getComponentCount(); i++) {
				Component component = parent.getComponent(i);
				if (!component.isVisible()) {
					continue;
				}
				Dimension size = component.getPreferredSize();
				width += size.width;
				height = Math.max(height, size.height);
				visibleCount++;
			}
			if (visibleCount > 1) {
				width += BUTTON_GAP * (visibleCount - 1);
			}
			return new Dimension(width, height);
		}
	}
}
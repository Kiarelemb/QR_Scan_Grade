package sg.qr.kiarelemb;

import sg.qr.kiarelemb.data.BallTempData;
import sg.qr.kiarelemb.res.Info;
import method.qr.kiarelemb.utils.QRFontUtils;
import method.qr.kiarelemb.utils.QRSystemUtils;
import method.qr.kiarelemb.utils.QRTimeCountUtil;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QREmptyDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-12 22:52
 **/
public class FlashLoadingWindow extends QREmptyDialog {
    private static class TextLabel extends QRLabel {
        public TextLabel(String text) {
            super(text);
            setFont(QRColorsAndFonts.MENU_ITEM_DEFAULT_FONT.deriveFont(25f));
            setForeground(new Color(235, 235, 235));
            setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    /**
     * 启动闪屏上的小球滚动加载动画。
     * <p>
     * 7 颗绿色小球以三段式物理运动（减速 → 匀速 → 加速）从左侧依次滚入，
     * 每次到达右边界后重置循环，形成连续等待动画。
     */
    private class BallRollPane extends QRLabel {

        /** 右边界像素值：最后一个球右边缘超过此值后重置动画循环 */
        public static final int RIGHT_MARGIN = 650;
        /** 小球绘制半径（px），实际绘制为 8×8 像素的实心圆 */
        public static final int RADIUS = 8;
        private final int y;
        /** 小球数量（注意变量名 boll 为 ball 的拼写笔误） */
        private final int bollNum = 7;
        private final double[] xes = new double[this.bollNum];
        private QRTimeCountUtil tcd = null;

        public BallRollPane(int y) {
            this.y = y;

            // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
            //  Timer 刷新间隔：10 ms = 100 FPS
            //  高帧率保证 7 个小球的滚动动画足够平滑
            // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
            final int delay = 10;

            // ════════════════════════════════════════════════════════════════
            //  三段式物理运动参数（传入 BallTempData，详见 getX() 方法）
            // ════════════════════════════════════════════════════════════════
            //
            //  加速度 a = 735 px/s²
            //     Phase A (0 ~ 0.5s) : 做匀减速运动，速度从 v0 下降
            //     Phase C (1.3s ~)   : 做匀加速运动，速度重新上升
            //     数值越大，速度变化越剧烈，滚动节奏感越强
            //
            //  初速度 v0 = 530 px/s
            //     Phase A 开始时每秒移动 530 像素
            //
            //  timePartA = 0.5 s（减速段时间）
            //     速度从 v0 = 530 降至 vTmp = v0 - |a|·t = 162.5 px/s
            //
            //  timePartB = 1.3 s（匀速段结束时间点）
            //     0.5s ~ 1.3s : 匀速段，以 vTmp = 162.5 px/s 运行 0.8 秒
            //     1.3s ~      : 加速段，速度从 162.5 px/s 重新上升
            //
            //  三段效果：快 → 慢 → 匀速 → 快
            //  模拟小球滚动中先因摩擦减速、再匀速滑行、最后被"推"加速的视觉效果
            // ════════════════════════════════════════════════════════════════
            final int a = 735;
            final int v0 = 530;
            final float timePartA = 0.5f;
            final float timePartB = 1.3f;
            final BallTempData ballTempData = new BallTempData(a, v0, timePartA, timePartB);

            AtomicBoolean flag = new AtomicBoolean(false);
            AtomicReference<Timer> timer = new AtomicReference<>();
            ActionListener action = e -> {
                boolean visible = FlashLoadingWindow.this.isVisible();
                if (flag.get() && !visible) {
                    timer.get().stop();
                }
                if (visible) {
                    flag.set(true);
                    double t = this.tcd.get() / 1000D;
                    for (int j = 0; j < this.bollNum; j++) {
                        // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
                        //  小球间时间偏移：各球依次滞后 0.075 s = 75 ms
                        //  7 个球的总时间跨度 0.075 × 6 = 0.45 s
                        //  效果：一串球依次滚过，形成拖尾队列动画
                        // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
                        this.xes[j] = ballTempData.getX(t - 0.075 * j);
                    }
                    // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
                    //  右边界判定（RIGHT_MARGIN = 650 px）
                    //  当最后一颗球（索引 bollNum-1）的右边缘超过 650
                    //  时重置计时器，使动画重新从左侧开始，无限循环
                    // ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
                    if (this.xes[this.bollNum - 1] + RADIUS > RIGHT_MARGIN) {
                        this.tcd.startTimeUpdate();
                    }
                    repaint();
                    Toolkit.getDefaultToolkit().sync();
                }
            };
            timer.set(new Timer(delay, action));
            this.tcd = new QRTimeCountUtil();
            timer.get().start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            final Graphics2D graphics2D = (Graphics2D) g;
            graphics2D.setColor(QRColorsAndFonts.LIGHT_GREEN);
            for (double x : this.xes) {
                // 绘制实心圆：圆心 (x, y)，半径 RADIUS
                // y 为球的底部 Y 坐标，绘制起点为 y - RADIUS
                graphics2D.fillOval((int) x, this.y - RADIUS, RADIUS, RADIUS);
            }
        }
    }

    private final QRLabel loadingInfoLabel;
    private final QRPanel mainPanel;

    public FlashLoadingWindow() {
        super(null, false);
        setIconImage(QRSwing.windowIcon.getImage());
        setSize(550, 330);

        this.mainPanel = new QRPanel();
        //255
        this.mainPanel.setLayout(new BorderLayout());
        setContentPane(this.mainPanel);

        // ── 小球滚动动画 Y 坐标：305 px ─────────────────────────────
        // 位于闪屏背景图片上方，球心 Y=305，绘制起点 305-8=297
        QRLabel imageLabel = new BallRollPane(305);
        this.mainPanel.add(imageLabel, BorderLayout.CENTER);
//        flashImage = ImageIO.read(Objects.requireNonNull());
//
        imageLabel.setIcon(new ImageIcon(Info.FLASH_PATH));
//        imageLabel.setIcon(new ImageIcon(FLASH_PATH));
        imageLabel.setLayout(null);

        TextLabel qiLabel = new TextLabel("智能阅卷系统");
        qiLabel.setTextRight();

        this.loadingInfoLabel = new QRLabel();
        final Color foreColor = new Color(235, 235, 235);
        this.loadingInfoLabel.setVerticalAlignment(SwingConstants.BOTTOM);

        QRLabel versionLabel = new TextLabel(Info.SOFTWARE_VERSION);
        versionLabel.setForeground(foreColor);
        versionLabel.setHorizontalAlignment(SwingConstants.LEFT);
        versionLabel.setFont(QRFontUtils.getFont("Consolas", 18));


//		qiLabel.setBounds(10, 217, 150, 30);//37
//		versionLabel.setBounds(10, 190, 100, 30);
        qiLabel.setBounds(10, 20, 530, 30);//37
        versionLabel.setBounds(475, 10, 100, 30);
//        loadingInfoLabel.setBounds(10, 297, 250, 23);
        this.loadingInfoLabel.setBounds(10, 5, 450, 23);
//        loadingInfoLabel.setForeground(Color.BLACK);
        this.loadingInfoLabel.setForeground(new Color(235, 235, 235, 200));
//		loadingInfoLabel.setForeground(new Color(0, 0, 0, 90));
        this.loadingInfoLabel.setFont(QRColorsAndFonts.MENU_ITEM_DEFAULT_FONT);


        imageLabel.add(qiLabel);
        imageLabel.add(this.loadingInfoLabel);

        setCursorWait();
        setLocationRelativeTo(null);
        QRSystemUtils.setWindowNotTrans(this);
        QRSystemUtils.setWindowNotRound(this);
    }
}
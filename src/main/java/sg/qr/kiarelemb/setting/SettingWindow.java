package sg.qr.kiarelemb.setting;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.component.Tree;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.menu.type.SettingsItem;
import swing.qr.kiarelemb.assembly.QRMutableTreeNode;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.combination.QRTreeTabbedPane;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.QRComponentUtils;
import swing.qr.kiarelemb.window.basic.QRDialog;

import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-30 12:51
 **/
public class SettingWindow extends QRDialog {
    public static final SettingWindow INSTANCE = new SettingWindow();
    private boolean sure = false;


    private SettingWindow() {
        super(MainWindow.INSTANCE);
        setTitle("设置");
        setTitlePlace(QRDialog.CENTER);
        setSize(700, 470);
        //移动时，主窗体不移动
        setParentWindowNotFollowMove();
        this.mainPanel.setLayout(new BorderLayout());
        this.mainPanel.setBorder(new EmptyBorder(5, 10, 0, 10));

        QRMutableTreeNode root = new QRMutableTreeNode("设置");
        root.setCollapsable(false);
        Tree tree = new Tree(root);
        tree.setRowHeight(35);
        tree.setRootVisible(false);
        tree.setPreferredSize(new Dimension(150, 100));

        //需要记录哪些节点展开了，哪些又没展开
        tree.expendAll();

        QRTreeTabbedPane treeTabbedPane = new QRTreeTabbedPane(tree) {
            @Override
            public void componentFresh() {
                super.componentFresh();
                setBorder(new MatteBorder(0, 0, 2, 0, QRColorsAndFonts.LINE_COLOR));
            }
        };
        treeTabbedPane.setBorder(new MatteBorder(0, 0, 2, 0, QRColorsAndFonts.LINE_COLOR));

        //加入面板

        this.mainPanel.add(treeTabbedPane, BorderLayout.CENTER);

        //region 底部面板
        QRPanel bottomPanel = new QRPanel();

        QRRoundButton sureBtn = new QRRoundButton("确认") {
            @Override
            protected void actionEvent(ActionEvent o) {
                SettingWindow.this.sure = true;
                dispose();
            }
        };
        QRRoundButton cancelBtn = new QRRoundButton("取消") {
            @Override
            protected void actionEvent(ActionEvent o) {
                SettingWindow.this.sure = false;
                dispose();
            }
        };
        QRRoundButton backToDefaultBtn = new QRRoundButton("恢复默认设置并关闭") {
            @Override
            protected void actionEvent(ActionEvent o) {
                SettingsItem.CHANGE_MAP.putAll(Keys.DEFAULT_MAP);
                sureBtn.clickInvokeLater();
            }
        };

        bottomPanel.setLayout(null);
        QRComponentUtils.setBoundsAndAddToComponent(bottomPanel, sureBtn, 480, 10, 78, 30);
        QRComponentUtils.setBoundsAndAddToComponent(bottomPanel, cancelBtn, 580, 10, 78, 30);
        QRComponentUtils.setBoundsAndAddToComponent(bottomPanel, backToDefaultBtn, 5, 10, 180, 30);
        //endregion 底部面板

        bottomPanel.setPreferredSize(700, 50);
        this.mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public boolean save() {
        return this.sure;
    }
}
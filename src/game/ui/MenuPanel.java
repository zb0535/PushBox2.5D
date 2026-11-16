package game.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 시작 화면. 타이틀과 버튼 3개(시작 / 설정 / 종료)만 가진다.
 * 화면 전환은 직접 하지 않고 MainFrame이 넘겨준 동작(Runnable)만 호출한다.
 * 나중에 로고/배경 이미지가 오면 paintComponent와 버튼 스타일만 바꾸면 된다.
 */
public class MenuPanel extends JPanel {

    // 게임 이름이 정해지면 이 상수만 바꾼다
    private static final String TITLE = "SOKOBAN 2.5D";

    private static final Color BACKGROUND = new Color(30, 34, 44);
    private static final Color TITLE_COLOR = new Color(255, 214, 102);

    public MenuPanel(Runnable onStart, Runnable onSettings, Runnable onExit) {
        super(new GridBagLayout());
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(640, 480));

        JLabel title = new JLabel(TITLE, SwingConstants.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 44));
        title.setForeground(TITLE_COLOR);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 32, 0));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(8, 0, 8, 0);

        add(title, c);
        add(createMenuButton("게임 시작", onStart), c);
        add(createMenuButton("설정", onSettings), c);
        add(createMenuButton("종료", onExit), c);
    }

    /** 메뉴 버튼 공통 스타일. 모든 화면의 버튼이 같은 모양이 되도록 한 곳에서 만든다. */
    private JButton createMenuButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("Dialog", Font.BOLD, 20));
        button.setPreferredSize(new Dimension(220, 48));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        return button;
    }
}
package game.ui;

import game.model.Direction;
import game.model.GameController;
import game.model.GameView;
import game.render.IsoRenderer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 게임 화면 패널: 렌더러 호출 + 키 입력 처리 + 간단한 HUD.
 * 로직 클래스는 GameView / GameController 인터페이스로만 접근한다.
 */
public class GamePanel extends JPanel {

    private final GameView view;
    private final GameController controller;
    private final IsoRenderer renderer = new IsoRenderer();

    private final Font hudFont = new Font("SansSerif", Font.BOLD, 16);
    private final Font clearFont = new Font("SansSerif", Font.BOLD, 48);

    // paintComponent에서 매번 만들지 않도록 미리 만들어 둔 색
    private static final Color CLEAR_BOX_COLOR = new Color(0, 0, 0, 150);
    private static final Color CLEAR_TEXT_COLOR = new Color(255, 220, 80);

    // 이동 횟수 문구 캐시 (횟수가 바뀔 때만 새로 만든다)
    private int cachedMoveCount = -1;
    private String moveText = "";

    public GamePanel(GameView view, GameController controller) {
        this.view = view;
        this.controller = controller;

        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(30, 30, 40));
        setFocusable(true);

        setupKeyBindings();
    }

    /** Key Bindings 등록 (KeyListener보다 포커스 문제가 적다) */
    private void setupKeyBindings() {
        bindMove(Direction.UP,    KeyEvent.VK_UP,    KeyEvent.VK_W);
        bindMove(Direction.DOWN,  KeyEvent.VK_DOWN,  KeyEvent.VK_S);
        bindMove(Direction.LEFT,  KeyEvent.VK_LEFT,  KeyEvent.VK_A);
        bindMove(Direction.RIGHT, KeyEvent.VK_RIGHT, KeyEvent.VK_D);

        bindKey(KeyEvent.VK_Z, "undo",    true,  controller::undo);
        bindKey(KeyEvent.VK_R, "restart", false, controller::restart);
    }

    private void bindMove(Direction dir, int... keyCodes) {
        for (int code : keyCodes) {
            bindKey(code, "move_" + dir + "_" + code, true, () -> controller.move(dir));
        }
    }

    /**
     * @param lockWhenCleared true면 클리어된 뒤에는 이 키를 무시한다.
     *        (클리어 화면으로 넘어가기 전 짧은 시간에 이동/되돌리기가 먹혀 상태가 어긋나는 것을 막는 화면 쪽 처리.
     *         다시 시작(R)은 클리어 후에도 허용한다.)
     */
    private void bindKey(int keyCode, String name, boolean lockWhenCleared, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (lockWhenCleared && view.isCleared()) {
                    return;
                }
                action.run();
                repaint();   // 상태가 바뀌었으니 다시 그림
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            renderer.render(g2, view, getWidth(), getHeight());
            drawHud(g2);
        } finally {
            g2.dispose();
        }
    }

    /** 임시 HUD (나중에 HudPanel로 분리 예정) */
    private void drawHud(Graphics2D g2) {
        g2.setFont(hudFont);
        g2.setColor(Color.WHITE);
        int moves = view.getMoveCount();
        if (moves != cachedMoveCount) {
            cachedMoveCount = moves;
            moveText = "이동: " + moves;
        }
        g2.drawString(moveText, 16, 28);
        g2.drawString("방향키/WASD 이동  |  Z 되돌리기  |  R 다시 시작", 16, getHeight() - 16);

        if (view.isCleared()) {
            g2.setFont(clearFont);
            String text = "CLEAR!";
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = getHeight() / 2;
            g2.setColor(CLEAR_BOX_COLOR);
            g2.fillRoundRect(x - 24, y - fm.getAscent(), fm.stringWidth(text) + 48, fm.getHeight() + 8, 24, 24);
            g2.setColor(CLEAR_TEXT_COLOR);
            g2.drawString(text, x, y);
        }
    }
}

package game.ui;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 레벨 선택 화면. 레벨 버튼을 격자로 보여 주고, 고르면 번호(0부터)를 onSelect로 알린다.
 * 화면 전환과 레벨 로딩은 직접 하지 않고 MainFrame이 넘겨준 동작만 호출한다. (MenuPanel과 같은 방식)
 *
 * 조작: 마우스 클릭 / 방향키로 이동 + Enter 또는 Space로 선택 / ESC는 MainFrame이 처리(메뉴로 복귀).
 * 레벨이 많아지면 세로 스크롤이 생기고, 키보드로 이동하면 포커스된 버튼이 보이는 위치로 따라간다.
 */
public class LevelSelectPanel extends JPanel {

    /**
     * 레벨 목록 한 칸의 표시 정보. 임시 구조.
     * 레벨 파일 포맷/목록 형식이 정해지면 로직 쪽 타입으로 교체한다.
     * (클리어 여부·잠금 표시는 진행 저장 여부가 정해진 뒤에 필드를 추가한다.)
     */
    public record LevelInfo(String name) { }

    private static final int COLUMNS = 5;
    private static final int GAP = 12;
    // 격자 너비 = 5*104 + 4*12 = 568. 스크롤바를 뺀 640px 창(약 623px)에도 좌우 여백이 남는다.
    private static final int BUTTON_W = 104;
    private static final int BUTTON_H = 84;

    private static final Color BACKGROUND = new Color(30, 34, 44);
    private static final Color TITLE_COLOR = new Color(255, 214, 102);
    private static final Color HINT_COLOR = new Color(150, 155, 170);

    private final List<JButton> levelButtons = new ArrayList<>();
    private final JButton backButton;
    private int lastFocusedIndex = 0;   // 키보드로 버튼 영역에 다시 들어올 때 돌아갈 위치

    public LevelSelectPanel(List<LevelInfo> levels, IntConsumer onSelect, Runnable onBack) {
        super(new BorderLayout());
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(640, 480));

        JLabel title = new JLabel("레벨 선택", SwingConstants.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 36));
        title.setForeground(TITLE_COLOR);
        title.setBorder(BorderFactory.createEmptyBorder(28, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        add(createLevelArea(levels, onSelect), BorderLayout.CENTER);

        backButton = createBackButton(onBack);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 20, 0));
        bottom.add(backButton);
        add(bottom, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------
    // 화면 구성
    // ---------------------------------------------------------------

    /** 레벨 버튼 격자 + 스크롤. 레벨이 없으면 안내 문구만 보여 준다. */
    private JComponent createLevelArea(List<LevelInfo> levels, IntConsumer onSelect) {
        if (levels.isEmpty()) {
            JLabel empty = new JLabel("불러올 수 있는 레벨이 없습니다.", SwingConstants.CENTER);
            empty.setFont(new Font("Dialog", Font.PLAIN, 18));
            empty.setForeground(HINT_COLOR);
            return empty;
        }

        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, GAP, GAP));
        grid.setOpaque(false);
        for (int i = 0; i < levels.size(); i++) {
            JButton button = createLevelButton(i, levels.get(i), onSelect);
            levelButtons.add(button);
            grid.add(button);
        }

        // GridBagLayout 래퍼: 버튼이 적을 때는 가운데에 모으고, 많을 때는 스크롤된다
        JPanel wrapper = new GridHolder();
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        wrapper.add(grid);

        JScrollPane scroll = new JScrollPane(wrapper,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private JButton createLevelButton(int index, LevelInfo info, IntConsumer onSelect) {
        JButton button = new FocusRingButton(buildLabel(index + 1, info.name()));
        button.setFont(new Font("Dialog", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(BUTTON_W, BUTTON_H));
        installNavigation(button);
        button.addActionListener(e -> onSelect.accept(index));
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                lastFocusedIndex = index;
                // 스크롤 영역 밖의 버튼으로 포커스가 가면 보이는 곳까지 스크롤
                Rectangle r = new Rectangle(-GAP, -GAP, button.getWidth() + 2 * GAP, button.getHeight() + 2 * GAP);
                button.scrollRectToVisible(r);
            }
        });
        return button;
    }

    private JButton createBackButton(Runnable onBack) {
        JButton button = new FocusRingButton("뒤로");
        button.setFont(new Font("Dialog", Font.BOLD, 18));
        button.setPreferredSize(new Dimension(160, 44));
        installNavigation(button);
        button.addActionListener(e -> onBack.run());
        return button;
    }

    /** 버튼 라벨: 큰 번호 + (있으면) 레벨 이름. 이름은 HTML 특수문자를 이스케이프한다. */
    private static String buildLabel(int number, String name) {
        StringBuilder sb = new StringBuilder("<html><center><span style='font-size:20pt'>")
                .append(number)
                .append("</span>");
        if (name != null && !name.isBlank()) {
            sb.append("<br>").append(escapeHtml(name));
        }
        return sb.append("</center></html>").toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------------------------------------------------------
    // 외부에서 부르는 메서드
    // ---------------------------------------------------------------

    /**
     * 해당 번호(0부터)의 레벨 버튼에 키보드 포커스를 준다. 화면이 보인 뒤에 부를 것.
     * 범위를 벗어나면 가장 가까운 버튼, 레벨이 없으면 뒤로 버튼에 준다.
     */
    public void focusLevel(int index) {
        if (levelButtons.isEmpty()) {
            backButton.requestFocusInWindow();
            return;
        }
        int clamped = Math.max(0, Math.min(index, levelButtons.size() - 1));
        levelButtons.get(clamped).requestFocusInWindow();
    }

    // ---------------------------------------------------------------
    // 키보드 이동 (Key Bindings)
    // ---------------------------------------------------------------

    /**
     * 버튼 하나에 방향키 이동 + Enter 선택 바인딩을 다는다. (레벨 버튼과 뒤로 버튼 모두)
     * 패널(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)이 아니라 버튼 자신의 WHEN_FOCUSED에 건 이유:
     * JScrollPane이 방향키를 스크롤 동작으로 먼저 가로채서, 상위 패널에 걸면 이벤트가 도달하지 못한다.
     */
    private void installNavigation(JComponent component) {
        bindKey(component, KeyEvent.VK_LEFT,  "levelLeft",  () -> moveFocus(-1));
        bindKey(component, KeyEvent.VK_RIGHT, "levelRight", () -> moveFocus(1));
        bindKey(component, KeyEvent.VK_UP,    "levelUp",    () -> moveFocus(-COLUMNS));
        bindKey(component, KeyEvent.VK_DOWN,  "levelDown",  () -> moveFocus(COLUMNS));
        bindKey(component, KeyEvent.VK_ENTER, "levelEnter", this::clickFocused);
    }

    private static void bindKey(JComponent component, int keyCode, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** 현재 포커스된 버튼에서 delta칸 이동한다. (좌우 ±1, 상하 ±COLUMNS) */
    private void moveFocus(int delta) {
        if (levelButtons.isEmpty()) {
            return;
        }
        int count = levelButtons.size();
        int current = levelButtons.indexOf(focusOwner());

        // 뒤로 버튼 등 격자 밖에서 누르면: 위쪽/왼쪽은 마지막 위치로 돌아가고, 나머지는 무시
        if (current < 0) {
            if (delta < 0) {
                levelButtons.get(Math.min(lastFocusedIndex, count - 1)).requestFocusInWindow();
            }
            return;
        }

        int target = current + delta;
        if (delta == COLUMNS && target >= count) {
            // 바로 아래 칸이 없을 때: 아래에 줄이 더 있으면 마지막 버튼, 마지막 줄이면 뒤로 버튼
            if (current / COLUMNS < (count - 1) / COLUMNS) {
                target = count - 1;
            } else {
                backButton.requestFocusInWindow();
                return;
            }
        }
        if (target < 0 || target >= count) {
            return;   // 맨 앞/맨 위에서 더 가지 않는다
        }
        // 좌우 이동이 줄 끝에서 다음 줄로 넘어가도 자연스럽게 이어지게 둔다
        levelButtons.get(target).requestFocusInWindow();
    }

    /** Enter: 포커된 버튼(레벨 또는 뒤로)을 누른 것과 같게 처리한다. */
    private void clickFocused() {
        if (focusOwner() instanceof AbstractButton button) {
            button.doClick();
        }
    }

    private static Component focusOwner() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    /**
     * 버튼 격자를 담는 스크롤 내용 패널. 가로는 항상 뷰포트 너비에 맞춰 가로 스크롤/밀림을 막고,
     * 내용이 뷰포트보다 낮을 때는 높이도 맞춰 격자가 가운데에 오게 한다. (GridBagLayout이 가운데 정렬)
     */
    private static class GridHolder extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        GridHolder() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return BUTTON_H / 2;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height - BUTTON_H / 2;   // 한 페이지 스크롤 시 줄이 조금 겹치게
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }

    /**
     * 키보드 포커스가 있을 때 버튼 테두리에 굵은 강조선을 그리는 버튼.
     * 기본 포커스 표시(라벨 주변 점선)는 너무 약해서 끄고 이걸 쓴다.
     */
    private static class FocusRingButton extends JButton {
        private static final long serialVersionUID = 1L;
        private static final Color RING_COLOR = new Color(255, 214, 102);
        private static final BasicStroke RING_STROKE = new BasicStroke(3f);

        FocusRingButton(String text) {
            super(text);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(RING_COLOR);
                    g2.setStroke(RING_STROKE);
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                } finally {
                    g2.dispose();
                }
            }
        }
    }
}

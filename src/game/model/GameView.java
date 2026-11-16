package game.model;

import java.awt.Point;

/**
 * 프론트엔드가 게임 상태를 "읽기만" 하는 인터페이스.
 * 게임 로직 담당이 이 인터페이스를 구현한다. (MockGame은 임시 구현체)
 */
public interface GameView {

    /** 맵 가로 칸 수 */
    int getWidth();

    /** 맵 세로 칸 수 */
    int getHeight();

    /** (x, y) 칸의 타일 종류. 범위를 벗어나면 EMPTY를 반환해야 한다. */
    TileType getTile(int x, int y);

    /** (x, y) 칸에 박스가 있는지 여부. 범위를 벗어나면 false. */
    boolean hasBox(int x, int y);

    /** 플레이어의 현재 격자 좌표 */
    Point getPlayerPos();

    /** 지금까지 이동한 횟수 */
    int getMoveCount();

    /** 모든 목표 지점에 박스가 놓였는지 여부 */
    boolean isCleared();
}
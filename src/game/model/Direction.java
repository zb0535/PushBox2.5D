package game.model;

/** 이동 방향. dx, dy는 격자 좌표 기준 이동량 (x: 오른쪽 +, y: 아래쪽 +) */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }
}
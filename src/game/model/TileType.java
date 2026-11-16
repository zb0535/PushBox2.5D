package game.model;

/** 바닥 타일 종류. 박스와 플레이어는 타일이 아니라 GameView에서 따로 조회한다. */
public enum TileType {
    EMPTY,  // 맵 바깥 (아무것도 그리지 않음)
    FLOOR,  // 일반 바닥
    WALL,   // 벽
    GOAL    // 박스를 놓아야 하는 목표 지점
}
package com.assassin.model;

/**
 * Interface for game events that trigger cache invalidation
 */
public interface GameEvent {
    String getType();
    String getGameId();
    String getPlayerId();
    String getVictimId();
    String getKillerId();
    String getHunterId();
    String getTargetId();
    Object getLocationData();
    Object getInitialLeaderboard();
}
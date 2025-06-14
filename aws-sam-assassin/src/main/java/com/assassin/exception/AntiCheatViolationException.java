package com.assassin.exception;

import com.assassin.service.AntiCheatService.CheatType;

/**
 * Exception thrown when anti-cheat violations are detected.
 * Provides detailed information about the violation type and severity.
 */
public class AntiCheatViolationException extends Exception {
    
    private final CheatType cheatType;
    private final int severity;
    private final String playerId;
    
    public AntiCheatViolationException(String message, CheatType cheatType, int severity, String playerId) {
        super(message);
        this.cheatType = cheatType;
        this.severity = severity;
        this.playerId = playerId;
    }
    
    public AntiCheatViolationException(String message, CheatType cheatType, int severity, String playerId, Throwable cause) {
        super(message, cause);
        this.cheatType = cheatType;
        this.severity = severity;
        this.playerId = playerId;
    }
    
    public CheatType getCheatType() {
        return cheatType;
    }
    
    public int getSeverity() {
        return severity;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    @Override
    public String toString() {
        return String.format("AntiCheatViolationException{cheatType=%s, severity=%d, playerId='%s', message='%s'}", 
                           cheatType, severity, playerId, getMessage());
    }
}
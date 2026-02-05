package com.hockey.bot.models;

public class PeriodInfo {
    private int currentPeriod; // 1, 2, 3, 4 (овертайм)
    private String currentPeriodTime; // "12:34" время текущего периода
    private String matchStatus; // "Идет", "Перерыв", "Завершен"

    // Геттеры и сеттеры
    public int getCurrentPeriod() { return currentPeriod; }
    public void setCurrentPeriod(int currentPeriod) { this.currentPeriod = currentPeriod; }

    public String getCurrentPeriodTime() { return currentPeriodTime; }
    public void setCurrentPeriodTime(String currentPeriodTime) { this.currentPeriodTime = currentPeriodTime; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    @Override
    public String toString() {
        return String.format("Период: %d, Время: %s, Статус: %s",
                currentPeriod, currentPeriodTime != null ? currentPeriodTime : "Н/Д", matchStatus);
    }
}
package com.hockey.bot.models;

import java.time.LocalDateTime;

public class MatchInfo {
    private String matchId;
    private String homeTeam;
    private String awayTeam;
    private LocalDateTime matchTime;
    private String status;
    private int homeScore;
    private int awayScore;
    private String periodScores;
    private String tournament;

    // Геттеры и сеттеры
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public LocalDateTime getMatchTime() { return matchTime; }
    public void setMatchTime(LocalDateTime matchTime) { this.matchTime = matchTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getHomeScore() { return homeScore; }
    public void setHomeScore(int homeScore) { this.homeScore = homeScore; }

    public int getAwayScore() { return awayScore; }
    public void setAwayScore(int awayScore) { this.awayScore = awayScore; }

    public String getPeriodScores() { return periodScores; }
    public void setPeriodScores(String periodScores) { this.periodScores = periodScores; }

    public String getTournament() { return tournament; }
    public void setTournament(String tournament) { this.tournament = tournament; }

    @Override
    public String toString() {
        return String.format("%s - %s | %d:%d | %s | Периоды: %s",
                homeTeam, awayTeam, homeScore, awayScore, status, periodScores);
    }
}
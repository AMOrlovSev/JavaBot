package com.hockey.bot.utils;

import com.hockey.bot.models.MatchInfo;
import com.hockey.bot.models.PeriodInfo;
import com.hockey.bot.models.PeriodStats;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SimpleParser {
    // Те же тестовые методы, что были раньше
    public static List<MatchInfo> parseTodayMatches() throws IOException {
        // Возвращаем тестовые матчи
        List<MatchInfo> matches = new ArrayList<>();
        String[] testMatches = {
                "1184703,Icebreakers,Dream Chasers,Live,4,6,3HL SOUTH",
                "1184998,City devils,Black angels,Live,2,1,3HL NORTH",
                "1185396,Wild Vikings,Rangers,Scheduled,0,0,MNHL2x2 A",
                "1185690,Thunderheads,Blood Razors,Scheduled,0,0,MNHL2x2 B"
        };

        for (String testMatch : testMatches) {
            String[] parts = testMatch.split(",");
            if (parts.length >= 6) {
                MatchInfo match = new MatchInfo();
                match.setMatchId(parts[0]);
                match.setHomeTeam(parts[1]);
                match.setAwayTeam(parts[2]);
                match.setStatus(parts[3]);
                match.setHomeScore(Integer.parseInt(parts[4]));
                match.setAwayScore(Integer.parseInt(parts[5]));
                match.setMatchTime(LocalDateTime.now());

                if (parts.length > 6) {
                    match.setTournament(parts[6]);
                }

                matches.add(match);
            }
        }

        return matches;
    }

    public static PeriodStats getPeriodStats(String matchId) throws IOException {
        // Тестовые данные
        if (matchId.equals("1184703")) return new PeriodStats("(4:6)");
        if (matchId.equals("1184998")) return new PeriodStats("(2:1)");
        return new PeriodStats("(2:1)");
    }

    public static PeriodInfo getPeriodInfo(String matchId) throws IOException {
        // Тестовые данные
        PeriodInfo periodInfo = new PeriodInfo();

        // Для тестового матча 1184998 возвращаем, что это 2-й период (первый завершен)
        if (matchId.equals("1184998")) {
            periodInfo.setCurrentPeriod(2);
            periodInfo.setCurrentPeriodTime("05:30");
            periodInfo.setMatchStatus("Идет");
        }
        // Для тестового матча 1184703 возвращаем, что это 1-й период (еще не завершен)
        else if (matchId.equals("1184703")) {
            periodInfo.setCurrentPeriod(1);
            periodInfo.setCurrentPeriodTime("18:45");
            periodInfo.setMatchStatus("Идет");
        }
        // Для остальных - по умолчанию 1-й период
        else {
            periodInfo.setCurrentPeriod(1);
            periodInfo.setCurrentPeriodTime("00:00");
            periodInfo.setMatchStatus("Идет");
        }

        return periodInfo;
    }

    public static void debugPageStructure() throws IOException {
        System.out.println("Простой парсер - debug не доступен");
    }

    public static void analyzeMatchPage(String matchId) throws IOException {
        System.out.println("Простой парсер - анализ не доступен");
    }
}
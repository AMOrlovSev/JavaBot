package com.hockey.bot.utils;

import com.hockey.bot.models.MatchInfo;
import com.hockey.bot.models.PeriodStats;
import java.io.IOException;
import java.util.List;

public class ParserUtils {
    // Флаг для выбора парсера
    private static boolean useSelenium = true;

    public static List<MatchInfo> parseTodayMatches() throws IOException {
        if (useSelenium) {
            try {
                return SeleniumParser.parseTodayMatches();
            } catch (Exception e) {
                System.err.println("Selenium парсинг не удался: " + e.getMessage());
                return SimpleParser.parseTodayMatches();
            }
        } else {
            return SimpleParser.parseTodayMatches();
        }
    }

    public static PeriodStats getPeriodStats(String matchId) throws IOException {
        if (useSelenium) {
            try {
                return SeleniumParser.getPeriodStats(matchId);
            } catch (Exception e) {
                System.err.println("Selenium не удалось получить статистику: " + e.getMessage());
                return SimpleParser.getPeriodStats(matchId);
            }
        } else {
            return SimpleParser.getPeriodStats(matchId);
        }
    }

    public static void debugPageStructure() throws IOException {
        if (useSelenium) {
            SeleniumParser.debugPageStructure();
        } else {
            SimpleParser.debugPageStructure();
        }
    }

    public static void analyzeMatchPage(String matchId) throws IOException {
        if (useSelenium) {
            SeleniumParser.analyzeMatchPage(matchId);
        } else {
            SimpleParser.analyzeMatchPage(matchId);
        }
    }

    // Инициализация/закрытие Selenium
    public static void initSelenium() {
        SeleniumParser.initialize();
    }

    public static void closeSelenium() {
        SeleniumParser.close();
    }

    // Переключение между парсерами
    public static void setUseSelenium(boolean use) {
        useSelenium = use;
    }
}
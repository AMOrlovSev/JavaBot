package com.hockey.bot.models;

public class PeriodStats {
    private int firstPeriodHomeGoals = -1;
    private int firstPeriodAwayGoals = -1;

    public PeriodStats(String periodScores) {
        parsePeriodScores(periodScores);
    }

    private void parsePeriodScores(String periodScores) {
        if (periodScores != null && !periodScores.isEmpty()) {
            try {
                System.out.println("Парсинг: " + periodScores);

                // Убираем скобки
                String clean = periodScores.replace("(", "").replace(")", "").trim();

                // Разные форматы: "2:1", "2-1", "2-1 | 0-0 | 0-0"
                if (clean.contains("|")) {
                    // Несколько периодов
                    String[] periods = clean.split("\\|");
                    if (periods.length >= 1) {
                        parseScore(periods[0].trim());
                    }
                } else {
                    // Один период
                    parseScore(clean);
                }

            } catch (Exception e) {
                System.err.println("Ошибка парсинга: " + periodScores + " - " + e.getMessage());
            }
        }
    }

    private void parseScore(String scoreStr) {
        // Поддерживаем ":", "-" и другие разделители
        String[] parts = scoreStr.replace(":", "-").replace("–", "-").split("-");

        if (parts.length == 2) {
            try {
                this.firstPeriodHomeGoals = Integer.parseInt(parts[0].trim());
                this.firstPeriodAwayGoals = Integer.parseInt(parts[1].trim());
                System.out.println("Распарсено: " + firstPeriodHomeGoals + ":" + firstPeriodAwayGoals);
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат счета: " + scoreStr);
            }
        }
    }

    public boolean isFirstPeriodLessThanThree() {
        int total = getFirstPeriodTotalGoals();
        return total != -1 && total < 3;
    }

    public int getFirstPeriodTotalGoals() {
        if (firstPeriodHomeGoals == -1 || firstPeriodAwayGoals == -1) return -1;
        return firstPeriodHomeGoals + firstPeriodAwayGoals;
    }

    public int getFirstPeriodHomeGoals() { return firstPeriodHomeGoals; }
    public int getFirstPeriodAwayGoals() { return firstPeriodAwayGoals; }
}
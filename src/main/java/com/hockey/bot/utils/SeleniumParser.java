package com.hockey.bot.utils;

import com.hockey.bot.models.MatchInfo;
import com.hockey.bot.models.PeriodInfo;
import com.hockey.bot.models.PeriodStats;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SeleniumParser {
    private static final String BASE_URL = "https://sportregion2020.ru/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static WebDriver driver;

    public static void initialize() {
        // Автоматически скачиваем и настраиваем драйвер Chrome
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // Настройки для headless режима (без GUI)
        options.addArguments("--headless=new"); // Новый headless режим
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Скрываем автоматизацию
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(options);

        // Скрываем WebDriver - приводим driver к JavascriptExecutor
        if (driver instanceof JavascriptExecutor) {
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );
        }

        System.out.println("✅ Selenium драйвер инициализирован");
    }

    public static void close() {
        if (driver != null) {
            driver.quit();
            System.out.println("✅ Selenium драйвер закрыт");
        }
    }

    public static List<MatchInfo> parseTodayMatches() {
        if (driver == null) {
            initialize();
        }

        List<MatchInfo> matches = new ArrayList<>();
        System.out.println("🔄 Парсинг через Selenium: " + BASE_URL);

        try {
            // Загружаем страницу
            driver.get(BASE_URL);

            // Ждем загрузки страницы
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Ждем загрузки табов
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".nav-tabs")));

            // Даем время для загрузки контента
            Thread.sleep(3000);

            // Используем JavaScript для клика на вкладки вместо Selenium click()
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Ищем все вкладки
            List<WebElement> tabButtons = driver.findElements(By.cssSelector(".nav-tabs .nav-link"));

            // Проходим по всем вкладкам
            for (WebElement tabButton : tabButtons) {
                try {
                    String tabText = tabButton.getText().trim();
                    String tabId = tabButton.getAttribute("id");
                    String ariaControls = tabButton.getAttribute("aria-controls");

                    System.out.println("Проверяем вкладку: '" + tabText + "' (id: " + tabId + ", aria-controls: " + ariaControls + ")");

                    // Проверяем, является ли вкладка 3HL лигой
                    boolean is3hlSouth = tabText.contains("3HL SOUTH") ||
                            (tabId != null && tabId.contains("3HL_South")) ||
                            (ariaControls != null && ariaControls.contains("3HL_South"));

                    boolean is3hlNorth = tabText.contains("3HL NORTH") ||
                            (tabId != null && tabId.contains("3HL_North")) ||
                            (ariaControls != null && ariaControls.contains("3HL_North"));

                    if (is3hlSouth || is3hlNorth) {
                        String leagueName = is3hlSouth ? "3HL SOUTH" : "3HL NORTH";
                        System.out.println("Обработка лиги: " + leagueName);

                        // Активируем вкладку через JavaScript
                        js.executeScript("arguments[0].click();", tabButton);
                        Thread.sleep(2000); // Ждем загрузки контента

                        // Находим соответствующую tab-pane
                        if (ariaControls != null && !ariaControls.isEmpty()) {
                            WebElement tabPane = driver.findElement(By.id(ariaControls));
                            if (tabPane != null) {
                                List<MatchInfo> leagueMatches = parseMatchesFromTabPane(tabPane, leagueName);
                                matches.addAll(leagueMatches);
                                System.out.println("Добавлено матчей из " + leagueName + ": " + leagueMatches.size());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ Ошибка обработки вкладки: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга через Selenium: " + e.getMessage());
            e.printStackTrace();

            // В случае ошибки, используем тестовые данные
            matches = getTestMatches();
        }

        System.out.println("Всего найдено матчей в 3HL лигах: " + matches.size());
        return matches;
    }

    private static List<MatchInfo> parseMatchesFromTabPane(WebElement tabPane, String leagueName) {
        List<MatchInfo> matches = new ArrayList<>();

        try {
            // Ищем все ссылки на матчи внутри вкладки
            List<WebElement> matchLinks = tabPane.findElements(By.cssSelector("a[href]"));
            System.out.println("Найдено потенциальных матчей в лиге " + leagueName + ": " + matchLinks.size());

            for (WebElement matchLink : matchLinks) {
                try {
                    String href = matchLink.getAttribute("href");
                    if (href == null || href.isEmpty()) {
                        continue;
                    }

                    // Извлекаем ID матча из ссылки
                    String matchId = extractMatchIdFromHref(href);
                    if (matchId == null || matchId.isEmpty()) {
                        continue;
                    }

                    // Парсим матч
                    MatchInfo match = parseMatchElement(matchLink, matchId);
                    if (match != null) {
                        match.setTournament(leagueName);
                        matches.add(match);
                        System.out.println("  ✅ " + match.getHomeTeam() + " vs " +
                                match.getAwayTeam() + " [" + match.getStatus() + "] ID: " + matchId);
                    }
                } catch (Exception e) {
                    System.err.println("  ❌ Ошибка парсинга матча: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга вкладки " + leagueName + ": " + e.getMessage());
        }

        return matches;
    }

    private static String extractMatchIdFromHref(String href) {
        if (href == null || href.isEmpty()) return null;

        try {
            // Ищем цифры в URL
            Pattern pattern = Pattern.compile("/(\\d+)(?:/|$)");
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                return matcher.group(1);
            }

            // Альтернативный способ: извлекаем последний сегмент
            String[] parts = href.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (parts[i].matches("\\d+")) {
                    return parts[i];
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка извлечения ID из href: " + href);
        }

        return null;
    }

    private static MatchInfo parseMatchElement(WebElement matchElement, String matchId) {
        try {
            MatchInfo match = new MatchInfo();
            match.setMatchId(matchId);

            // Находим контейнер матча
            WebElement matchContainer;
            try {
                matchContainer = matchElement.findElement(By.xpath(".//div[contains(@class, 'matches') or contains(@class, 'match') or contains(@class, 'game')]"));
            } catch (Exception e) {
                matchContainer = matchElement;
            }

            String containerText = matchContainer.getText().trim();
            if (containerText.isEmpty() || containerText.length() < 10) {
                // Недостаточно информации в элементе
                return null;
            }

            // Парсим команды
            try {
                // Попробуем найти названия команд по классам
                List<WebElement> teamElements = matchContainer.findElements(By.cssSelector(".homeText, .awayText, .team-name, .team"));
                if (teamElements.size() >= 2) {
                    match.setHomeTeam(teamElements.get(0).getText().trim());
                    match.setAwayTeam(teamElements.get(1).getText().trim());
                } else {
                    // Альтернативный парсинг из текста
                    String[] lines = containerText.split("\n");
                    if (lines.length >= 2) {
                        match.setHomeTeam(lines[0].trim());
                        match.setAwayTeam(lines[1].trim());
                    }
                }
            } catch (Exception e) {
                System.err.println("Не удалось найти команды для матча " + matchId);
                return null;
            }

            // Парсим статус
            try {
                if (containerText.contains("Live") || containerText.contains("LIVE")) {
                    match.setStatus("Live");
                } else if (containerText.contains("Завершен") || containerText.contains("завершен")) {
                    match.setStatus("Завершен");
                } else if (containerText.contains("Предстоящий") || containerText.contains("предстоящий")) {
                    match.setStatus("Scheduled");
                } else {
                    match.setStatus("Unknown");
                }
            } catch (Exception e) {
                match.setStatus("Unknown");
            }

            // Парсим время матча
            try {
                Pattern timePattern = Pattern.compile("\\b\\d{2}:\\d{2}\\b");
                Matcher timeMatcher = timePattern.matcher(containerText);
                if (timeMatcher.find()) {
                    String timeStr = timeMatcher.group();
                    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    try {
                        LocalDateTime matchTime = LocalDateTime.parse(today + " " + timeStr, DATE_FORMATTER);
                        match.setMatchTime(matchTime);
                    } catch (Exception e) {
                        match.setMatchTime(LocalDateTime.now());
                    }
                } else {
                    match.setMatchTime(LocalDateTime.now());
                }
            } catch (Exception e) {
                match.setMatchTime(LocalDateTime.now());
            }

            // Парсим счет
            try {
                Pattern scorePattern = Pattern.compile("(\\d+)\\s*[-:]\\s*(\\d+)");
                Matcher scoreMatcher = scorePattern.matcher(containerText);
                if (scoreMatcher.find()) {
                    match.setHomeScore(Integer.parseInt(scoreMatcher.group(1)));
                    match.setAwayScore(Integer.parseInt(scoreMatcher.group(2)));
                } else {
                    match.setHomeScore(0);
                    match.setAwayScore(0);
                }
            } catch (Exception e) {
                match.setHomeScore(0);
                match.setAwayScore(0);
            }

            // Проверяем валидность матча
            if (match.getHomeTeam() == null || match.getHomeTeam().isEmpty() ||
                    match.getAwayTeam() == null || match.getAwayTeam().isEmpty() ||
                    match.getHomeTeam().equals(match.getAwayTeam())) {
                return null;
            }

            return match;

        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга элемента матча " + matchId + ": " + e.getMessage());
            return null;
        }
    }

    public static PeriodInfo getPeriodInfo(String matchId) {
        if (driver == null) {
            initialize();
        }

        System.out.println("🔄 Получение информации о периоде для матча: " + matchId);

        PeriodInfo periodInfo = new PeriodInfo();

        try {
            String matchUrl = BASE_URL + matchId;
            driver.get(matchUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body")));

            Thread.sleep(2000);

            // Получаем весь текст страницы
            String pageText = driver.findElement(By.tagName("body")).getText();

            // 1. Определяем статус матча
            String matchStatus = determineMatchStatus(pageText);
            periodInfo.setMatchStatus(matchStatus);

            // 2. Определяем текущий период
            int currentPeriod = determineCurrentPeriod(pageText, matchStatus);
            periodInfo.setCurrentPeriod(currentPeriod);

            // 3. Определяем, завершен ли текущий период (ищем "Конец периода" или время 7:00)
            boolean isCurrentPeriodEnded = isPeriodEnded(pageText, currentPeriod);

            // 4. Если текущий период завершен и матч идет, значит следующий период
            if (isCurrentPeriodEnded && "Идет".equals(matchStatus)) {
                if (currentPeriod < 3) {
                    periodInfo.setCurrentPeriod(currentPeriod + 1);
                    System.out.println("✓ Период " + currentPeriod + " завершен, переходим к периоду " + (currentPeriod + 1));
                } else {
                    // Если 3-й период завершен, матч должен быть завершен
                    periodInfo.setMatchStatus("Завершен");
                    System.out.println("✓ Период 3 завершен, матч завершен");
                }
            }

            // 5. Ищем время текущего периода
            if ("Идет".equals(matchStatus) || "Перерыв".equals(matchStatus)) {
                String periodTime = findCurrentPeriodTime(pageText, currentPeriod);
                periodInfo.setCurrentPeriodTime(periodTime);
            }

            System.out.println("✓ Итог: период=" + periodInfo.getCurrentPeriod() +
                    ", статус=" + periodInfo.getMatchStatus() +
                    ", время=" + periodInfo.getCurrentPeriodTime());

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения информации о периоде: " + e.getMessage());
            e.printStackTrace();
            // Возвращаем значения по умолчанию
            periodInfo.setCurrentPeriod(1);
            periodInfo.setMatchStatus("Идет");
        }

        return periodInfo;
    }

    private static String determineMatchStatus(String pageText) {
        if (pageText.contains("Завершен") || pageText.contains("завершен")) {
            return "Завершен";
        } else if (pageText.contains("Перерыв") || pageText.contains("перерыв")) {
            return "Перерыв";
        } else if (pageText.contains("Live") || pageText.contains("LIVE") || pageText.contains("Идет")) {
            return "Идет";
        } else if (pageText.contains("Предстоящий") || pageText.contains("предстоящий")) {
            return "Предстоящий";
        }
        return "Неизвестно";
    }

    private static int determineCurrentPeriod(String pageText, String matchStatus) {
        // Если матч завершен, возвращаем 3
        if ("Завершен".equals(matchStatus)) {
            return 3;
        }

        // Если матч предстоящий, возвращаем 0
        if ("Предстоящий".equals(matchStatus)) {
            return 0;
        }

        // 1. Ищем явное указание периода
        Pattern periodPattern = Pattern.compile("(\\d+)\\s*(?:й|й пер|пер|период)", Pattern.CASE_INSENSITIVE);
        Matcher periodMatcher = periodPattern.matcher(pageText);

        int explicitPeriod = 0;
        while (periodMatcher.find()) {
            try {
                int period = Integer.parseInt(periodMatcher.group(1));
                if (period > explicitPeriod) {
                    explicitPeriod = period;
                }
            } catch (Exception e) {}
        }

        if (explicitPeriod > 0) {
            return explicitPeriod;
        }

        // 2. Анализируем счет периодов
        int periodFromScore = analyzePeriodScores(pageText);
        if (periodFromScore > 0) {
            return periodFromScore;
        }

        // 3. По умолчанию для Live матчей - первый период
        return 1;
    }

    private static int analyzePeriodScores(String pageText) {
        // Формат: "1-0 | 0-0 | 0-0"
        Pattern fullPattern = Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
        Matcher matcher = fullPattern.matcher(pageText);

        if (matcher.find()) {
            // Если найден полный формат с 3 периодами, проверяем какой период сыгран
            int period1Home = Integer.parseInt(matcher.group(1));
            int period1Away = Integer.parseInt(matcher.group(2));
            int period2Home = Integer.parseInt(matcher.group(3));
            int period2Away = Integer.parseInt(matcher.group(4));
            int period3Home = Integer.parseInt(matcher.group(5));
            int period3Away = Integer.parseInt(matcher.group(6));

            // Проверяем, есть ли голы в третьем периоде
            if (period3Home > 0 || period3Away > 0) {
                return 3; // Третий период сыгран (матч завершен)
            }
            // Проверяем, есть ли голы во втором периоде
            else if (period2Home > 0 || period2Away > 0) {
                return 2; // Второй период сыгран
            }
            // Есть голы в первом периоде
            else if (period1Home > 0 || period1Away > 0) {
                return 1; // Первый период сыгран
            }
        }

        return 0;
    }

    private static boolean isPeriodEnded(String pageText, int periodNumber) {
        if (periodNumber <= 0 || periodNumber > 3) {
            return false;
        }

        System.out.println("🔍 Проверяем завершенность периода " + periodNumber);

        // 1. Ищем индикатор "Конец периода" для конкретного периода
        Pattern endPattern = Pattern.compile(
                "Конец\\s*(?:периода\\s*)?\\s*" + periodNumber +
                        "|" + periodNumber + "\\s*(?:й|й пер|пер|период)\\s*завершен" +
                        "|После\\s*" + periodNumber + "\\s*(?:го|го|й|)\\s*периода" +
                        "|Перерыв\\s*после\\s*" + periodNumber +
                        "|" + periodNumber + "\\s*пер(?:иод)?\\s*закончен",
                Pattern.CASE_INSENSITIVE
        );

        if (endPattern.matcher(pageText).find()) {
            System.out.println("✓ Найден индикатор завершения периода " + periodNumber);
            return true;
        }

        // 2. Ищем время периода и проверяем, равно ли оно 7:00 или больше
        Pattern timePattern = Pattern.compile(
                periodNumber + "\\s*(?:й|й пер|пер|период).*?(\\d{1,2}):(\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher timeMatcher = timePattern.matcher(pageText);
        if (timeMatcher.find()) {
            try {
                int minutes = Integer.parseInt(timeMatcher.group(1));
                int seconds = Integer.parseInt(timeMatcher.group(2));

                // Период длится 7 минут, считаем завершенным если время >= 7:00
                if (minutes >= 7) {
                    System.out.println("✓ Период " + periodNumber + " завершен (время: " + minutes + ":" + seconds + ")");
                    return true;
                } else if (minutes == 6 && seconds >= 30) {
                    // Если 6:30+, считаем что почти завершен
                    System.out.println("⚠ Период " + periodNumber + " почти завершен (время: " + minutes + ":" + seconds + ")");
                    return true;
                } else {
                    System.out.println("✓ Период " + periodNumber + " еще идет (время: " + minutes + ":" + seconds + ")");
                    return false;
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка парсинга времени периода: " + e.getMessage());
            }
        }

        // 3. Если период явно указан как текущий, но нет информации о завершении - считаем что не завершен
        Pattern currentPeriodPattern = Pattern.compile(
                periodNumber + "\\s*(?:й|й пер|пер|период).*?(?:текущий|сейчас|идет)",
                Pattern.CASE_INSENSITIVE
        );

        if (currentPeriodPattern.matcher(pageText).find()) {
            System.out.println("✓ Период " + periodNumber + " указан как текущий и идет");
            return false;
        }

        // 4. По умолчанию считаем период не завершенным
        System.out.println("⚠ Не найдено информации о завершении периода " + periodNumber + ", считаем что идет");
        return false;
    }

    private static String findCurrentPeriodTime(String pageText, int currentPeriod) {
        if (currentPeriod <= 0) {
            return null;
        }

        System.out.println("🔍 Ищем время для периода " + currentPeriod);

        // 1. Ищем время, связанное с текущим периодом
        Pattern pattern = Pattern.compile(
                currentPeriod + "\\s*(?:й|й пер|пер|период).*?(\\d{1,2}):(\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(pageText);
        if (matcher.find()) {
            String time = matcher.group(1) + ":" + matcher.group(2);
            // Проверяем, что это валидное время периода (0-7 минут)
            if (isValidPeriodTime(time)) {
                System.out.println("✓ Найдено время для периода " + currentPeriod + ": " + time);
                return time;
            }
        }

        // 2. Ищем любой таймер в формате XX:XX или X:XX
        pattern = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
        matcher = pattern.matcher(pageText);

        while (matcher.find()) {
            String time = matcher.group();
            // Проверяем, что это время периода (0-7 минут) и не время начала матча
            if (isValidPeriodTime(time) && !isLikelyMatchTime(time)) {
                System.out.println("✓ Найдено подходящее время: " + time);
                return time;
            }
        }

        System.out.println("✗ Время периода не найдено");
        return null;
    }

    private static boolean isValidPeriodTime(String time) {
        try {
            String[] parts = time.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            // Время периода хоккея 3HL: 00:00 - 07:00 (7 минут)
            return minutes >= 0 && minutes <= 7 && seconds >= 0 && seconds <= 59;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLikelyMatchTime(String time) {
        // Проверяем, не является ли это временем начала матча
        try {
            String[] parts = time.split(":");
            int minutes = Integer.parseInt(parts[0]);
            // Время начала матчей обычно вечером (18:00, 19:00, 20:00 и т.д.)
            return minutes >= 18 && minutes <= 23;
        } catch (Exception e) {
            return false;
        }
    }

    public static PeriodStats getPeriodStats(String matchId) {
        if (driver == null) {
            initialize();
        }

        System.out.println("🔄 Получение статистики периодов для матча: " + matchId);

        try {
            String matchUrl = BASE_URL + matchId;
            driver.get(matchUrl);

            // Ждем загрузки страницы
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body")));

            // Даем время для загрузки
            Thread.sleep(2000);

            // Получаем весь текст страницы
            String pageText = driver.findElement(By.tagName("body")).getText();

            // Ищем счет периодов в формате: "1:1 | 0:0 | 0:0" или "1-1 | 0-0 | 0-0"
            Pattern pattern = Pattern.compile(
                    "(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)"
            );

            Matcher matcher = pattern.matcher(pageText);
            if (matcher.find()) {
                System.out.println("Найден счет периодов: " + matcher.group(0));
                // Возвращаем счет первого периода
                String firstPeriodScore = matcher.group(1) + ":" + matcher.group(2);
                return new PeriodStats("(" + firstPeriodScore + ")");
            }

            // Ищем счет 2 периодов
            pattern = Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
            matcher = pattern.matcher(pageText);
            if (matcher.find()) {
                System.out.println("Найден счет 2 периодов: " + matcher.group(0));
                String firstPeriodScore = matcher.group(1) + ":" + matcher.group(2);
                return new PeriodStats("(" + firstPeriodScore + ")");
            }

            // Ищем любой счет
            pattern = Pattern.compile("(\\d+)[:-](\\d+)");
            matcher = pattern.matcher(pageText);
            if (matcher.find()) {
                String score = matcher.group(0);
                System.out.println("Найден счет: " + score);
                return new PeriodStats("(" + score + ")");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения статистики: " + e.getMessage());
        }

        // Если не удалось получить данные, возвращаем дефолтные
        System.out.println("Возвращаю дефолтные данные для матча " + matchId);
        return new PeriodStats("(0:0)");
    }

    public static void debugPageStructure() {
        if (driver == null) {
            initialize();
        }

        System.out.println("\n=== SELENIUM ДЕБАГ СТРУКТУРЫ ===");

        try {
            driver.get(BASE_URL);

            // Ждем загрузки
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body")));

            Thread.sleep(3000);

            // 1. Получаем информацию о странице
            System.out.println("URL: " + driver.getCurrentUrl());
            System.out.println("Заголовок: " + driver.getTitle());
            System.out.println("Размер страницы: " + driver.getPageSource().length() + " символов");

            // 2. Ищем табы
            System.out.println("\nПоиск табов с лигами:");
            List<WebElement> tabs = driver.findElements(By.cssSelector(".nav-tabs .nav-link"));
            System.out.println("Найдено табов: " + tabs.size());

            for (int i = 0; i < tabs.size(); i++) {
                WebElement tab = tabs.get(i);
                System.out.println((i+1) + ". Текст: '" + tab.getText() + "'");
                System.out.println("   ID: " + tab.getAttribute("id"));
                System.out.println("   aria-controls: " + tab.getAttribute("aria-controls"));
                System.out.println("   Класс: " + tab.getAttribute("class"));
                System.out.println("   Активен: " + tab.getAttribute("class").contains("active"));
            }

            // 3. Ищем матчи в активном табе
            System.out.println("\nПоиск матчей в активном табе:");
            WebElement activeTabPane = driver.findElement(By.cssSelector(".tab-pane.active"));
            List<WebElement> matchLinks = activeTabPane.findElements(By.cssSelector("a[href]"));
            System.out.println("Найдено ссылок на матчи: " + matchLinks.size());

            for (int i = 0; i < Math.min(5, matchLinks.size()); i++) {
                WebElement link = matchLinks.get(i);
                String href = link.getAttribute("href");
                System.out.println((i+1) + ". Ссылка: " + href);
                System.out.println("   Текст ссылки: '" + link.getText().replace("\n", " | ") + "'");
            }

            // 4. Показываем HTML активной вкладки
            System.out.println("\nHTML активной вкладки (первые 2000 символов):");
            System.out.println(activeTabPane.getAttribute("outerHTML").substring(0, Math.min(2000, activeTabPane.getAttribute("outerHTML").length())));

        } catch (Exception e) {
            System.err.println("Ошибка при дебаге: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== КОНЕЦ ДЕБАГА ===");
    }

    public static void analyzeMatchPage(String matchId) {
        if (driver == null) {
            initialize();
        }

        System.out.println("\n=== SELENIUM АНАЛИЗ МАТЧА " + matchId + " ===");

        try {
            String matchUrl = BASE_URL + matchId;
            driver.get(matchUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body")));

            Thread.sleep(2000);

            // 1. Основная информация
            System.out.println("URL: " + driver.getCurrentUrl());
            System.out.println("Заголовок: " + driver.getTitle());

            // 2. Получаем весь текст страницы
            String pageText = driver.findElement(By.tagName("body")).getText();
            System.out.println("\nТекст страницы (первые 1500 символов):");
            System.out.println(pageText.substring(0, Math.min(1500, pageText.length())));

            // 3. Ищем информацию о завершении периода
            System.out.println("\nПоиск информации о завершении периодов:");

            // Ищем "Конец периода" или аналогичные фразы
            Pattern endPattern = Pattern.compile("Конец\\s*(?:периода\\s*)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher endMatcher = endPattern.matcher(pageText);

            boolean foundEnd = false;
            while (endMatcher.find()) {
                System.out.println("✓ Найден 'Конец периода " + endMatcher.group(1) + "'");
                foundEnd = true;
            }

            if (!foundEnd) {
                System.out.println("✗ Не найден 'Конец периода'");

                // Ищем другие варианты
                endPattern = Pattern.compile("(\\d+)\\s*(?:й|й пер|пер|период)\\s*завершен", Pattern.CASE_INSENSITIVE);
                endMatcher = endPattern.matcher(pageText);
                while (endMatcher.find()) {
                    System.out.println("✓ Найден 'период " + endMatcher.group(1) + " завершен'");
                }
            }

            // 4. Ищем время периодов
            System.out.println("\nПоиск времени периодов:");
            Pattern timePattern = Pattern.compile("(\\d+)\\s*(?:й|й пер|пер|период).*?(\\d{1,2}):(\\d{2})", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher timeMatcher = timePattern.matcher(pageText);

            boolean foundTime = false;
            while (timeMatcher.find()) {
                System.out.println("✓ Период " + timeMatcher.group(1) + ": время " + timeMatcher.group(2) + ":" + timeMatcher.group(3));

                // Проверяем, завершен ли период по времени
                int minutes = Integer.parseInt(timeMatcher.group(2));
                if (minutes >= 7) {
                    System.out.println("  ⚠ Период " + timeMatcher.group(1) + " завершен по времени (" + minutes + " минут)");
                }
                foundTime = true;
            }

            if (!foundTime) {
                System.out.println("✗ Не найдено время периодов");
            }

            // 5. Ищем счет периодов
            System.out.println("\nПоиск счета периодов:");

            Pattern pattern = Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
            Matcher matcher = pattern.matcher(pageText);

            if (matcher.find()) {
                System.out.println("✓ Счет периодов найден: " + matcher.group(0));
                System.out.println("  Период 1: " + matcher.group(1) + "-" + matcher.group(2));
                System.out.println("  Период 2: " + matcher.group(3) + "-" + matcher.group(4));
                System.out.println("  Период 3: " + matcher.group(5) + "-" + matcher.group(6));

                // Анализируем, какие периоды сыграны
                int period1Home = Integer.parseInt(matcher.group(1));
                int period1Away = Integer.parseInt(matcher.group(2));
                int period2Home = Integer.parseInt(matcher.group(3));
                int period2Away = Integer.parseInt(matcher.group(4));
                int period3Home = Integer.parseInt(matcher.group(5));
                int period3Away = Integer.parseInt(matcher.group(6));

                if (period3Home > 0 || period3Away > 0) {
                    System.out.println("  ⚠ Третий период сыгран - матч завершен");
                } else if (period2Home > 0 || period2Away > 0) {
                    System.out.println("  ⚠ Второй период сыгран");
                } else if (period1Home > 0 || period1Away > 0) {
                    System.out.println("  ⚠ Первый период сыгран");
                }
            } else {
                System.out.println("✗ Полный счет периодов не найден");

                // Ищем частичный счет
                pattern = Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
                matcher = pattern.matcher(pageText);
                if (matcher.find()) {
                    System.out.println("✓ Найден счет 2 периодов: " + matcher.group(0));
                } else {
                    pattern = Pattern.compile("(\\d+)[:-](\\d+)");
                    matcher = pattern.matcher(pageText);
                    if (matcher.find()) {
                        System.out.println("✓ Найден счет: " + matcher.group(0));
                    } else {
                        System.out.println("✗ Счет не найден");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка анализа: " + e.getMessage());
        }

        System.out.println("=== КОНЕЦ АНАЛИЗА ===");
    }

    private static List<MatchInfo> getTestMatches() {
        // Тестовые данные с учетом, что нужны только 3HL NORTH и 3HL SOUTH
        List<MatchInfo> matches = new ArrayList<>();

        // 3HL SOUTH матчи
        String[] testMatchesSouth = {
                "1184751,Wizards,Dream Chasers,Live,2,5,3HL SOUTH",
                "1184703,Icebreakers,Dream Chasers,Завершен,5,8,3HL SOUTH",
                "1184752,Galaxy Warriors,Bad Company,Предстоящий,0,0,3HL SOUTH"
        };

        for (String testMatch : testMatchesSouth) {
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

        // 3HL NORTH матчи
        String[] testMatchesNorth = {
                "1185046,Golden knights,Black angels,Live,1,1,3HL NORTH",
                "1184998,City devils,Black angels,Завершен,7,2,3HL NORTH",
                "1185047,White bruins,Golden knights,Предстоящий,0,0,3HL NORTH"
        };

        for (String testMatch : testMatchesNorth) {
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
}
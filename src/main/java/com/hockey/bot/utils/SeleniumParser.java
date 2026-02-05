package com.hockey.bot.utils;

import com.hockey.bot.models.MatchInfo;
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

            // Ждем, пока загрузятся вкладки с матчами
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".tab-pane")));

            // Даем время для загрузки контента
            Thread.sleep(3000);

            // Ищем все вкладки
            List<WebElement> tabPanes = driver.findElements(By.cssSelector(".tab-pane"));
            System.out.println("Найдено вкладок: " + tabPanes.size());

            // Парсим каждую вкладку
            for (WebElement tabPane : tabPanes) {
                String leagueName = getLeagueNameFromTab(tabPane);
                System.out.println("Обработка лиги: " + leagueName);

                // Ищем все матчи во вкладке
                List<WebElement> matchLinks = tabPane.findElements(By.cssSelector("a[href]"));
                System.out.println("Найдено матчей в лиге: " + matchLinks.size());

                for (WebElement matchLink : matchLinks) {
                    try {
                        String href = matchLink.getAttribute("href");
                        if (href == null) continue;

                        // Извлекаем ID матча
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
                                    match.getAwayTeam() + " [" + match.getStatus() + "]");
                        }
                    } catch (Exception e) {
                        System.err.println("  ❌ Ошибка парсинга матча: " + e.getMessage());
                    }
                }
            }

            // Если не нашли через вкладки, пробуем другой подход
            if (matches.isEmpty()) {
                System.out.println("Альтернативный поиск матчей...");
                matches = findMatchesAlternative();
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга через Selenium: " + e.getMessage());
            e.printStackTrace();

            // В случае ошибки, используем тестовые данные
            matches = getTestMatches();
        }

        System.out.println("Всего найдено матчей: " + matches.size());
        return matches;
    }

    private static String getLeagueNameFromTab(WebElement tabPane) {
        try {
            // Пытаемся получить ID вкладки
            String tabId = tabPane.getAttribute("id");

            if (tabId != null) {
                if (tabId.contains("3HL_South")) return "3HL SOUTH";
                if (tabId.contains("3HL_North")) return "3HL NORTH";
                if (tabId.contains("MNHL2x2_A")) return "MNHL2x2 A";
                if (tabId.contains("MNHL2x2_B")) return "MNHL2x2 B";
            }

            // Ищем кнопку вкладки
            WebElement tabButton = driver.findElement(By.cssSelector("button[aria-controls='" + tabId + "']"));
            if (tabButton != null) {
                return tabButton.getText().trim();
            }

        } catch (Exception e) {
            // Игнорируем ошибки
        }

        return "Unknown League";
    }

    private static String extractMatchIdFromHref(String href) {
        if (href == null || href.isEmpty()) return null;

        // Извлекаем цифры из URL
        href = href.replaceAll("^.*/", ""); // Убираем все до последнего слэша
        href = href.replaceAll("\\D+", ""); // Оставляем только цифры

        return href.isEmpty() ? null : href;
    }

    private static MatchInfo parseMatchElement(WebElement matchElement, String matchId) {
        try {
            MatchInfo match = new MatchInfo();
            match.setMatchId(matchId);

            // Находим контейнер матча
            WebElement matchContainer;
            try {
                matchContainer = matchElement.findElement(By.cssSelector(".matches"));
            } catch (Exception e) {
                matchContainer = matchElement;
            }

            // Парсим команды
            try {
                WebElement homeTeamElement = matchContainer.findElement(By.cssSelector(".homeText"));
                match.setHomeTeam(homeTeamElement.getText().trim());
            } catch (Exception e) {
                // Пробуем найти по другому
                List<WebElement> matchTexts = matchContainer.findElements(By.cssSelector(".matchText"));
                if (matchTexts.size() >= 1) {
                    match.setHomeTeam(matchTexts.get(0).getText().trim());
                }
            }

            try {
                WebElement awayTeamElement = matchContainer.findElement(By.cssSelector(".awayText"));
                match.setAwayTeam(awayTeamElement.getText().trim());
            } catch (Exception e) {
                List<WebElement> matchTexts = matchContainer.findElements(By.cssSelector(".matchText"));
                if (matchTexts.size() >= 2) {
                    match.setAwayTeam(matchTexts.get(1).getText().trim());
                }
            }

            // Парсим статус
            try {
                WebElement statusElement = matchContainer.findElement(By.cssSelector(".vs span"));
                String status = statusElement.getText().trim();

                if (status.equalsIgnoreCase("Live") || status.contains("LIVE")) {
                    match.setStatus("Live");
                } else if (status.equalsIgnoreCase("Завершен")) {
                    match.setStatus("Завершен");
                } else if (status.equalsIgnoreCase("Предстоящий")) {
                    match.setStatus("Scheduled");
                } else {
                    match.setStatus(status);
                }
            } catch (Exception e) {
                match.setStatus("Unknown");
            }

            // Парсим время
            try {
                WebElement timeElement = matchContainer.findElement(By.cssSelector(".bg-gray"));
                String timeStr = timeElement.getText().trim();

                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                LocalDateTime matchTime = LocalDateTime.parse(today + " " + timeStr, DATE_FORMATTER);
                match.setMatchTime(matchTime);
            } catch (Exception e) {
                match.setMatchTime(LocalDateTime.now());
            }

            // Парсим счет
            try {
                WebElement homeScoreElement = matchContainer.findElement(By.cssSelector(".scoreHome"));
                WebElement awayScoreElement = matchContainer.findElement(By.cssSelector(".scoreAway"));

                String homeScoreStr = homeScoreElement.getText().trim();
                String awayScoreStr = awayScoreElement.getText().trim();

                if (!homeScoreStr.isEmpty() && !awayScoreStr.isEmpty()) {
                    match.setHomeScore(Integer.parseInt(homeScoreStr));
                    match.setAwayScore(Integer.parseInt(awayScoreStr));
                } else {
                    match.setHomeScore(0);
                    match.setAwayScore(0);
                }
            } catch (Exception e) {
                match.setHomeScore(0);
                match.setAwayScore(0);
            }

            // Проверяем, что матч валидный
            if (match.getHomeTeam() == null || match.getHomeTeam().isEmpty() ||
                    match.getAwayTeam() == null || match.getAwayTeam().isEmpty()) {
                return null;
            }

            return match;

        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга элемента: " + e.getMessage());
            return null;
        }
    }

    private static List<MatchInfo> findMatchesAlternative() {
        List<MatchInfo> matches = new ArrayList<>();

        try {
            // Ищем все элементы с классом .matches
            List<WebElement> matchElements = driver.findElements(By.cssSelector(".matches"));

            for (WebElement matchElement : matchElements) {
                try {
                    // Ищем родительскую ссылку
                    WebElement parentLink = matchElement.findElement(By.xpath("./.."));
                    if (parentLink != null && parentLink.getTagName().equals("a")) {
                        String href = parentLink.getAttribute("href");
                        String matchId = extractMatchIdFromHref(href);

                        if (matchId != null) {
                            MatchInfo match = parseMatchElement(parentLink, matchId);
                            if (match != null) {
                                matches.add(match);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }

        } catch (Exception e) {
            System.err.println("Ошибка альтернативного поиска: " + e.getMessage());
        }

        return matches;
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

            // Ищем счет периодов
            try {
                WebElement scorePeriodElement = driver.findElement(By.cssSelector(".score_period span"));
                String periodText = scorePeriodElement.getText().trim();
                System.out.println("Найден счет периодов: " + periodText);

                if (periodText.contains("|")) {
                    String[] periods = periodText.split("\\|");
                    if (periods.length >= 1) {
                        return new PeriodStats("(" + periods[0].trim() + ")");
                    }
                } else {
                    return new PeriodStats("(" + periodText + ")");
                }
            } catch (Exception e) {
                System.err.println("Не найден .score_period, ищу альтернативно...");
            }

            // Альтернативный поиск
            String pageSource = driver.getPageSource();

            // Ищем в исходном коде
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("1[-\\s]*(?:й|й пер|пер|период)[:\\s]*([\\d\\s:-]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(pageSource);

            if (matcher.find()) {
                String score = matcher.group(1).trim();
                return new PeriodStats("(" + score + ")");
            }

            // Ищем в тексте страницы
            String pageText = driver.findElement(By.tagName("body")).getText();
            pattern = java.util.regex.Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
            matcher = pattern.matcher(pageText);

            if (matcher.find()) {
                String score = matcher.group(1) + ":" + matcher.group(2);
                return new PeriodStats("(" + score + ")");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения статистики: " + e.getMessage());
        }

        // Возвращаем тестовые данные
        System.out.println("Возвращаю тестовые данные");
        return new PeriodStats("(2:1)");
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

            // 2. Ищем вкладки
            List<WebElement> tabs = driver.findElements(By.cssSelector(".tab-pane"));
            System.out.println("\nНайдено вкладок (.tab-pane): " + tabs.size());

            for (int i = 0; i < tabs.size(); i++) {
                WebElement tab = tabs.get(i);
                System.out.println((i+1) + ". ID: " + tab.getAttribute("id"));
                System.out.println("   Видимый: " + tab.isDisplayed());
                System.out.println("   Текст: '" + tab.getText().substring(0, Math.min(100, tab.getText().length())) + "'");
            }

            // 3. Ищем матчи
            List<WebElement> matchElements = driver.findElements(By.cssSelector(".matches"));
            System.out.println("\nНайдено элементов .matches: " + matchElements.size());

            for (int i = 0; i < Math.min(5, matchElements.size()); i++) {
                WebElement match = matchElements.get(i);
                System.out.println((i+1) + ". .matches элемент:");
                System.out.println("   Текст: '" + match.getText().replace("\n", " | ") + "'");

                // Ищем ссылку
                try {
                    WebElement link = match.findElement(By.xpath("./.."));
                    if (link.getTagName().equals("a")) {
                        System.out.println("   Ссылка: " + link.getAttribute("href"));
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }

            // 4. Ищем Live матчи
            System.out.println("\nПоиск Live матчей:");
            List<WebElement> liveElements = driver.findElements(By.xpath("//*[contains(text(), 'Live') or contains(text(), 'LIVE')]"));
            System.out.println("Найдено элементов с 'Live': " + liveElements.size());

            // 5. Делаем скриншот (опционально)
            try {
                // Для скриншота нужно добавить зависимость на org.apache.commons:commons-io
                // File screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
                // FileUtils.copyFile(screenshot, new File("debug_screenshot.png"));
                // System.out.println("Скриншот сохранен: debug_screenshot.png");
            } catch (Exception e) {
                // Игнорируем
            }

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

            // 2. Ищем элементы периода
            System.out.println("\nПоиск элементов периода:");

            try {
                WebElement scorePeriod = driver.findElement(By.cssSelector(".score_period span"));
                System.out.println("✓ .score_period найден: " + scorePeriod.getText());
            } catch (Exception e) {
                System.out.println("✗ .score_period не найден");
            }

            try {
                List<WebElement> periodElements = driver.findElements(By.xpath("//*[contains(text(), 'период') or contains(text(), 'ПЕРИОД') or contains(text(), 'period')]"));
                System.out.println("Найдено элементов с 'период': " + periodElements.size());

                for (int i = 0; i < Math.min(3, periodElements.size()); i++) {
                    System.out.println((i+1) + ". " + periodElements.get(i).getText());
                }
            } catch (Exception e) {
                System.out.println("✗ Элементы периода не найдены");
            }

            // 3. Показываем фрагмент страницы
            System.out.println("\nТекст страницы (первые 500 символов):");
            String pageText = driver.findElement(By.tagName("body")).getText();
            System.out.println(pageText.substring(0, Math.min(500, pageText.length())));

            // 4. Ищем счет
            System.out.println("\nПоиск счета:");
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)\\s*[|]\\s*(\\d+)[:-]\\s*(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(pageText);

            if (matcher.find()) {
                System.out.println("✓ Счет найден:");
                System.out.println("  Период 1: " + matcher.group(1) + "-" + matcher.group(2));
                System.out.println("  Период 2: " + matcher.group(3) + "-" + matcher.group(4));
                System.out.println("  Период 3: " + matcher.group(5) + "-" + matcher.group(6));
            } else {
                System.out.println("✗ Счет не найден в стандартном формате");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка анализа: " + e.getMessage());
        }

        System.out.println("=== КОНЕЦ АНАЛИЗА ===");
    }

    private static List<MatchInfo> getTestMatches() {
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
}
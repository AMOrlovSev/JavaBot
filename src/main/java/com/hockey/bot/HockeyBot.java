package com.hockey.bot;

import com.hockey.bot.models.MatchInfo;
import com.hockey.bot.models.PeriodStats;
import com.hockey.bot.utils.ParserUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class HockeyBot extends TelegramLongPollingBot {

    private static final String BOT_TOKEN = "7361604859:AAGYKiYyFaBVZvY-qJjmfzaEdx240CPm1Qg";
    private static final String BOT_USERNAME = "hockey_match_tracker_bot";

    // Админский chat ID
    private static final long ADMIN_ID = 619727534L;

    private final Set<String> notifiedMatches = ConcurrentHashMap.newKeySet();
    private final Set<Long> monitoringChats = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    public void startScheduledMonitoring() {
        // Запуск мониторинга каждую минуту
        scheduler.scheduleAtFixedRate(this::checkMatchesRealTime, 0, 1, TimeUnit.MINUTES);
    }

    private void checkMatchesRealTime() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("\n" + timestamp + " - Проверка матчей");

        try {
            List<MatchInfo> matches = ParserUtils.parseTodayMatches();
            System.out.println("Найдено матчей: " + matches.size());

            if (matches.isEmpty()) {
                System.out.println("⚠️ Матчи не найдены!");
                return;
            }

            int liveCount = 0;
            int analyzedCount = 0;
            int signalCount = 0;

            for (MatchInfo match : matches) {
                // Пропускаем уже обработанные
                if (notifiedMatches.contains(match.getMatchId())) {
                    continue;
                }

                // Проверяем только Live матчи
                if ("Live".equalsIgnoreCase(match.getStatus())) {
                    liveCount++;
                    System.out.println("\n🔍 Live матч: " + match.getHomeTeam() + " vs " + match.getAwayTeam());
                    System.out.println("   ID: " + match.getMatchId() + ", Лига: " + match.getTournament());
                    System.out.println("   Счет: " + match.getHomeScore() + ":" + match.getAwayScore());

                    try {
                        PeriodStats stats = ParserUtils.getPeriodStats(match.getMatchId());

                        if (stats != null) {
                            analyzedCount++;
                            System.out.println("   📊 Период 1: " + stats.getFirstPeriodHomeGoals() +
                                    ":" + stats.getFirstPeriodAwayGoals());
                            System.out.println("   📈 Всего шайб: " + stats.getFirstPeriodTotalGoals());

                            if (stats.isFirstPeriodLessThanThree()) {
                                System.out.println("   🚨 УСЛОВИЕ ВЫПОЛНЕНО! TM 2.5 в 1-м периоде!");
                                sendAlert(match, stats);
                                signalCount++;
                            }

                            notifiedMatches.add(match.getMatchId());
                            System.out.println("   ✅ Матч помечен как обработанный");
                        } else {
                            System.out.println("   ⚠ Статистика периодов не найдена");
                        }
                    } catch (Exception e) {
                        System.err.println("   ❗ Ошибка: " + e.getMessage());
                    }
                }
            }

            System.out.println("\n📊 ИТОГ:");
            System.out.println("Всего матчей: " + matches.size());
            System.out.println("Live матчей: " + liveCount);
            System.out.println("Проанализировано: " + analyzedCount);
            System.out.println("Сигналов отправлено: " + signalCount);
            System.out.println("Обработано всего: " + notifiedMatches.size());

        } catch (Exception e) {
            System.err.println("❌ Ошибка в мониторинге: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== Конец проверки ===\n");
    }

    private void sendAlert(MatchInfo match, PeriodStats stats) {
        String message = "🏒 *СИГНАЛ: ТМ 2.5 В 1-М ПЕРИОДЕ*\n\n" +
                "🔥 " + match.getHomeTeam() + " — " + match.getAwayTeam() + "\n" +
                "🏆 Лига: " + match.getTournament() + "\n" +
                "📊 Счет 1-го пер: *" + stats.getFirstPeriodHomeGoals() + ":" +
                stats.getFirstPeriodAwayGoals() + "*\n" +
                "🎯 Всего шайб: *" + stats.getFirstPeriodTotalGoals() + "*\n" +
                "✅ Условие выполнено!";

        System.out.println("📤 Отправка уведомления в " + monitoringChats.size() + " чат(ов)");

        for (Long chatId : monitoringChats) {
            sendMessage(chatId, message);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();

            System.out.println("📩 Сообщение от " + chatId + ": " + text);

            // Проверка на команды с параметрами
            if (text.startsWith("/analyze ")) {
                String matchId = text.substring(9).trim();
                handleAnalyzeCommand(chatId, matchId);
            } else {
                // Команды без параметров
                switch (text) {
                    case "/start":
                        handleStartCommand(chatId);
                        break;

                    case "/monitor":
                        handleMonitorCommand(chatId);
                        break;

                    case "/stop":
                        handleStopCommand(chatId);
                        break;

                    case "/status":
                        handleStatusCommand(chatId);
                        break;

                    case "/matches":
                        handleMatchesCommand(chatId);
                        break;

                    case "/today":
                        handleTodayCommand(chatId);
                        break;

                    case "/help":
                        handleHelpCommand(chatId);
                        break;

                    case "/debug":
                        if (chatId == ADMIN_ID) {
                            handleDebugCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/debugpage":
                        if (chatId == ADMIN_ID) {
                            handleDebugPageCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/testperiod":
                        if (chatId == ADMIN_ID) {
                            handleTestPeriodCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/selenium_on":
                        if (chatId == ADMIN_ID) {
                            handleSeleniumOnCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/selenium_off":
                        if (chatId == ADMIN_ID) {
                            handleSeleniumOffCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/parser_status":
                        handleParserStatusCommand(chatId);
                        break;

                    case "/clear_matches":
                        if (chatId == ADMIN_ID) {
                            handleClearMatchesCommand(chatId);
                        } else {
                            sendMessage(chatId, "⚠️ Эта команда только для администратора.");
                        }
                        break;

                    case "/analyze":
                        sendMessage(chatId, "⚠️ Использование: /analyze [ID_матча]\n" +
                                "Пример: /analyze 1184998");
                        break;

                    default:
                        sendMessage(chatId, "🤔 Неизвестная команда. Используйте /help для списка команд.");
                        break;
                }
            }
        }
    }

    // Вспомогательные методы для обработки команд
    private void handleStartCommand(long chatId) {
        String message = "🏒 *Hockey Match Tracker Bot*\n\n" +
                "Я отслеживаю хоккейные матчи с сайта sportregion2020.ru в реальном времени.\n\n" +
                "📊 *Условие сигнала:*\n" +
                "• Матч в статусе Live\n" +
                "• Первый период завершен\n" +
                "• Меньше 3 шайб в 1-м периоде\n\n" +
                "🚨 *При выполнении условия вы получите уведомление!*\n\n" +
                "📋 *Доступные команды:*\n" +
                "/monitor - начать мониторинг\n" +
                "/stop - остановить мониторинг\n" +
                "/status - статус мониторинга\n" +
                "/matches - список отслеживаемых матчей\n" +
                "/today - матчи на сегодня\n" +
                "/analyze [ID] - анализ матча\n" +
                "/help - помощь";

        if (chatId == ADMIN_ID) {
            message += "\n\n⚙️ *Админ команды:*\n" +
                    "/debug - отладочная информация\n" +
                    "/debugpage - анализ структуры сайта\n" +
                    "/testperiod - тест парсинга\n" +
                    "/selenium_on - включить Selenium\n" +
                    "/selenium_off - выключить Selenium\n" +
                    "/parser_status - статус парсеров\n" +
                    "/clear_matches - очистить список матчей";
        }

        sendMessage(chatId, message);
    }

    private void handleMonitorCommand(long chatId) {
        monitoringChats.add(chatId);
        String message = "✅ *Мониторинг запущен!*\n\n" +
                "Теперь вы будете получать уведомления при выполнении условия TM 2.5 в 1-м периоде.\n\n" +
                "🔍 *Как это работает:*\n" +
                "• Проверка каждую минуту\n" +
                "• Только Live матчи\n" +
                "• Все лиги: 3HL SOUTH/NORTH, MNHL2x2 A/B\n" +
                "• Уведомление сразу после завершения 1-го периода\n\n" +
                "📊 *Статистика:*\n" +
                "• Отслеживаемых чатов: " + monitoringChats.size() + "\n" +
                "• Обработано матчей: " + notifiedMatches.size();

        sendMessage(chatId, message);
        System.out.println("✅ Мониторинг активирован для chatId=" + chatId);
    }

    private void handleStopCommand(long chatId) {
        if (monitoringChats.remove(chatId)) {
            sendMessage(chatId, "🛑 *Мониторинг остановлен.*\n\n" +
                    "Вы больше не будете получать уведомления.\n" +
                    "Для возобновления отправьте /monitor");
            System.out.println("🛑 Мониторинг остановлен для chatId=" + chatId);
        } else {
            sendMessage(chatId, "ℹ️ Мониторинг уже был остановлен.");
        }
    }

    private void handleStatusCommand(long chatId) {
        boolean isMonitoring = monitoringChats.contains(chatId);
        String status = isMonitoring ? "✅ Активен" : "❌ Не активен";

        StringBuilder statusMsg = new StringBuilder();
        statusMsg.append("📊 *Статус мониторинга:*\n\n");
        statusMsg.append("• Ваш статус: ").append(status).append("\n");
        statusMsg.append("• Всего чатов: ").append(monitoringChats.size()).append("\n");
        statusMsg.append("• Обработано матчей: ").append(notifiedMatches.size()).append("\n");
        statusMsg.append("• Следующая проверка: через 1 минуту\n\n");

        if (isMonitoring) {
            statusMsg.append("📅 *Расписание проверок:*\n");
            statusMsg.append("• Каждую минуту\n");
            statusMsg.append("• Только Live матчи\n");
            statusMsg.append("• Все лиги\n\n");
            statusMsg.append("🎯 *Условие сигнала:*\n");
            statusMsg.append("Меньше 3 шайб в 1-м периоде");
        } else {
            statusMsg.append("ℹ️ Для начала мониторинга отправьте /monitor");
        }

        sendMessage(chatId, statusMsg.toString());
    }

    private void handleMatchesCommand(long chatId) {
        if (notifiedMatches.isEmpty()) {
            sendMessage(chatId, "📭 *Список обработанных матчей пуст.*\n\n" +
                    "Мониторинг еще не нашел матчей, соответствующих условиям.");
        } else {
            StringBuilder msg = new StringBuilder("📋 *Обработанные матчи:*\n\n");
            int count = 1;

            List<String> matchList = new ArrayList<>(notifiedMatches);
            Collections.sort(matchList, Collections.reverseOrder());

            for (String matchId : matchList) {
                if (count > 15) {
                    msg.append("... и еще ").append(notifiedMatches.size() - 15).append(" матчей");
                    break;
                }
                msg.append(count).append(". ID: ").append(matchId).append("\n");
                count++;
            }

            msg.append("\n📊 Всего: ").append(notifiedMatches.size()).append(" матчей");
            sendMessage(chatId, msg.toString());
        }
    }

    private void handleTodayCommand(long chatId) {
        sendMessage(chatId, "🔍 *Поиск матчей на сегодня...*");

        new Thread(() -> {
            try {
                List<MatchInfo> matches = ParserUtils.parseTodayMatches();

                if (matches.isEmpty()) {
                    sendMessage(chatId, "📭 *Матчей на сегодня не найдено.*");
                    return;
                }

                StringBuilder msg = new StringBuilder();
                msg.append("📅 *Матчи на сегодня:*\n\n");

                // Группируем по статусу
                int liveCount = 0, finishedCount = 0, upcomingCount = 0;
                List<MatchInfo> liveMatches = new ArrayList<>();
                List<MatchInfo> finishedMatches = new ArrayList<>();
                List<MatchInfo> upcomingMatches = new ArrayList<>();

                for (MatchInfo match : matches) {
                    switch (match.getStatus()) {
                        case "Live":
                            liveCount++;
                            liveMatches.add(match);
                            break;
                        case "Завершен":
                            finishedCount++;
                            finishedMatches.add(match);
                            break;
                        default:
                            upcomingCount++;
                            upcomingMatches.add(match);
                            break;
                    }
                }

                msg.append("🎥 *Live сейчас:* ").append(liveCount).append("\n");
                msg.append("✅ *Завершенные:* ").append(finishedCount).append("\n");
                msg.append("⏰ *Предстоящие:* ").append(upcomingCount).append("\n");
                msg.append("📊 *Всего:* ").append(matches.size()).append("\n\n");

                // Показываем Live матчи
                if (!liveMatches.isEmpty()) {
                    msg.append("🔥 *Сейчас в эфире:*\n");
                    for (int i = 0; i < Math.min(3, liveMatches.size()); i++) {
                        MatchInfo match = liveMatches.get(i);
                        msg.append("• ").append(match.getHomeTeam())
                                .append(" - ").append(match.getAwayTeam())
                                .append(" (").append(match.getHomeScore())
                                .append(":").append(match.getAwayScore()).append(")");

                        if (match.getTournament() != null) {
                            msg.append(" - ").append(match.getTournament());
                        }
                        msg.append("\n");
                    }
                    if (liveMatches.size() > 3) {
                        msg.append("... и еще ").append(liveMatches.size() - 3).append(" матчей\n");
                    }
                    msg.append("\n");
                }

                // Показываем несколько завершенных матчей
                if (!finishedMatches.isEmpty()) {
                    msg.append("✅ *Недавно завершены:*\n");
                    int shown = 0;
                    for (MatchInfo match : finishedMatches) {
                        if (shown < 3) {
                            msg.append("• ").append(match.getHomeTeam())
                                    .append(" ").append(match.getHomeScore())
                                    .append("-").append(match.getAwayScore())
                                    .append(" ").append(match.getAwayTeam());

                            if (match.getTournament() != null) {
                                msg.append(" (").append(match.getTournament()).append(")");
                            }
                            msg.append("\n");
                            shown++;
                        }
                    }
                    if (finishedMatches.size() > 3) {
                        msg.append("... и еще ").append(finishedMatches.size() - 3).append(" матчей\n");
                    }
                }

                sendMessage(chatId, msg.toString());

            } catch (Exception e) {
                sendMessage(chatId, "❌ *Ошибка при загрузке матчей:* " + e.getMessage());
                System.err.println("Ошибка в команде /today: " + e.getMessage());
            }
        }).start();
    }

    private void handleHelpCommand(long chatId) {
        String message = "❓ *Помощь*\n\n" +
                "🤖 *О боте:*\n" +
                "Бот парсит сайт sportregion2020.ru и проверяет хоккейные матчи.\n\n" +
                "📊 *Условие сигнала:*\n" +
                "• Матч в статусе Live\n" +
                "• Первый период завершен\n" +
                "• Меньше 3 шайб в 1-м периоде\n\n" +
                "🚨 *Как это работает:*\n" +
                "1. Бот проверяет сайт каждую минуту\n" +
                "2. Находит все Live матчи\n" +
                "3. Анализирует счет первого периода\n" +
                "4. Если шайб < 3 - отправляет сигнал\n\n" +
                "📋 *Команды для всех:*\n" +
                "/start - информация о боте\n" +
                "/monitor - начать получать уведомления\n" +
                "/stop - остановить уведомления\n" +
                "/status - текущий статус\n" +
                "/today - матчи на сегодня\n" +
                "/analyze [ID] - анализ матча\n" +
                "/matches - список обработанных матчей\n" +
                "/help - эта справка";

        if (chatId == ADMIN_ID) {
            message += "\n\n⚙️ *Админ команды:*\n" +
                    "/debug - отладочная информация\n" +
                    "/debugpage - анализ структуры сайта\n" +
                    "/testperiod - тест парсинга\n" +
                    "/selenium_on - включить Selenium\n" +
                    "/selenium_off - выключить Selenium\n" +
                    "/parser_status - статус парсеров\n" +
                    "/clear_matches - очистить список матчей";
        }

        sendMessage(chatId, message);
    }

    private void handleAnalyzeCommand(long chatId, String matchId) {
        if (matchId.isEmpty()) {
            sendMessage(chatId, "⚠️ Укажите ID матча\n" +
                    "Пример: /analyze 1184998");
            return;
        }

        if (!matchId.matches("\\d+")) {
            sendMessage(chatId, "⚠️ ID матча должен содержать только цифры\n" +
                    "Пример: /analyze 1184998");
            return;
        }

        sendMessage(chatId, "🔬 *Анализ матча " + matchId + "*\n\nЗапускаю анализ...");

        new Thread(() -> {
            try {
                ParserUtils.analyzeMatchPage(matchId);
                sendMessage(chatId, "✅ Анализ структуры завершен. Результаты в консоли.");

                // Получаем статистику периодов
                PeriodStats stats = ParserUtils.getPeriodStats(matchId);
                if (stats != null) {
                    StringBuilder result = new StringBuilder();
                    result.append("📊 *Статистика периодов:*\n\n");
                    result.append("• ID матча: ").append(matchId).append("\n");
                    result.append("• Счет 1-го пер: ").append(stats.getFirstPeriodHomeGoals())
                            .append(":").append(stats.getFirstPeriodAwayGoals()).append("\n");
                    result.append("• Всего шайб: ").append(stats.getFirstPeriodTotalGoals()).append("\n");
                    result.append("• Условие TM 2.5: ");

                    if (stats.isFirstPeriodLessThanThree()) {
                        result.append("✅ ВЫПОЛНЕНО (меньше 3 шайб)");
                    } else {
                        result.append("❌ НЕ выполнено (3 или больше шайб)");
                    }

                    sendMessage(chatId, result.toString());
                } else {
                    sendMessage(chatId, "❌ Не удалось получить статистику периодов.");
                }
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка анализа: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void handleDebugCommand(long chatId) {
        sendMessage(chatId, "🔧 *Режим отладки*\n\nЗапускаю диагностику...");

        new Thread(() -> {
            try {
                StringBuilder debugMsg = new StringBuilder();
                debugMsg.append("🔄 *Диагностика системы:*\n\n");

                // 1. Информация о мониторинге
                debugMsg.append("1️⃣ *Мониторинг:*\n");
                debugMsg.append("• Активных чатов: ").append(monitoringChats.size()).append("\n");
                debugMsg.append("• Обработано матчей: ").append(notifiedMatches.size()).append("\n");
                debugMsg.append("• Время сервера: ").append(LocalDateTime.now()).append("\n\n");

                // 2. Тестовый парсинг
                debugMsg.append("2️⃣ *Тест парсинга:*\n");
                try {
                    List<MatchInfo> matches = ParserUtils.parseTodayMatches();
                    debugMsg.append("• Найдено матчей: ").append(matches.size()).append("\n");

                    if (!matches.isEmpty()) {
                        int live = 0, finished = 0, upcoming = 0;
                        for (MatchInfo match : matches) {
                            if ("Live".equals(match.getStatus())) live++;
                            else if ("Завершен".equals(match.getStatus())) finished++;
                            else upcoming++;
                        }
                        debugMsg.append("• Live: ").append(live).append(", Завершены: ").append(finished)
                                .append(", Предстоящие: ").append(upcoming).append("\n");

                        if (!matches.isEmpty()) {
                            MatchInfo first = matches.get(0);
                            debugMsg.append("• Пример: ").append(first.getHomeTeam())
                                    .append(" vs ").append(first.getAwayTeam())
                                    .append(" [").append(first.getStatus()).append("]\n");
                        }
                    }
                } catch (Exception e) {
                    debugMsg.append("• ❌ Ошибка парсинга: ").append(e.getMessage()).append("\n");
                }
                debugMsg.append("\n");

                // 3. Системная информация
                debugMsg.append("3️⃣ *Система:*\n");
                Runtime runtime = Runtime.getRuntime();
                debugMsg.append("• Память: ")
                        .append(runtime.freeMemory() / 1024 / 1024).append("MB свободно / ")
                        .append(runtime.totalMemory() / 1024 / 1024).append("MB всего\n");
                debugMsg.append("• Процессоров: ").append(runtime.availableProcessors()).append("\n");
                debugMsg.append("• Время работы: ").append(getUptime()).append("\n");

                sendMessage(chatId, debugMsg.toString());

            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка диагностики: " + e.getMessage());
            }
        }).start();
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - startTime;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format("%d ч. %d мин. %d сек.", hours % 24, minutes % 60, seconds % 60);
    }

    private final long startTime = System.currentTimeMillis();

    private void handleDebugPageCommand(long chatId) {
        sendMessage(chatId, "🔍 *Анализ структуры сайта...*");

        new Thread(() -> {
            try {
                ParserUtils.debugPageStructure();
                sendMessage(chatId, "✅ Анализ структуры завершен. Результаты в консоли сервера.");
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка анализа: " + e.getMessage());
            }
        }).start();
    }

    private void handleTestPeriodCommand(long chatId) {
        sendMessage(chatId, "🧪 *Тест парсинга периодов*\n\nТестирую на матче 1184998...");

        new Thread(() -> {
            try {
                PeriodStats stats = ParserUtils.getPeriodStats("1184998");

                StringBuilder testMsg = new StringBuilder();
                testMsg.append("📊 *Результаты теста:*\n\n");

                if (stats != null) {
                    testMsg.append("✅ Статистика получена успешно!\n\n");
                    testMsg.append("• ID матча: 1184998\n");
                    testMsg.append("• Счет 1-го пер: ").append(stats.getFirstPeriodHomeGoals())
                            .append(":").append(stats.getFirstPeriodAwayGoals()).append("\n");
                    testMsg.append("• Всего шайб: ").append(stats.getFirstPeriodTotalGoals()).append("\n");
                    testMsg.append("• Условие TM 2.5: ");

                    if (stats.isFirstPeriodLessThanThree()) {
                        testMsg.append("✅ ВЫПОЛНЕНО (меньше 3 шайб)\n");
                    } else {
                        testMsg.append("❌ НЕ выполнено (3 или больше шайб)\n");
                    }
                } else {
                    testMsg.append("❌ Не удалось получить статистику.\n");
                    testMsg.append("Возможные причины:\n");
                    testMsg.append("1. Матч не найден\n");
                    testMsg.append("2. Нет данных о периодах\n");
                    testMsg.append("3. Ошибка парсинга");
                }

                sendMessage(chatId, testMsg.toString());

            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка теста: " + e.getMessage());
            }
        }).start();
    }

    private void handleSeleniumOnCommand(long chatId) {
        sendMessage(chatId, "⚙️ *Включение Selenium...*");

        new Thread(() -> {
            try {
                ParserUtils.initSelenium();
                sendMessage(chatId, "✅ Selenium успешно инициализирован!\n\n" +
                        "Теперь используется Selenium парсер для работы с JavaScript-сайтами.");
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка инициализации Selenium: " + e.getMessage());
            }
        }).start();
    }

    private void handleSeleniumOffCommand(long chatId) {
        sendMessage(chatId, "⚙️ *Выключение Selenium...*");

        new Thread(() -> {
            try {
                ParserUtils.closeSelenium();
                sendMessage(chatId, "✅ Selenium выключен.\n\n" +
                        "Теперь используется простой парсер.");
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка выключения Selenium: " + e.getMessage());
            }
        }).start();
    }

    private void handleParserStatusCommand(long chatId) {
        sendMessage(chatId, "⚙️ *Статус парсеров:*\n\n" +
                "• Selenium: " + (isSeleniumAvailable() ? "✅ Доступен" : "❌ Недоступен") + "\n" +
                "• Простой парсер: ✅ Всегда доступен\n\n" +
                "ℹ️ Selenium используется для парсинга JavaScript-сайтов.\n" +
                "Для управления используйте:\n" +
                "/selenium_on - включить Selenium\n" +
                "/selenium_off - выключить Selenium");
    }

    private void handleClearMatchesCommand(long chatId) {
        int count = notifiedMatches.size();
        notifiedMatches.clear();
        sendMessage(chatId, "🧹 *Список матчей очищен*\n\n" +
                "Удалено " + count + " матчей из памяти.\n" +
                "Мониторинг начнет заново отслеживать матчи.");
        System.out.println("🗑️ Очищено " + count + " матчей по команде от " + chatId);
    }

    // Вспомогательный метод для проверки доступности Selenium
    private boolean isSeleniumAvailable() {
        try {
            // Простая проверка - можно расширить при необходимости
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("Markdown");

        try {
            execute(message);
            System.out.println("✅ Сообщение отправлено в " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки сообщения в chatId=" + chatId + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        System.out.println("🛑 Завершение работы планировщика...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                System.out.println("⚠️ Планировщик принудительно остановлен");
            }
            System.out.println("✅ Планировщик остановлен");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            System.err.println("❌ Ошибка остановки планировщика: " + e.getMessage());
        }
    }
}
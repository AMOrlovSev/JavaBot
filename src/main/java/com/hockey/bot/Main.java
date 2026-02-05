package com.hockey.bot;

import com.hockey.bot.utils.ParserUtils;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 Запуск Hockey Match Tracker...");

        try {
            // Инициализируем Selenium
            System.out.println("🔄 Инициализация Selenium...");
            ParserUtils.initSelenium();

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            HockeyBot bot = new HockeyBot();
            botsApi.registerBot(bot);

            // Запускаем мониторинг
            bot.startScheduledMonitoring();

            System.out.println("✅ Бот успешно запущен!");
            System.out.println("\n📋 Доступные команды:");
            System.out.println("1. /today - матчи на сегодня");
            System.out.println("2. /analyze [ID] - детальный анализ матча");
            System.out.println("3. /monitor - автоматический мониторинг");
            System.out.println("4. /stop - остановить мониторинг");
            System.out.println("5. /debugpage - отладка структуры (админ)");
            System.out.println("6. /help - помощь");

            // Обработка завершения
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Завершение работы бота...");
                bot.shutdown();
                ParserUtils.closeSelenium();
                System.out.println("👋 Бот остановлен.");
            }));

            // Бесконечный цикл
            while (true) {
                Thread.sleep(1000);
            }

        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка запуска бота: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("❌ Общая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
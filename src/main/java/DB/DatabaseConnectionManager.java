package DB;

import Config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Клас-менеджер для управління з'єднаннями з базою даних.
 * Надає метод для отримання активного з'єднання.
 */
public class DatabaseConnectionManager {

    private static final Logger logger = LogManager.getLogger("insurance.log");

    static {
        try {
            logger.debug("Спроба завантажити JDBC драйвер MySQL: com.mysql.cj.jdbc.Driver");
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("JDBC драйвер MySQL успішно завантажено.");
        } catch (ClassNotFoundException e) {
            logger.fatal("Критична помилка: JDBC драйвер MySQL не знайдено. Перевірте залежності проекту.", e);
            throw new RuntimeException("Не вдалося завантажити JDBC драйвер MySQL", e);
        }
    }

    /**
     * Встановлює та повертає з'єднання з базою даних.
     * <p>
     * Важливо: Клієнтський код, який викликає цей метод,
     * відповідає за закриття отриманого з'єднання за допомогою
     * {@code connection.close()} у блоці {@code finally} або використовуючи try-with-resources.
     * </p>
     *
     * @return Об'єкт {@link Connection} для взаємодії з базою даних.
     * @throws SQLException якщо виникає помилка під час спроби підключення до бази даних.
     */
    public static Connection getConnection() throws SQLException {
        logger.debug("Спроба отримати з'єднання з базою даних.");
        String url = DatabaseConfig.getDbUrl();
        String user = DatabaseConfig.getDbUsername();
        String password = DatabaseConfig.getDbPassword();

        if (url == null || url.trim().isEmpty()) {
            logger.error("Помилка конфігурації: URL для БД не вказано або не завантажено. Перевірте файл 'db.properties'.");
            throw new SQLException("URL для підключення до БД не налаштовано.");
        }
        if (user == null) {
            logger.error("Помилка конфігурації: Ім'я користувача для БД не вказано або не завантажено. Перевірте файл 'db.properties'.");
            throw new SQLException("Ім'я користувача для підключення до БД не налаштовано.");
        }
        if (password == null) {
            logger.warn("Попередження конфігурації: Пароль для БД не вказано або не завантажено. Використовується null. Перевірте файл 'db.properties'.");
        }

        logger.debug("Параметри підключення: URL='{}', Користувач='{}'", url, user);

        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            logger.info("З'єднання з базою даних '{}' успішно встановлено для користувача '{}'.", url, user);
            return connection;
        } catch (SQLException e) {
            logger.error("Помилка підключення до бази даних: URL='{}', Користувач='{}'. Помилка: {}", url, user, e.getMessage(), e);
            throw e;
        }
    }
}
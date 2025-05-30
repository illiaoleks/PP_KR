package Config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Клас для завантаження конфігураційних даних для підключення до бази даних
 * з файлу db.properties.
 */
public class DatabaseConfig {
    private static final Logger logger = LogManager.getLogger(DatabaseConfig.class);
    private static final String PROPERTIES_FILE = "db.properties";
    private static final Properties properties = new Properties();

    static {
        logger.info("Початок завантаження конфігурації бази даних з файлу '{}'", PROPERTIES_FILE);
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                logger.error("Помилка: Неможливо знайти конфігураційний файл '{}'. Переконайтесь, що він знаходиться в src/main/resources.", PROPERTIES_FILE);
            } else {
                properties.load(input);
                logger.info("Конфігураційний файл '{}' успішно завантажено.", PROPERTIES_FILE);
            }
        } catch (IOException ex) {
            logger.error("Помилка завантаження конфігураційного файлу '{}': {}", PROPERTIES_FILE, ex.getMessage(), ex);
        }
    }

    /**
     * Повертає URL для підключення до бази даних.
     *
     * @return URL бази даних або null, якщо властивість не знайдена.
     */
    public static String getDbUrl() {
        String url = properties.getProperty("db.url");
        if (url == null) {
            logger.warn("Властивість 'db.url' не знайдена у файлі '{}'", PROPERTIES_FILE);
        }
        return url;
    }

    /**
     * Повертає ім'я користувача для підключення до бази даних.
     *
     * @return Ім'я користувача або null, якщо властивість не знайдена.
     */
    public static String getDbUsername() {
        String username = properties.getProperty("db.username");
        if (username == null) {
            logger.warn("Властивість 'db.username' не знайдена у файлі '{}'", PROPERTIES_FILE);
        }
        return username;
    }

    /**
     * Повертає пароль для підключення до бази даних.
     *
     * @return Пароль або null, якщо властивість не знайдена.
     */
    public static String getDbPassword() {
        String password = properties.getProperty("db.password");
        if (password == null) {
            logger.warn("Властивість 'db.password' не знайдена у файлі '{}'", PROPERTIES_FILE);
        }
        return password;
    }

}
package DB;

import Config.DatabaseConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DatabaseConnectionManagerTest {

    @Mock
    private static Connection mockConnection;

    private static ListAppender listAppender;
    private static org.apache.logging.log4j.core.Logger insuranceLogger;


    private static MockedStatic<DatabaseConfig> mockedDatabaseConfig;
    private static MockedStatic<DriverManager> mockedDriverManager;



    private static class ListAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();
        ListAppender(String name) { super(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY); }
        @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        public List<LogEvent> getEvents() { return events; }
        public void clearEvents() { events.clear(); }
        public boolean containsMessage(Level level, String partialMessage) {
            return events.stream().anyMatch(event ->
                    event.getLevel().equals(level) &&
                            event.getMessage().getFormattedMessage().contains(partialMessage));
        }
    }

    @BeforeAll
    static void setupAll() {
        // Налаштовуємо ListAppender
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        insuranceLogger = context.getLogger("insurance.log");
        listAppender = new ListAppender("TestDBManagerAppender");
        listAppender.start();
        insuranceLogger.addAppender(listAppender);
        insuranceLogger.setLevel(Level.ALL);


        mockedDatabaseConfig = Mockito.mockStatic(DatabaseConfig.class);
        mockedDriverManager = Mockito.mockStatic(DriverManager.class);



        mockConnection = mock(Connection.class);
    }

    @AfterAll
    static void tearDownAll() {

        if (listAppender != null) {
            insuranceLogger.removeAppender(listAppender);
            listAppender.stop();
        }

        if (mockedDatabaseConfig != null) mockedDatabaseConfig.close();
        if (mockedDriverManager != null) mockedDriverManager.close();

    }

    @BeforeEach
    void setUpForEachTest() {
        listAppender.clearEvents();



        mockedDatabaseConfig.reset();
        mockedDriverManager.reset();


    }

    @AfterEach
    void tearDownForEachTest() {
        // Немає потреби викликати .close() на MockedStatic тут
    }

    @Test
    void staticBlock_driverLoadsSuccessfully_logsInfo() {

        try {
            assertNotNull(Class.forName("DB.DatabaseConnectionManager"));
        } catch (ClassNotFoundException e) {
            fail("Клас DatabaseConnectionManager не знайдено: " + e.getMessage());
        }



    }



    @Test
    void getConnection_success_returnsConnection() throws SQLException {
        String testUrl = "jdbc:mysql://localhost:3306/testdb";
        String testUser = "testuser";
        String testPassword = "testpassword";

        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn(testUrl);
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn(testUser);
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn(testPassword);

        mockedDriverManager.when(() -> DriverManager.getConnection(testUrl, testUser, testPassword))
                .thenReturn(mockConnection);

        Connection conn = DatabaseConnectionManager.getConnection();

        assertNotNull(conn);
        assertSame(mockConnection, conn);
        assertTrue(listAppender.containsMessage(Level.INFO, "З'єднання з базою даних '" + testUrl + "' успішно встановлено для користувача '" + testUser + "'."));
        mockedDriverManager.verify(() -> DriverManager.getConnection(testUrl, testUser, testPassword));
    }

    @Test
    void getConnection_urlIsNull_throwsSQLExceptionAndLogsError() {

        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn(null);
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn("user");
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn("pass");

        SQLException exception = assertThrows(SQLException.class, DatabaseConnectionManager::getConnection);
        assertEquals("URL для підключення до БД не налаштовано.", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка конфігурації: URL для БД не вказано або не завантажено."));
    }

    @Test
    void getConnection_urlIsEmpty_throwsSQLExceptionAndLogsError() {
        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn("   ");
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn("user");
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn("pass");

        SQLException exception = assertThrows(SQLException.class, DatabaseConnectionManager::getConnection);
        assertEquals("URL для підключення до БД не налаштовано.", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка конфігурації: URL для БД не вказано або не завантажено."));
    }

    @Test
    void getConnection_usernameIsNull_throwsSQLExceptionAndLogsError() {
        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn("jdbc:mysql://localhost/db");
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn(null);
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn("pass");

        SQLException exception = assertThrows(SQLException.class, DatabaseConnectionManager::getConnection);
        assertEquals("Ім'я користувача для підключення до БД не налаштовано.", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка конфігурації: Ім'я користувача для БД не вказано або не завантажено."));
    }

    @Test
    void getConnection_passwordIsNull_logsWarningAndAttemptsConnection() throws SQLException {
        String testUrl = "jdbc:mysql://localhost/db_nopass";
        String testUser = "user_nopass";

        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn(testUrl);
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn(testUser);
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn(null);

        mockedDriverManager.when(() -> DriverManager.getConnection(testUrl, testUser, null))
                .thenReturn(mockConnection);

        Connection conn = DatabaseConnectionManager.getConnection();

        assertNotNull(conn);
        assertTrue(listAppender.containsMessage(Level.WARN, "Попередження конфігурації: Пароль для БД не вказано або не завантажено."));
        assertTrue(listAppender.containsMessage(Level.INFO, "З'єднання з базою даних '" + testUrl + "' успішно встановлено"));
        mockedDriverManager.verify(() -> DriverManager.getConnection(testUrl, testUser, null));
    }

    @Test
    void getConnection_driverManagerThrowsSQLException_rethrowsAndLogsError() throws SQLException {
        String testUrl = "jdbc:mysql://invalidhost/db";
        String testUser = "baduser";
        String testPassword = "badpassword";
        SQLException sqlEx = new SQLException("Не вдалося підключитися до сервера");

        mockedDatabaseConfig.when(DatabaseConfig::getDbUrl).thenReturn(testUrl);
        mockedDatabaseConfig.when(DatabaseConfig::getDbUsername).thenReturn(testUser);
        mockedDatabaseConfig.when(DatabaseConfig::getDbPassword).thenReturn(testPassword);

        mockedDriverManager.when(() -> DriverManager.getConnection(testUrl, testUser, testPassword))
                .thenThrow(sqlEx);

        SQLException thrown = assertThrows(SQLException.class, DatabaseConnectionManager::getConnection);

        assertSame(sqlEx, thrown);
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка підключення до бази даних: URL='" + testUrl + "', Користувач='" + testUser + "'. Помилка: " + sqlEx.getMessage()));
    }
}
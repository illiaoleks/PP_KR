package Config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class DatabaseConfigTest {

    private static ListAppender listAppender;

    private static org.apache.logging.log4j.core.Logger sutLogger;

    private static final org.apache.logging.log4j.Logger testClassLogger = LogManager.getLogger(DatabaseConfigTest.class);



    private static final String SUT_PROPERTIES_FILENAME = "db.properties";



    private static class ListAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        ListAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        public List<LogEvent> getEvents() {
            return events;
        }

        public void clearEvents() {
            events.clear();
        }

        public boolean containsMessage(Level level, String partialMessage) {
            return events.stream().anyMatch(event ->
                    event.getLevel().equals(level) &&
                            event.getMessage().getFormattedMessage().contains(partialMessage));
        }
    }

    @BeforeClass
    public static void setupClass() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);

        sutLogger = context.getLogger(DatabaseConfig.class.getName());

        listAppender = new ListAppender("ListAppenderForTest");
        listAppender.start();
        sutLogger.addAppender(listAppender);
        sutLogger.setLevel(Level.ALL); // Capture all levels from SUT


    }

    @AfterClass
    public static void teardownClass() {
        if (listAppender != null) {
            sutLogger.removeAppender(listAppender);
            listAppender.stop();
        }
    }

    private void clearSutProperties() throws NoSuchFieldException, IllegalAccessException {
        Field propertiesField = DatabaseConfig.class.getDeclaredField("properties");
        propertiesField.setAccessible(true);
        Properties propsInstance = (Properties) propertiesField.get(null);
        assertNotNull("DatabaseConfig.properties instance should not be null", propsInstance);
        propsInstance.clear();
    }


    private void simulatePropertiesLoad(String propertiesContent) {
        try {
            Field propertiesField = DatabaseConfig.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            Properties propsInstance = (Properties) propertiesField.get(null);
            assertNotNull("DatabaseConfig.properties instance should not be null for simulation", propsInstance);



            if (propertiesContent != null) {
                try (InputStream stream = new ByteArrayInputStream(propertiesContent.getBytes(StandardCharsets.UTF_8))) {
                    propsInstance.load(stream);
                }

                testClassLogger.info("TEST_SIMULATION: Content loaded into DatabaseConfig.properties for '{}'.", SUT_PROPERTIES_FILENAME);
            } else {

                testClassLogger.info("TEST_SIMULATION: DatabaseConfig.properties remains empty for '{}' (simulating not found/empty).", SUT_PROPERTIES_FILENAME);
            }
        } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
            throw new RuntimeException("Failed to simulate properties load: " + e.getMessage(), e);
        }
    }


    @Before
    public void setup() {
        listAppender.clearEvents();
        try {
            clearSutProperties();
        } catch (Exception e) {
            fail("Failed to clear SUT properties in @Before: " + e.getMessage());
        }
    }

    @After
    public void tearDown() {

    }

    @Test
    public void testAllPropertiesPresent() {
        String propsContent = "db.url=jdbc:mysql://host1:3306/db1\n" +
                "db.username=user1\n" +
                "db.password=pass1";
        simulatePropertiesLoad(propsContent);

        assertEquals("jdbc:mysql://host1:3306/db1", DatabaseConfig.getDbUrl());
        assertEquals("user1", DatabaseConfig.getDbUsername());
        assertEquals("pass1", DatabaseConfig.getDbPassword());

        List<LogEvent> logs = listAppender.getEvents();

        assertFalse("SUT should not log 'db.url' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("db.url")));
        assertFalse("SUT should not log 'db.username' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("db.username")));
        assertFalse("SUT should not log 'db.password' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("db.password")));
    }

    @Test
    public void testSomePropertiesMissing() {
        String propsContent = "db.url=jdbc:mysql://host2:3306/db2\n" +
                "#db.username is missing\n" +
                "db.password=pass2";
        simulatePropertiesLoad(propsContent);

        assertEquals("jdbc:mysql://host2:3306/db2", DatabaseConfig.getDbUrl());
        assertNull(DatabaseConfig.getDbUsername());
        assertEquals("pass2", DatabaseConfig.getDbPassword());

        List<LogEvent> logs = listAppender.getEvents();


        assertTrue("SUT should log 'db.username' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("Властивість 'db.username' не знайдена")));

        assertFalse("SUT should not log 'db.url' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("db.url")));
    }

    @Test
    public void testPropertiesFileCompletelyMissingOrEmptySimulation() {
        simulatePropertiesLoad(null);

        assertNull(DatabaseConfig.getDbUrl());
        assertNull(DatabaseConfig.getDbUsername());
        assertNull(DatabaseConfig.getDbPassword());

        List<LogEvent> logs = listAppender.getEvents();


        assertTrue("SUT should log 'db.url' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("Властивість 'db.url' не знайдена")));
        assertTrue("SUT should log 'db.username' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("Властивість 'db.username' не знайдена")));
        assertTrue("SUT should log 'db.password' not found warning",
                logs.stream().anyMatch(event -> event.getLoggerName().equals(DatabaseConfig.class.getName()) &&
                        event.getLevel() == Level.WARN &&
                        event.getMessage().getFormattedMessage().contains("Властивість 'db.password' не знайдена")));
    }

    @Test
    public void testIOExceptionDuringInternalLoadSimulation() {

        simulatePropertiesLoad(null);

        assertNull("URL should be null", DatabaseConfig.getDbUrl());
        assertNull("Username should be null", DatabaseConfig.getDbUsername());
        assertNull("Password should be null", DatabaseConfig.getDbPassword());

        List<LogEvent> logs = listAppender.getEvents();
        assertTrue("Getter for URL should warn",
                listAppender.containsMessage(Level.WARN, "Властивість 'db.url' не знайдена"));
        assertTrue("Getter for Username should warn",
                listAppender.containsMessage(Level.WARN, "Властивість 'db.username' не знайдена"));
        assertTrue("Getter for Password should warn",
                listAppender.containsMessage(Level.WARN, "Властивість 'db.password' не знайдена"));


        testClassLogger.info("testIOExceptionDuringInternalLoadSimulation: Verifying getter behavior when properties are empty, simulating aftermath of SUT's internal load IOException.");
    }
}
package Models;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StopTest {

    @Plugin(name = "TestListAppenderStop", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
    public static class TestListAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        protected TestListAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
            super(name, filter, layout, ignoreExceptions, properties);
        }

        @PluginFactory
        public static TestListAppender createAppender(
                @PluginAttribute("name") String name,
                @PluginElement("Layout") Layout<? extends Serializable> layout,
                @PluginElement("Filter") final Filter filter) {
            if (name == null) {
                LOGGER.error("No name provided for TestListAppenderStop");
                return null;
            }
            return new TestListAppender(name, filter, layout, true, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        public List<LogEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public void clear() {
            events.clear();
        }
    }

    private TestListAppender listAppender;
    private Stop validStop;

    private final long DEFAULT_ID = 1L;
    private final String DEFAULT_NAME = "Центральний Автовокзал";
    private final String DEFAULT_CITY = "Київ";

    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderStop", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();

        validStop = new Stop(DEFAULT_ID, DEFAULT_NAME, DEFAULT_CITY);
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppenderStop");
        listAppender.stop();
        listAppender.clear();
        ctx.updateLoggers();
    }

    private boolean findLogMessage(Level level, String partialMessage) {
        return listAppender.getEvents().stream().anyMatch(event ->
                event.getLevel() == level &&
                        event.getMessage().getFormattedMessage().contains(partialMessage)
        );
    }

    private boolean findExactLogMessage(Level level, String exactMessage) {
        return listAppender.getEvents().stream().anyMatch(event ->
                event.getLevel() == level &&
                        event.getMessage().getFormattedMessage().equals(exactMessage)
        );
    }


    @Test
    void constructor_validArguments_createsInstanceAndLogs() {

        assertNotNull(validStop);
        assertEquals(DEFAULT_ID, validStop.getId());
        assertEquals(DEFAULT_NAME, validStop.getName());
        assertEquals(DEFAULT_CITY, validStop.getCity());


        listAppender.clear();
        Stop testStop = new Stop(2L, "Інша Зупинка", "Інше Місто");
        assertNotNull(testStop);
        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Stop з ID: 2"));
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Stop успішно створено: ID=2, Назва=Інша Зупинка, Місто=Інше Місто"));
    }

    private static Stream<Arguments> invalidConstructorArguments() {
        return Stream.of(
                Arguments.of(1L, null, "Київ", "Назва зупинки не може бути порожньою."),
                Arguments.of(1L, "  ", "Київ", "Назва зупинки не може бути порожньою."),
                Arguments.of(1L, "", "Київ", "Назва зупинки не може бути порожньою."),
                Arguments.of(1L, "Зупинка", null, "Місто не може бути порожнім."),
                Arguments.of(1L, "Зупинка", "  ", "Місто не може бути порожнім."),
                Arguments.of(1L, "Зупинка", "", "Місто не може бути порожнім.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void constructor_invalidArguments_throwsIllegalArgumentExceptionAndLogsError(
            long id, String name, String city, String expectedMessage) {

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new Stop(id, name, city)
        );
        assertEquals(expectedMessage, exception.getMessage());

        if (name == null || name.trim().isEmpty()) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Stop: Назва зупинки (name) не може бути порожньою для ID: " + id));
        }
        if (city == null || city.trim().isEmpty()) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Stop: Місто (city) не може бути порожнім для ID: " + id));
        }
        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Stop з ID: " + id));
    }

    @Test
    void getId_returnsCorrectId() {
        assertEquals(DEFAULT_ID, validStop.getId());
    }

    @Test
    void setId_changesIdAndLogsTrace() {
        validStop.setId(99L);
        assertEquals(99L, validStop.getId());
        assertFalse(findExactLogMessage(Level.TRACE, "Встановлення ID зупинки " + DEFAULT_ID + " на: 99"));
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals(DEFAULT_NAME, validStop.getName());
    }



    @Test
    void getCity_returnsCorrectCity() {
        assertEquals(DEFAULT_CITY, validStop.getCity());
    }

    @Test
    void setCity_validCity_changesCityAndLogsTrace() {
        String newCity = "Львів";
        validStop.setCity(newCity);
        assertEquals(newCity, validStop.getCity());
        assertFalse(findExactLogMessage(Level.TRACE, "Зміна міста зупинки ID " + DEFAULT_ID + "."));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void setCity_nullOrEmptyOrBlankCity_setsItAndLogsWarnAndTrace(String invalidCity) {
        validStop.setCity(invalidCity);

        assertEquals(invalidCity, validStop.getCity());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожнє місто для зупинки ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна міста зупинки ID " + DEFAULT_ID));
    }

    @Test
    void toString_withValidData_returnsCorrectFormat() {
        String expected = String.format("ID: %d, Назва: %s, Місто: %s", DEFAULT_ID, DEFAULT_NAME, DEFAULT_CITY);
        assertEquals(expected, validStop.toString());
    }

    @Test
    void toString_withNullNameInObjectState_returnsND() {

        validStop.setCity(null);
        listAppender.clear();
        String expected = String.format("ID: %d, Назва: %s, Місто: %s", DEFAULT_ID, DEFAULT_NAME, "н/д");
        assertEquals(expected, validStop.toString());
    }

    @Test
    void toString_withNullCityInObjectState_returnsND() {
        validStop.setCity(null);
        listAppender.clear();
        String expected = String.format("ID: %d, Назва: %s, Місто: %s", DEFAULT_ID, DEFAULT_NAME, "н/д");
        assertEquals(expected, validStop.toString());
    }


    @Test
    void equals_sameObject_returnsTrue() {
        assertTrue(validStop.equals(validStop));
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertFalse(validStop.equals(null));
    }

    @Test
    void equals_differentClass_returnsFalse() {
        assertFalse(validStop.equals(new Object()));
    }

    @Test
    void equals_sameId_returnsTrue() {
        Stop anotherStop = new Stop(DEFAULT_ID, "Інша Назва", "Інше Місто");
        assertTrue(validStop.equals(anotherStop));
    }

    @Test
    void equals_differentId_returnsFalse() {
        Stop anotherStop = new Stop(DEFAULT_ID + 1, DEFAULT_NAME, DEFAULT_CITY);
        assertFalse(validStop.equals(anotherStop));
    }

    @Test
    void hashCode_basedOnId() {
        Stop anotherStopWithSameId = new Stop(DEFAULT_ID, "Різна Назва", "Різне Місто");
        assertEquals(validStop.hashCode(), anotherStopWithSameId.hashCode());

        Stop anotherStopWithDifferentId = new Stop(DEFAULT_ID + 1, DEFAULT_NAME, DEFAULT_CITY);
        assertNotEquals(validStop.hashCode(), anotherStopWithDifferentId.hashCode());
    }
}
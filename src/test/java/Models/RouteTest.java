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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;


@ExtendWith(MockitoExtension.class)
class RouteTest {

    @Plugin(name = "TestListAppenderRoute", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderRoute");
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
    private Route validRoute;
    private Stop stopA, stopB, stopC, stopD;

    private final long DEFAULT_ID = 1L;

    @Mock
    private Stop mockDepartureStop;
    @Mock
    private Stop mockDestinationStop;
    @Mock
    private Stop mockIntermediateStop1;
    @Mock
    private Stop mockIntermediateStop2;


    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderRoute", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();

        stopA = new Stop(10L, "Вокзал", "Київ");
        stopB = new Stop(20L, "Центр", "Львів");
        stopC = new Stop(30L, "Ринок", "Одеса");
        stopD = new Stop(40L, "Аеропорт", "Харків");

        Mockito.lenient().when(mockDepartureStop.getCity()).thenReturn("МістоА");
        Mockito.lenient().when(mockDestinationStop.getCity()).thenReturn("МістоБ");
        Mockito.lenient().when(mockIntermediateStop1.getCity()).thenReturn("МістоС1");
        Mockito.lenient().when(mockIntermediateStop2.getCity()).thenReturn("МістоС2");


        validRoute = new Route(DEFAULT_ID, stopA, stopB, Arrays.asList(stopC));
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppenderRoute");
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
        listAppender.clear();
        Route route = new Route(2L, stopA, stopB, Arrays.asList(stopC));
        assertNotNull(route);
        assertEquals(2L, route.getId());
        assertEquals(stopA, route.getDepartureStop());
        assertEquals(stopB, route.getDestinationStop());
        assertEquals(1, route.getIntermediateStops().size());
        assertEquals(stopC, route.getIntermediateStops().get(0));

        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Route з ID: 2"));
        assertFalse(findLogMessage(Level.TRACE, "Для Route ID: 2 встановлено 1 проміжних зупинок."));
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Route успішно створено: ID=2"));
    }

    @Test
    void constructor_nullIntermediateStops_initializesEmptyListAndLogs() {
        listAppender.clear();
        Route route = new Route(3L, stopA, stopB, null);
        assertNotNull(route.getIntermediateStops());
        assertTrue(route.getIntermediateStops().isEmpty());
        assertFalse(findLogMessage(Level.TRACE, "Для Route ID: 3 список проміжних зупинок був null, ініціалізовано порожнім списком."));
    }

    @Test
    void constructor_emptyIntermediateStops_initializesEmptyListAndLogs() {
        listAppender.clear();
        Route route = new Route(4L, stopA, stopB, Collections.emptyList());
        assertNotNull(route.getIntermediateStops());
        assertTrue(route.getIntermediateStops().isEmpty());
        assertFalse(findLogMessage(Level.TRACE, "Для Route ID: 4 встановлено 0 проміжних зупинок."));
    }

    @Test
    void constructor_departureEqualsDestination_logsWarn() {
        listAppender.clear();

        Route route = new Route(5L, stopA, stopA, null);
        assertTrue(findLogMessage(Level.WARN, "Увага при створенні Route (ID: 5): Зупинка відправлення та зупинка призначення однакові."));
    }

    private static Stream<Arguments> invalidConstructorArguments() {

        Stop validStop1 = mock(Stop.class);
        Stop validStop2 = mock(Stop.class);
        return Stream.of(
                Arguments.of(null, validStop2, Collections.emptyList(), "Зупинка відправлення не може бути null."),
                Arguments.of(validStop1, null, Collections.emptyList(), "Зупинка призначення не може бути null.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void constructor_nullStops_throwsIllegalArgumentExceptionAndLogsError(
            Stop departure, Stop destination, List<Stop> intermediate, String expectedMessage) {

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new Route(6L, departure, destination, intermediate)
        );
        assertEquals(expectedMessage, exception.getMessage());

        if (departure == null) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Route: Зупинка відправлення (departureStop) не може бути null для ID: 6"));
        }
        if (destination == null) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Route: Зупинка призначення (destinationStop) не може бути null для ID: 6"));
        }
        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Route з ID: 6"));
    }

    @Test
    void getId_returnsCorrectId() {
        assertEquals(DEFAULT_ID, validRoute.getId());
    }

    @Test
    void setId_changesIdAndLogsTrace() {
        validRoute.setId(99L);
        assertEquals(99L, validRoute.getId());
        assertFalse(findExactLogMessage(Level.TRACE, "Встановлення ID маршруту " + DEFAULT_ID + " на: 99"));
    }

    @Test
    void getDepartureStop_returnsCorrectStop() {
        assertEquals(stopA, validRoute.getDepartureStop());
    }

    @Test
    void setDepartureStop_validStop_changesStopAndLogs() {
        Stop newDeparture = stopD;
        validRoute.setDepartureStop(newDeparture);
        assertEquals(newDeparture, validRoute.getDepartureStop());
        assertFalse(findLogMessage(Level.TRACE, "Зміна зупинки відправлення для маршруту ID " + DEFAULT_ID));
    }

    @Test
    void setDepartureStop_nullStop_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validRoute.setDepartureStop(null)
        );
        assertEquals("Зупинка відправлення не може бути null.", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null зупинку відправлення для маршруту ID: " + DEFAULT_ID));
    }

    @Test
    void setDepartureStop_equalsDestination_logsWarn() {

        Stop stopBEquivalent = new Stop(stopB.getId(), "Інша назва, те саме ID", "Інше місто, те саме ID");

        validRoute.setDepartureStop(stopBEquivalent);
        assertEquals(stopBEquivalent, validRoute.getDepartureStop());
        assertTrue(findLogMessage(Level.WARN, "Увага при зміні зупинки відправлення маршруту ID " + DEFAULT_ID + ": Нова зупинка відправлення збігається з поточною зупинкою призначення."));
    }


    @Test
    void getDestinationStop_returnsCorrectStop() {
        assertEquals(stopB, validRoute.getDestinationStop());
    }

    @Test
    void setDestinationStop_validStop_changesStopAndLogs() {
        Stop newDestination = stopD;
        validRoute.setDestinationStop(newDestination);
        assertEquals(newDestination, validRoute.getDestinationStop());
        assertFalse(findLogMessage(Level.TRACE, "Зміна зупинки призначення для маршруту ID " + DEFAULT_ID));
    }

    @Test
    void setDestinationStop_nullStop_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validRoute.setDestinationStop(null)
        );
        assertEquals("Зупинка призначення не може бути null.", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null зупинку призначення для маршруту ID: " + DEFAULT_ID));
    }

    @Test
    void setDestinationStop_equalsDeparture_logsWarn() {

        Stop stopAEquivalent = new Stop(stopA.getId(), "Інша назва для A", "Інше місто для A");

        validRoute.setDestinationStop(stopAEquivalent);
        assertEquals(stopAEquivalent, validRoute.getDestinationStop());
        assertTrue(findLogMessage(Level.WARN, "Увага при зміні зупинки призначення маршруту ID " + DEFAULT_ID + ": Нова зупинка призначення збігається з поточною зупинкою відправлення."));
    }

    @Test
    void getIntermediateStops_returnsCopyOfList() {
        List<Stop> originalStops = validRoute.getIntermediateStops();
        assertNotNull(originalStops);
        assertEquals(1, originalStops.size());
        assertEquals(stopC, originalStops.get(0));

        originalStops.add(stopD);

        List<Stop> currentStopsInRoute = validRoute.getIntermediateStops();
        assertEquals(1, currentStopsInRoute.size(), "Оригінальний список у Route не повинен змінюватися.");
        assertFalse(currentStopsInRoute.contains(stopD), "Оригінальний список не повинен містити додану зупинку.");
    }

    @Test
    void getIntermediateStops_whenInternalListIsNull_returnsEmptyList() {
        Route routeWithNullInternals = new Route(7L, stopA, stopB, null);
        List<Stop> stops = routeWithNullInternals.getIntermediateStops();
        assertNotNull(stops);
        assertTrue(stops.isEmpty());
    }



    @Test
    void setIntermediateStops_nullList_setsEmptyListAndLogs() {
        validRoute.setIntermediateStops(null);
        assertNotNull(validRoute.getIntermediateStops());
        assertTrue(validRoute.getIntermediateStops().isEmpty());
        assertFalse(findLogMessage(Level.TRACE, "Список проміжних зупинок для маршруту ID " + DEFAULT_ID + " очищено (був null)."));
    }

    @Test
    void getFullRouteDescription_noIntermediateStops() {
        Route simpleRoute = new Route(8L, stopA, stopB, null);
        assertEquals("Київ -> Львів", simpleRoute.getFullRouteDescription());
    }

    @Test
    void getFullRouteDescription_withIntermediateStops() {

        assertEquals("Київ -> Одеса -> Львів", validRoute.getFullRouteDescription());
    }

    @Test
    void getFullRouteDescription_withMultipleIntermediateStops() {
        Route complexRoute = new Route(9L, stopA, stopB, Arrays.asList(stopC, stopD));
        assertEquals("Київ -> Одеса -> Харків -> Львів", complexRoute.getFullRouteDescription());
    }



    @Test
    void getFullRouteDescription_intermediateStopNullCity() {
        Mockito.reset(mockIntermediateStop1);
        Mockito.lenient().when(mockIntermediateStop1.getCity()).thenReturn(null);

        Mockito.lenient().when(mockIntermediateStop1.getId()).thenReturn(70L);

        Route route = new Route(12L, stopA, stopB, Arrays.asList(mockIntermediateStop1));
        assertEquals("Київ -> Невідомо -> Львів", route.getFullRouteDescription());
    }

    @Test
    void getFullRouteDescription_intermediateStopIsNull() {
        List<Stop> stopsWithNull = new ArrayList<>();
        stopsWithNull.add(null);
        Route route = new Route(13L, stopA, stopB, stopsWithNull);
        assertEquals("Київ -> Невідомо -> Львів", route.getFullRouteDescription());
    }



    @Test
    void toString_returnsCorrectFormat() {
        String expectedDesc = "Київ -> Одеса -> Львів";
        String expected = "Маршрут ID " + DEFAULT_ID + ": " + expectedDesc;
        assertEquals(expected, validRoute.toString());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertTrue(validRoute.equals(validRoute));
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertFalse(validRoute.equals(null));
    }

    @Test
    void equals_differentClass_returnsFalse() {
        assertFalse(validRoute.equals(new Object()));
    }

    @Test
    void equals_sameId_returnsTrue() {
        Route anotherRoute = new Route(DEFAULT_ID, stopD, stopA, Collections.emptyList());
        assertTrue(validRoute.equals(anotherRoute));
    }

    @Test
    void equals_differentId_returnsFalse() {
        Route anotherRoute = new Route(DEFAULT_ID + 1, stopA, stopB, null);
        assertFalse(validRoute.equals(anotherRoute));
    }

    @Test
    void hashCode_basedOnId() {
        Route anotherRouteWithSameId = new Route(DEFAULT_ID, stopB, stopC, null);
        assertEquals(validRoute.hashCode(), anotherRouteWithSameId.hashCode());

        Route anotherRouteWithDifferentId = new Route(DEFAULT_ID + 5, stopA, stopB, null);
        assertNotEquals(validRoute.hashCode(), anotherRouteWithDifferentId.hashCode());
    }
}
package Model;

import Models.Enums.FlightStatus;
import Models.Flight;
import Models.Route;
import Models.Stop;
import UI.Model.FlightsTableModel;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightsTableModelTest {

    @Plugin(name = "TestListAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppender");
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

    private FlightsTableModel model;
    private List<Flight> sampleFlights;
    private Flight flight1, flight2;
    private Route route1, route2;
    private Stop stopA, stopB, stopC, stopD;

    private static final DateTimeFormatter TABLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TestListAppender listAppender;

    @Mock
    private Flight mockedFlight;
    @Mock
    private Route mockedRoute;
    @Mock
    private FlightStatus mockedStatus;

    private boolean tableDataChangedFired;
    private TableModelListener testListener;

    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppender", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();


        stopA = new Stop(1L, "Центральний Автовокзал", "Київ");
        stopB = new Stop(2L, "Стрийський Автовокзал", "Львів");
        stopC = new Stop(3L, "Привоз", "Одеса");
        stopD = new Stop(4L, "АС Умань", "Умань");


        route1 = new Route(1L, stopA, stopB, Collections.emptyList());
        route2 = new Route(2L, stopA, stopC, List.of(stopD));

        LocalDateTime dep1 = LocalDateTime.of(2024, 8, 15, 10, 0);
        LocalDateTime arr1 = LocalDateTime.of(2024, 8, 15, 18, 0);
        flight1 = new Flight(101L, route1, dep1, arr1, 50, FlightStatus.PLANNED, "Mercedes", new BigDecimal("500.00"));

        LocalDateTime dep2 = LocalDateTime.of(2024, 8, 16, 12, 30);
        LocalDateTime arr2 = LocalDateTime.of(2024, 8, 16, 20, 45);
        flight2 = new Flight(102L, route2, dep2, arr2, 45, FlightStatus.DEPARTED, "Neoplan", new BigDecimal("650.50"));

        sampleFlights = new ArrayList<>(List.of(flight1, flight2));

        tableDataChangedFired = false;
        testListener = e -> {
            if (e.getType() == TableModelEvent.UPDATE &&
                    e.getFirstRow() == 0 &&
                    e.getLastRow() == Integer.MAX_VALUE &&
                    e.getColumn() == TableModelEvent.ALL_COLUMNS) {
                tableDataChangedFired = true;
            }
        };
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppender");
        listAppender.stop();
        listAppender.clear();
        ctx.updateLoggers();
        if (model != null && testListener != null) {
            model.removeTableModelListener(testListener);
        }
    }

    private List<LogEvent> getLogEvents() {
        return listAppender.getEvents();
    }

    private boolean findLogMessage(Level level, String partialMessage) {
        return getLogEvents().stream().anyMatch(event ->
                event.getLevel() == level &&
                        event.getMessage().getFormattedMessage().contains(partialMessage)
        );
    }

    @Test
    void constructor_withNullFlights_initializesEmptyListAndLogs() {
        model = new FlightsTableModel(null);
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація FlightsTableModel з null списком рейсів. Створюється порожній список."));
    }

    @Test
    void constructor_withEmptyFlights_initializesEmptyListAndLogs() {
        model = new FlightsTableModel(new ArrayList<>());
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація FlightsTableModel з 0 рейсами."));
    }

    @Test
    void constructor_withPopulatedFlights_initializesCorrectlyAndLogs() {
        List<Flight> initialFlights = new ArrayList<>(sampleFlights);
        model = new FlightsTableModel(initialFlights);
        assertNotNull(model);
        assertEquals(2, model.getRowCount());
        assertEquals(flight1, model.getFlightAt(0));
        assertEquals(flight2, model.getFlightAt(1));
        initialFlights.clear();
        assertEquals(2, model.getRowCount(), "Модель повинна мати власну копію списку.");
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація FlightsTableModel з 2 рейсами."));
    }

    @Test
    void setFlights_withNullList_clearsListAndLogsAndFiresEvent() {
        model = new FlightsTableModel(sampleFlights);
        model.addTableModelListener(testListener);
        model.setFlights(null);
        assertEquals(0, model.getRowCount());
        assertTrue(tableDataChangedFired, "Подія fireTableDataChanged не була викликана");
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null список рейсів в FlightsTableModel. Список буде очищено."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці рейсів оновлено."));
    }

    @Test
    void setFlights_withNewList_updatesListAndLogsAndFiresEvent() {
        model = new FlightsTableModel(Collections.emptyList());
        model.addTableModelListener(testListener);
        model.setFlights(sampleFlights);
        assertEquals(2, model.getRowCount());
        assertEquals(flight1, model.getFlightAt(0));
        assertTrue(tableDataChangedFired, "Подія fireTableDataChanged не була викликана");
        assertTrue(findLogMessage(Level.INFO, "Встановлено новий список з 2 рейсів в FlightsTableModel."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці рейсів оновлено."));
    }

    @Test
    void getFlightAt_validIndex_returnsFlightAndLogsTrace() {
        model = new FlightsTableModel(sampleFlights);
        Flight result = model.getFlightAt(0);
        assertEquals(flight1, result);
        assertFalse(findLogMessage(Level.TRACE, "Отримання рейсу за індексом 0: ID 101"));
    }

    @Test
    void getFlightAt_negativeIndex_returnsNullAndLogsWarn() {
        model = new FlightsTableModel(sampleFlights);
        Flight result = model.getFlightAt(-1);
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати рейс за недійсним індексом рядка: -1. Розмір списку: 2"));
    }

    @Test
    void getFlightAt_indexOutOfBounds_returnsNullAndLogsWarn() {
        model = new FlightsTableModel(sampleFlights);
        Flight result = model.getFlightAt(sampleFlights.size());
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати рейс за недійсним індексом рядка: 2. Розмір списку: 2"));
    }

    @Test
    void getRowCount_returnsCorrectCount() {
        model = new FlightsTableModel(sampleFlights);
        assertEquals(sampleFlights.size(), model.getRowCount());
        model = new FlightsTableModel(Collections.emptyList());
        assertEquals(0, model.getRowCount());
    }

    @Test
    void getColumnCount_returnsCorrectCount() {
        model = new FlightsTableModel(Collections.emptyList());
        assertEquals(8, model.getColumnCount());
    }

    @ParameterizedTest
    @CsvSource({
            "0, ID",
            "1, Маршрут",
            "2, Відправлення",
            "3, Прибуття",
            "4, Місць",
            "5, Автобус",
            "6, Ціна",
            "7, Статус"
    })
    void getColumnName_validIndex_returnsCorrectName(int index, String expectedName) {
        model = new FlightsTableModel(Collections.emptyList());
        assertEquals(expectedName, model.getColumnName(index));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 8})
    void getColumnName_invalidIndex_returnsEmptyStringAndLogsWarn(int invalidIndex) {
        model = new FlightsTableModel(Collections.emptyList());
        assertEquals("", model.getColumnName(invalidIndex));
        assertTrue(findLogMessage(Level.WARN, "Запит назви стовпця для рейсів за недійсним індексом: " + invalidIndex));
    }

    @ParameterizedTest
    @CsvSource({
            "0, java.lang.Long",
            "1, java.lang.String",
            "2, java.lang.String",
            "3, java.lang.String",
            "4, java.lang.Integer",
            "5, java.lang.String",
            "6, java.math.BigDecimal",
            "7, java.lang.String"
    })
    void getColumnClass_returnsCorrectClass(int index, String expectedClassName) throws ClassNotFoundException {
        model = new FlightsTableModel(Collections.emptyList());
        assertEquals(Class.forName(expectedClassName), model.getColumnClass(index));
    }

    @Test
    void getValueAt_invalidRowIndex_returnsErrorStringAndLogsError() {
        model = new FlightsTableModel(sampleFlights);
        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(-1, 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка -1 при запиті значення для таблиці рейсів. Кількість рядків: 2"));

        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(sampleFlights.size(), 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка 2 при запиті значення для таблиці рейсів. Кількість рядків: 2"));
    }

    @Test
    void getValueAt_flightIsNullInList_returnsErrorStringAndLogsError() {
        List<Flight> flightsWithNull = new ArrayList<>();
        flightsWithNull.add(null);
        model = new FlightsTableModel(flightsWithNull);
        assertEquals("ПОМИЛКА: NULL РЕЙС", model.getValueAt(0, 0));
        assertTrue(findLogMessage(Level.ERROR, "Об'єкт Flight є null для рядка 0 при запиті значення для таблиці рейсів."));
    }

    @Test
    void getValueAt_invalidColumnIndex_returnsErrorStringAndLogsWarn() {
        model = new FlightsTableModel(sampleFlights);
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, -1));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для рейсів: -1 (рядок 0)"));
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, 8));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для рейсів: 8 (рядок 0)"));
    }

    @Test
    void getValueAt_validCell_returnsCorrectValue() {
        model = new FlightsTableModel(sampleFlights);

        assertEquals(101L, model.getValueAt(0, 0));
        assertEquals("Київ -> Львів", model.getValueAt(0, 1));
        assertEquals(flight1.getDepartureDateTime().format(TABLE_DATE_TIME_FORMATTER), model.getValueAt(0, 2));
        assertEquals(flight1.getArrivalDateTime().format(TABLE_DATE_TIME_FORMATTER), model.getValueAt(0, 3));
        assertEquals(50, model.getValueAt(0, 4));
        assertEquals("Mercedes", model.getValueAt(0, 5));
        assertEquals(new BigDecimal("500.00"), model.getValueAt(0, 6));
        assertEquals(FlightStatus.PLANNED.getDisplayName(), model.getValueAt(0, 7));


        assertEquals(102L, model.getValueAt(1, 0));
        assertEquals("Київ -> Умань -> Одеса", model.getValueAt(1, 1));
    }



    @Test
    void getValueAt_exceptionDuringGetter_returnsErrorStringAndLogsError() {

        Route faultyRoute = mock(Route.class);
        when(faultyRoute.getFullRouteDescription()).thenThrow(new RuntimeException("Test exception in route"));

        Flight flightWithFaultyRoute = new Flight(301L, faultyRoute,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                30, FlightStatus.PLANNED, "FaultyBus", BigDecimal.ONE);

        model = new FlightsTableModel(Collections.singletonList(flightWithFaultyRoute));

        assertEquals("ПОМИЛКА ДАНИХ", model.getValueAt(0, 1));
        assertTrue(findLogMessage(Level.ERROR, "Помилка при отриманні значення для комірки рейсів [0, 1], рейс ID 301"));
        assertTrue(getLogEvents().stream().anyMatch(e -> e.getThrown() != null && e.getThrown().getMessage().contains("Test exception in route")));
    }
}
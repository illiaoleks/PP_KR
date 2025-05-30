package Model;

import Models.Enums.BenefitType;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.*;
import UI.Model.PassengerHistoryTableModel;
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
class PassengerHistoryTableModelTest {

    @Plugin(name = "TestListAppenderHistory", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderHistory");
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

    private PassengerHistoryTableModel model;
    private List<Ticket> sampleTickets;
    private Ticket ticket1, ticket2;
    private Flight flight1, flight2;
    private Passenger passenger1;
    private Route route1, route2;
    private Stop stopA, stopB, stopC, stopD;

    private static final DateTimeFormatter HISTORY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private TestListAppender listAppender;

    @Mock
    private Ticket mockedTicket;
    @Mock
    private Flight mockedFlight;
    @Mock
    private Route mockedRoute;
    @Mock
    private TicketStatus mockedTicketStatus;

    private boolean tableDataChangedFired;
    private TableModelListener testListener;

    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderHistory", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();


        passenger1 = new Passenger(1L, "Іван Іванов", "СН123456", "Паспорт",
                "0991234567", "ivan@example.com", BenefitType.NONE);

        stopA = new Stop(1L, "Центральний Автовокзал", "Київ");
        stopB = new Stop(2L, "Стрийський Автовокзал", "Львів");
        stopC = new Stop(3L, "Привоз", "Одеса");
        stopD = new Stop(4L, "АС Умань", "Умань");

        route1 = new Route(1L, stopA, stopB, Collections.emptyList());
        route2 = new Route(2L, stopA, stopC, List.of(stopD));

        LocalDateTime dep1 = LocalDateTime.of(2023, 10, 20, 14, 0);
        LocalDateTime arr1 = LocalDateTime.of(2023, 10, 20, 22, 0);
        flight1 = new Flight(101L, route1, dep1, arr1, 50, FlightStatus.ARRIVED, "Mercedes", new BigDecimal("450.00"));

        LocalDateTime dep2 = LocalDateTime.of(2023, 11, 5, 9, 30);
        LocalDateTime arr2 = LocalDateTime.of(2023, 11, 5, 17, 15);
        flight2 = new Flight(102L, route2, dep2, arr2, 45, FlightStatus.ARRIVED, "Neoplan", new BigDecimal("600.00"));

        ticket1 = new Ticket(501L, flight1, passenger1, "1A", LocalDateTime.of(2023, 10, 1, 10, 0), new BigDecimal("450.00"), TicketStatus.USED);
        ticket2 = new Ticket(502L, flight2, passenger1, "5B", LocalDateTime.of(2023, 10, 15, 12, 0), new BigDecimal("600.00"), TicketStatus.USED);
        ticket2.setPurchaseDateTime(LocalDateTime.of(2023, 10, 15, 12, 5));

        sampleTickets = new ArrayList<>(List.of(ticket1, ticket2));

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
        loggerConfig.removeAppender("TestListAppenderHistory");
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
    void constructor_withNullTickets_initializesEmptyListAndLogs() {
        model = new PassengerHistoryTableModel(null);
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація PassengerHistoryTableModel з null списком квитків. Створюється порожній список."));
    }

    @Test
    void constructor_withEmptyTickets_initializesEmptyListAndLogs() {
        model = new PassengerHistoryTableModel(new ArrayList<>());
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація PassengerHistoryTableModel з 0 квитками."));
    }



    @Test
    void setTickets_withNullList_clearsListAndLogsAndFiresEvent() {
        model = new PassengerHistoryTableModel(sampleTickets);
        model.addTableModelListener(testListener);
        model.setTickets(null);
        assertEquals(0, model.getRowCount());
        assertTrue(tableDataChangedFired, "Подія fireTableDataChanged не була викликана");
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null список квитків в PassengerHistoryTableModel. Список буде очищено."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці історії пасажира оновлено."));
    }

    @Test
    void setTickets_withNewList_updatesListAndLogsAndFiresEvent() {
        model = new PassengerHistoryTableModel(Collections.emptyList());
        model.addTableModelListener(testListener);
        model.setTickets(sampleTickets);
        assertEquals(2, model.getRowCount());
        assertTrue(tableDataChangedFired, "Подія fireTableDataChanged не була викликана");
        assertTrue(findLogMessage(Level.INFO, "Встановлено новий список з 2 квитків для історії пасажира."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці історії пасажира оновлено."));
    }

    @Test
    void getRowCount_returnsCorrectCount() {
        model = new PassengerHistoryTableModel(sampleTickets);
        assertEquals(sampleTickets.size(), model.getRowCount());
        model = new PassengerHistoryTableModel(Collections.emptyList());
        assertEquals(0, model.getRowCount());
    }

    @Test
    void getColumnCount_returnsCorrectCount() {
        model = new PassengerHistoryTableModel(Collections.emptyList());
        assertEquals(7, model.getColumnCount());
    }

    @ParameterizedTest
    @CsvSource({
            "0, ID Квитка",
            "1, Рейс (ID)",
            "2, Маршрут",
            "3, Дата відпр.",
            "4, Місце",
            "5, Ціна",
            "6, Статус квитка"
    })
    void getColumnName_validIndex_returnsCorrectName(int index, String expectedName) {
        model = new PassengerHistoryTableModel(Collections.emptyList());
        assertEquals(expectedName, model.getColumnName(index));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 7})
    void getColumnName_invalidIndex_returnsEmptyStringAndLogsWarn(int invalidIndex) {
        model = new PassengerHistoryTableModel(Collections.emptyList());
        assertEquals("", model.getColumnName(invalidIndex));
        assertTrue(findLogMessage(Level.WARN, "Запит назви стовпця для історії пасажира за недійсним індексом: " + invalidIndex));
    }

    @Test
    void getValueAt_invalidRowIndex_returnsErrorStringAndLogsError() {
        model = new PassengerHistoryTableModel(sampleTickets);
        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(-1, 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка -1 при запиті значення для таблиці історії пасажира. Кількість рядків: 2"));

        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(sampleTickets.size(), 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка 2 при запиті значення для таблиці історії пасажира. Кількість рядків: 2"));
    }

    @Test
    void getValueAt_ticketIsNullInList_returnsErrorStringAndLogsError() {
        List<Ticket> ticketsWithNull = new ArrayList<>();
        ticketsWithNull.add(null);
        model = new PassengerHistoryTableModel(ticketsWithNull);
        assertEquals("ПОМИЛКА: NULL КВИТОК", model.getValueAt(0, 0));
        assertTrue(findLogMessage(Level.ERROR, "Об'єкт Ticket є null для рядка 0 при запиті значення для таблиці історії пасажира."));
    }



    @Test
    void getValueAt_invalidColumnIndex_returnsErrorStringAndLogsWarn() {
        model = new PassengerHistoryTableModel(sampleTickets);
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, -1));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для історії пасажира: -1 (рядок 0)"));
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, 7));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для історії пасажира: 7 (рядок 0)"));
    }

    @Test
    void getValueAt_validCell_returnsCorrectValue() {
        model = new PassengerHistoryTableModel(sampleTickets);

        assertEquals(501L, model.getValueAt(0, 0));
        assertEquals(101L, model.getValueAt(0, 1));
        assertEquals("Київ -> Львів", model.getValueAt(0, 2));
        assertEquals(flight1.getDepartureDateTime().format(HISTORY_DATE_FORMATTER), model.getValueAt(0, 3));
        assertEquals("1A", model.getValueAt(0, 4));
        assertEquals(new BigDecimal("450.00"), model.getValueAt(0, 5));
        assertEquals(TicketStatus.USED.getDisplayName(), model.getValueAt(0, 6));


        assertEquals(502L, model.getValueAt(1, 0));
        assertEquals(102L, model.getValueAt(1, 1));
        assertEquals("Київ -> Умань -> Одеса", model.getValueAt(1, 2));
        assertEquals(flight2.getDepartureDateTime().format(HISTORY_DATE_FORMATTER), model.getValueAt(1, 3));
    }



    @Test
    void getValueAt_exceptionDuringGetter_returnsErrorStringAndLogsError() {
        Route faultyRoute = mock(Route.class);
        when(faultyRoute.getFullRouteDescription()).thenThrow(new RuntimeException("Test exception in route getter"));

        Flight flightWithFaultyRoute = new Flight(301L, faultyRoute,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                30, FlightStatus.PLANNED, "FaultyBus", BigDecimal.ONE);

        Ticket ticketWithFaultyFlight = new Ticket(801L, flightWithFaultyRoute, passenger1, "X1",
                LocalDateTime.now(), BigDecimal.TEN, TicketStatus.BOOKED);

        model = new PassengerHistoryTableModel(Collections.singletonList(ticketWithFaultyFlight));

        assertEquals("ПОМИЛКА ДАНИХ", model.getValueAt(0, 2));
        assertTrue(findLogMessage(Level.ERROR, "Помилка при отриманні значення для комірки історії пасажира [0, 2], квиток ID 801"));
        assertTrue(getLogEvents().stream().anyMatch(e -> e.getThrown() != null && e.getThrown().getMessage().contains("Test exception in route getter")));
    }
}
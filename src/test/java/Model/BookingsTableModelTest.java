package Model;

import Models.Enums.BenefitType;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.*;
import UI.Model.BookingsTableModel;
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
class BookingsTableModelTest {

    @Plugin(name = "TestListAppenderBookings", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderBookings");
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

    private BookingsTableModel model;
    private List<Ticket> sampleTickets;
    private Ticket ticket1, ticket2, ticket3_older;
    private Flight flight1, flight2;
    private Passenger passenger1, passenger2;
    private Route route1, route2;
    private Stop stopA, stopB, stopC, stopD;

    private static final DateTimeFormatter TABLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    private TestListAppender listAppender;

    @Mock
    private Ticket mockedTicket;
    @Mock
    private Flight mockedFlight;
    @Mock
    private Passenger mockedPassenger;
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
        listAppender = TestListAppender.createAppender("TestListAppenderBookings", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();

        passenger1 = new Passenger(1L, "Іван Іванов", "СН123456", "Паспорт", "0991234567", "ivan@example.com", BenefitType.NONE);
        passenger2 = new Passenger(2L, "Марія Петренко", "МП654321", "Паспорт", "0671112233", "maria@example.com", BenefitType.STUDENT);

        stopA = new Stop(1L, "Центральний Автовокзал", "Київ");
        stopB = new Stop(2L, "Стрийський Автовокзал", "Львів");
        stopC = new Stop(3L, "Привоз", "Одеса");
        stopD = new Stop(4L, "АС Умань", "Умань");

        route1 = new Route(1L, stopA, stopB, Collections.emptyList());
        route2 = new Route(2L, stopA, stopC, List.of(stopD));

        LocalDateTime dep1 = LocalDateTime.of(2024, 9, 1, 10, 0);
        LocalDateTime arr1 = LocalDateTime.of(2024, 9, 1, 18, 0);
        flight1 = new Flight(101L, route1, dep1, arr1, 50, FlightStatus.PLANNED, "Mercedes", new BigDecimal("500.00"));

        LocalDateTime dep2 = LocalDateTime.of(2024, 9, 5, 12, 0);
        LocalDateTime arr2 = LocalDateTime.of(2024, 9, 5, 20, 0);
        flight2 = new Flight(102L, route2, dep2, arr2, 45, FlightStatus.PLANNED, "Neoplan", new BigDecimal("650.00"));

        ticket1 = new Ticket(201L, flight1, passenger1, "1A", LocalDateTime.of(2024, 8, 15, 10, 0), new BigDecimal("500.00"), TicketStatus.BOOKED);
        ticket1.setPurchaseDateTime(LocalDateTime.of(2024, 8, 15, 10, 5));

        ticket2 = new Ticket(202L, flight2, passenger2, "2B", LocalDateTime.of(2024, 8, 16, 12, 0), new BigDecimal("650.00"), TicketStatus.SOLD);

        ticket3_older = new Ticket(203L, flight1, passenger2, "3C", LocalDateTime.of(2024, 8, 10, 9, 0), new BigDecimal("480.00"), TicketStatus.CANCELLED);



        sampleTickets = new ArrayList<>(List.of(ticket1, ticket2, ticket3_older));

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
        loggerConfig.removeAppender("TestListAppenderBookings");
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
    void constructor_withNullTickets_initializesEmptyListSortsAndLogs() {
        model = new BookingsTableModel(null);
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація BookingsTableModel з null списком квитків. Створюється порожній список."));
        assertFalse(findLogMessage(Level.TRACE, "Квитки відсортовано за датою бронювання (новіші вгорі)."));
    }

    @Test
    void constructor_withEmptyTickets_initializesEmptyListSortsAndLogs() {
        model = new BookingsTableModel(new ArrayList<>());
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація BookingsTableModel з 0 квитками."));
        assertFalse(findLogMessage(Level.TRACE, "Квитки відсортовано за датою бронювання (новіші вгорі)."));
    }

    @Test
    void constructor_withPopulatedTickets_initializesSortsAndLogs() {
        List<Ticket> initialTickets = new ArrayList<>(sampleTickets);
        model = new BookingsTableModel(initialTickets);
        assertNotNull(model);
        assertEquals(3, model.getRowCount());
        assertEquals(ticket2.getId(), model.getTicketAt(0).getId());
        assertEquals(ticket1.getId(), model.getTicketAt(1).getId());
        assertEquals(ticket3_older.getId(), model.getTicketAt(2).getId());

        initialTickets.clear();
        assertEquals(3, model.getRowCount(), "Model should have its own copy of the list.");
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація BookingsTableModel з 3 квитками."));
        assertFalse(findLogMessage(Level.TRACE, "Квитки відсортовано за датою бронювання (новіші вгорі)."));
    }



    @Test
    void setTickets_withNullList_clearsListSortsLogsAndFiresEvent() {
        model = new BookingsTableModel(sampleTickets);
        model.addTableModelListener(testListener);
        model.setTickets(null);
        assertEquals(0, model.getRowCount());
        assertTrue(tableDataChangedFired, "TableModelEvent.UPDATE not fired");
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null список квитків в BookingsTableModel. Список буде очищено."));
        assertFalse(findLogMessage(Level.TRACE, "Квитки відсортовано за датою бронювання (новіші вгорі)."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці оновлено та відсортовано."));
    }

    @Test
    void setTickets_withNewList_updatesSortsLogsAndFiresEvent() {
        model = new BookingsTableModel(Collections.emptyList());
        model.addTableModelListener(testListener);
        List<Ticket> newUnsortedTickets = new ArrayList<>(List.of(ticket3_older, ticket1, ticket2));
        model.setTickets(newUnsortedTickets);

        assertEquals(3, model.getRowCount());
        assertEquals(ticket2.getId(), model.getTicketAt(0).getId());
        assertEquals(ticket1.getId(), model.getTicketAt(1).getId());
        assertEquals(ticket3_older.getId(), model.getTicketAt(2).getId());

        assertTrue(tableDataChangedFired, "TableModelEvent.UPDATE not fired");
        assertTrue(findLogMessage(Level.INFO, "Встановлено новий список з 3 квитків в BookingsTableModel."));
        assertFalse(findLogMessage(Level.TRACE, "Квитки відсортовано за датою бронювання (новіші вгорі)."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці оновлено та відсортовано."));
    }

    @Test
    void getTicketAt_validIndex_returnsTicketAndLogsTrace() {
        model = new BookingsTableModel(sampleTickets);
        Ticket result = model.getTicketAt(0);
        assertEquals(ticket2, result);
        assertFalse(findLogMessage(Level.TRACE, "Отримання квитка за індексом 0: ID " + ticket2.getId()));
    }

    @Test
    void getTicketAt_negativeIndex_returnsNullAndLogsWarn() {
        model = new BookingsTableModel(sampleTickets);
        Ticket result = model.getTicketAt(-1);
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати квиток за недійсним індексом рядка: -1. Розмір списку: 3"));
    }

    @Test
    void getTicketAt_indexOutOfBounds_returnsNullAndLogsWarn() {
        model = new BookingsTableModel(sampleTickets);
        Ticket result = model.getTicketAt(sampleTickets.size());
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати квиток за недійсним індексом рядка: 3. Розмір списку: 3"));
    }

    @Test
    void getRowCount_returnsCorrectCount() {
        model = new BookingsTableModel(sampleTickets);
        assertEquals(sampleTickets.size(), model.getRowCount());
        model = new BookingsTableModel(Collections.emptyList());
        assertEquals(0, model.getRowCount());
    }

    @Test
    void getColumnCount_returnsCorrectCount() {
        model = new BookingsTableModel(Collections.emptyList());
        assertEquals(9, model.getColumnCount());
    }

    @ParameterizedTest
    @CsvSource({
            "0, ID Квитка", "1, Рейс (ID)", "2, Маршрут", "3, Пасажир", "4, Місце",
            "5, Дата бронюв.", "6, Дата продажу", "7, Ціна", "8, Статус"
    })
    void getColumnName_validIndex_returnsCorrectName(int index, String expectedName) {
        model = new BookingsTableModel(Collections.emptyList());
        assertEquals(expectedName, model.getColumnName(index));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 9})
    void getColumnName_invalidIndex_returnsEmptyStringAndLogsWarn(int invalidIndex) {
        model = new BookingsTableModel(Collections.emptyList());
        assertEquals("", model.getColumnName(invalidIndex));
        assertTrue(findLogMessage(Level.WARN, "Запит назви стовпця за недійсним індексом: " + invalidIndex));
    }

    @Test
    void getValueAt_invalidRowIndex_returnsErrorStringAndLogsError() {
        model = new BookingsTableModel(sampleTickets);
        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(-1, 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка -1 при запиті значення. Кількість рядків: 3"));
        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(sampleTickets.size(), 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка 3 при запиті значення. Кількість рядків: 3"));
    }

    @Test
    void getValueAt_ticketIsNullInList_returnsErrorStringAndLogsError() {
        List<Ticket> ticketsWithNull = new ArrayList<>();
        ticketsWithNull.add(null);
        model = new BookingsTableModel(ticketsWithNull);
        assertEquals("ПОМИЛКА: NULL КВИТОК", model.getValueAt(0, 0));
        assertTrue(findLogMessage(Level.ERROR, "Об'єкт Ticket є null для рядка 0 при запиті значення."));
    }




    @Test
    void getValueAt_invalidColumnIndex_returnsErrorStringAndLogsWarn() {
        model = new BookingsTableModel(sampleTickets);
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, -1));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця: -1 (рядок 0)"));
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, 9));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця: 9 (рядок 0)"));
    }

    @Test
    void getValueAt_validCells_returnsCorrectValues() {
        model = new BookingsTableModel(sampleTickets);

        assertEquals(ticket2.getId(), model.getValueAt(0, 0));
        assertEquals(flight2.getId(), model.getValueAt(0, 1));
        assertEquals(route2.getFullRouteDescription(), model.getValueAt(0, 2));
        assertEquals(passenger2.getFullName(), model.getValueAt(0, 3));
        assertEquals(ticket2.getSeatNumber(), model.getValueAt(0, 4));
        assertEquals(ticket2.getBookingDateTime().format(TABLE_DATE_FORMATTER), model.getValueAt(0, 5));
        assertEquals(ticket2.getPurchaseDateTime() != null ? ticket2.getPurchaseDateTime().format(TABLE_DATE_FORMATTER) : "-", model.getValueAt(0, 6));
        assertEquals(ticket2.getPricePaid(), model.getValueAt(0, 7));
        assertEquals(ticket2.getStatus().getDisplayName(), model.getValueAt(0, 8));

        assertEquals(ticket1.getId(), model.getValueAt(1, 0));
        assertEquals(flight1.getId(), model.getValueAt(1, 1));
        assertEquals(route1.getFullRouteDescription(), model.getValueAt(1, 2));
    }



    @Test
    void getValueAt_handlesNullPurchaseDateTime_returnsDefaultString() {

        ticket1.setPurchaseDateTime(null);
        model = new BookingsTableModel(Collections.singletonList(ticket1));
        assertEquals("-", model.getValueAt(0, 6));
    }



    @Test
    void getValueAt_exceptionDuringGetter_returnsErrorStringAndLogsError() {
        Passenger faultyPassenger = mock(Passenger.class);
        when(faultyPassenger.getFullName()).thenThrow(new RuntimeException("Test exception in getFullName"));


        Ticket ticketWithFaultyPassenger = new Ticket(601L, flight1, faultyPassenger, "X1",
                LocalDateTime.now(), BigDecimal.TEN, TicketStatus.BOOKED);

        model = new BookingsTableModel(Collections.singletonList(ticketWithFaultyPassenger));

        assertEquals("ПОМИЛКА ДАНИХ", model.getValueAt(0, 3));
        assertTrue(findLogMessage(Level.ERROR, "Помилка при отриманні значення для комірки [0, 3], квиток ID 601"));
        assertTrue(getLogEvents().stream().anyMatch(e -> e.getThrown() != null && e.getThrown().getMessage().contains("Test exception in getFullName")));
    }
}
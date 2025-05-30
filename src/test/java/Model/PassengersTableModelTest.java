package Model;

import Models.Enums.BenefitType;
import Models.Passenger;
import UI.Model.PassengersTableModel;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengersTableModelTest {


    @Plugin(name = "TestListAppenderPassengers", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderPassengers");
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

    private PassengersTableModel model;
    private List<Passenger> samplePassengers;
    private Passenger passenger1, passenger2, passengerWithNulls;

    private TestListAppender listAppender;

    @Mock
    private Passenger mockedPassenger;
    @Mock
    private BenefitType mockedBenefitType;


    private boolean tableDataChangedFired;
    private TableModelListener testListener;


    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderPassengers", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();


        passenger1 = new Passenger(1L, "Петренко Петро Петрович", "АА123456", "Паспорт громадянина України",
                "0501112233", "petro@example.com", BenefitType.NONE);
        passenger2 = new Passenger(2L, "Сидоренко Марія Іванівна", "КК987654", "Студентський квиток",
                "0679998877", "maria.s@example.com", BenefitType.STUDENT);
        passengerWithNulls = new Passenger(3L, "Невідомий Невідомець", "XX000000", "Посвідка",
                null, null, null);

        samplePassengers = new ArrayList<>(List.of(passenger1, passenger2));

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
        loggerConfig.removeAppender("TestListAppenderPassengers");
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
    void constructor_withNullPassengers_initializesEmptyListAndLogs() {
        model = new PassengersTableModel(null);
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація PassengersTableModel з null списком пасажирів. Створюється порожній список."));
    }

    @Test
    void constructor_withEmptyPassengers_initializesEmptyListAndLogs() {
        model = new PassengersTableModel(new ArrayList<>());
        assertNotNull(model);
        assertEquals(0, model.getRowCount());
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація PassengersTableModel з 0 пасажирами."));
    }

    @Test
    void constructor_withPopulatedPassengers_initializesCorrectlyAndLogs() {
        List<Passenger> initialPassengers = new ArrayList<>(samplePassengers);
        model = new PassengersTableModel(initialPassengers);
        assertNotNull(model);
        assertEquals(2, model.getRowCount());
        assertEquals(passenger1, model.getPassengerAt(0));
        assertEquals(passenger2, model.getPassengerAt(1));
        initialPassengers.clear();
        assertEquals(2, model.getRowCount(), "Model should have its own copy of the list.");
        assertFalse(findLogMessage(Level.DEBUG, "Ініціалізація PassengersTableModel з 2 пасажирами."));
    }

    @Test
    void setPassengers_withNullList_clearsListAndLogsAndFiresEvent() {
        model = new PassengersTableModel(samplePassengers);
        model.addTableModelListener(testListener);
        model.setPassengers(null);
        assertEquals(0, model.getRowCount());
        assertTrue(tableDataChangedFired, "TableModelEvent.UPDATE not fired");
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null список пасажирів в PassengersTableModel. Список буде очищено."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці пасажирів оновлено."));
    }

    @Test
    void setPassengers_withNewList_updatesListAndLogsAndFiresEvent() {
        model = new PassengersTableModel(Collections.emptyList());
        model.addTableModelListener(testListener);
        model.setPassengers(samplePassengers);
        assertEquals(2, model.getRowCount());
        assertEquals(passenger1, model.getPassengerAt(0));
        assertTrue(tableDataChangedFired, "TableModelEvent.UPDATE not fired");
        assertTrue(findLogMessage(Level.INFO, "Встановлено новий список з 2 пасажирами."));
        assertFalse(findLogMessage(Level.DEBUG, "Дані таблиці пасажирів оновлено."));
    }

    @Test
    void getPassengerAt_validIndex_returnsPassengerAndLogsTrace() {
        model = new PassengersTableModel(samplePassengers);
        Passenger result = model.getPassengerAt(0);
        assertEquals(passenger1, result);
        assertFalse(findLogMessage(Level.TRACE, "Отримання пасажира за індексом 0: ID 1"));
    }

    @Test
    void getPassengerAt_validIndexWithNullPassengerInList_returnsNullAndLogsTrace() {
        List<Passenger> passengersWithNull = new ArrayList<>();
        passengersWithNull.add(null);
        model = new PassengersTableModel(passengersWithNull);
        Passenger result = model.getPassengerAt(0);
        assertNull(result);

        assertFalse(findLogMessage(Level.TRACE, "Отримання пасажира за індексом 0: ID null"));
    }


    @Test
    void getPassengerAt_negativeIndex_returnsNullAndLogsWarn() {
        model = new PassengersTableModel(samplePassengers);
        Passenger result = model.getPassengerAt(-1);
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати пасажира за недійсним індексом рядка: -1. Розмір списку: 2"));
    }

    @Test
    void getPassengerAt_indexOutOfBounds_returnsNullAndLogsWarn() {
        model = new PassengersTableModel(samplePassengers);
        Passenger result = model.getPassengerAt(samplePassengers.size());
        assertNull(result);
        assertTrue(findLogMessage(Level.WARN, "Спроба отримати пасажира за недійсним індексом рядка: 2. Розмір списку: 2"));
    }

    @Test
    void getRowCount_returnsCorrectCount() {
        model = new PassengersTableModel(samplePassengers);
        assertEquals(samplePassengers.size(), model.getRowCount());
        model = new PassengersTableModel(Collections.emptyList());
        assertEquals(0, model.getRowCount());
    }

    @Test
    void getColumnCount_returnsCorrectCount() {
        model = new PassengersTableModel(Collections.emptyList());
        assertEquals(7, model.getColumnCount());
    }

    @ParameterizedTest
    @CsvSource({
            "0, ID",
            "1, ПІБ",
            "2, Документ",
            "3, Номер документа",
            "4, Телефон",
            "5, Email",
            "6, Пільга"
    })
    void getColumnName_validIndex_returnsCorrectName(int index, String expectedName) {
        model = new PassengersTableModel(Collections.emptyList());
        assertEquals(expectedName, model.getColumnName(index));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 7})
    void getColumnName_invalidIndex_returnsEmptyStringAndLogsWarn(int invalidIndex) {
        model = new PassengersTableModel(Collections.emptyList());
        assertEquals("", model.getColumnName(invalidIndex));
        assertTrue(findLogMessage(Level.WARN, "Запит назви стовпця для таблиці пасажирів за недійсним індексом: " + invalidIndex));
    }

    @Test
    void getValueAt_invalidRowIndex_returnsErrorStringAndLogsError() {
        model = new PassengersTableModel(samplePassengers);
        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(-1, 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка -1 при запиті значення для таблиці пасажирів. Кількість рядків: 2"));

        assertEquals("ПОМИЛКА ІНДЕКСУ РЯДКА", model.getValueAt(samplePassengers.size(), 0));
        assertTrue(findLogMessage(Level.ERROR, "Недійсний індекс рядка 2 при запиті значення для таблиці пасажирів. Кількість рядків: 2"));
    }

    @Test
    void getValueAt_passengerIsNullInList_returnsErrorStringAndLogsError() {
        List<Passenger> passengersWithNull = new ArrayList<>();
        passengersWithNull.add(null);
        model = new PassengersTableModel(passengersWithNull);
        assertEquals("ПОМИЛКА: NULL ПАСАЖИР", model.getValueAt(0, 0));
        assertTrue(findLogMessage(Level.ERROR, "Об'єкт Passenger є null для рядка 0 при запиті значення для таблиці пасажирів."));
    }

    @Test
    void getValueAt_invalidColumnIndex_returnsErrorStringAndLogsWarn() {
        model = new PassengersTableModel(samplePassengers);
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, -1));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для пасажирів: -1 (рядок 0)"));
        assertEquals("НЕВІДОМИЙ СТОВПЕЦЬ", model.getValueAt(0, 7));
        assertTrue(findLogMessage(Level.WARN, "Запит значення для невідомого індексу стовпця для пасажирів: 7 (рядок 0)"));
    }

    @Test
    void getValueAt_validCells_returnsCorrectValues() {
        model = new PassengersTableModel(samplePassengers);

        assertEquals(1L, model.getValueAt(0, 0));
        assertEquals("Петренко Петро Петрович", model.getValueAt(0, 1));
        assertEquals("Паспорт громадянина України", model.getValueAt(0, 2));
        assertEquals("АА123456", model.getValueAt(0, 3));
        assertEquals("0501112233", model.getValueAt(0, 4));
        assertEquals("petro@example.com", model.getValueAt(0, 5));
        assertEquals(BenefitType.NONE.getDisplayName(), model.getValueAt(0, 6));


        assertEquals(2L, model.getValueAt(1, 0));
        assertEquals("Сидоренко Марія Іванівна", model.getValueAt(1, 1));
        assertEquals("Студентський квиток", model.getValueAt(1, 2));
        assertEquals("КК987654", model.getValueAt(1, 3));
        assertEquals("0679998877", model.getValueAt(1, 4));
        assertEquals("maria.s@example.com", model.getValueAt(1, 5));
        assertEquals(BenefitType.STUDENT.getDisplayName(), model.getValueAt(1, 6));
    }

    @Test
    void getValueAt_passengerWithNullFields_returnsDefaultStrings() {
        model = new PassengersTableModel(Collections.singletonList(passengerWithNulls));

        assertEquals(3L, model.getValueAt(0, 0));
        assertEquals("Невідомий Невідомець", model.getValueAt(0, 1));
        assertEquals("Посвідка", model.getValueAt(0, 2));
        assertEquals("XX000000", model.getValueAt(0, 3));
        assertEquals("Телефон не вказано", model.getValueAt(0, 4));
        assertEquals("-", model.getValueAt(0, 5));
        assertEquals(BenefitType.NONE.getDisplayName(), model.getValueAt(0, 6));
    }

    @Test
    void getValueAt_mockedPassengerWithNulls_returnsDefaultStrings() {
        when(mockedPassenger.getId()).thenReturn(101L);
        when(mockedPassenger.getFullName()).thenReturn(null);
        when(mockedPassenger.getDocumentType()).thenReturn(null);
        when(mockedPassenger.getDocumentNumber()).thenReturn(null);
        when(mockedPassenger.getPhoneNumber()).thenReturn(null);
        when(mockedPassenger.getEmail()).thenReturn(null);
        when(mockedPassenger.getBenefitType()).thenReturn(null);

        model = new PassengersTableModel(Collections.singletonList(mockedPassenger));
        assertEquals(101L, model.getValueAt(0, 0));
        assertEquals("ПІБ не вказано", model.getValueAt(0, 1));
        assertEquals("Тип не вказано", model.getValueAt(0, 2));
        assertEquals("Номер не вказано", model.getValueAt(0, 3));
        assertEquals("Телефон не вказано", model.getValueAt(0, 4));
        assertEquals("-", model.getValueAt(0, 5));
        assertEquals("Без пільг", model.getValueAt(0, 6));
    }



    @Test
    void getValueAt_exceptionDuringGetter_returnsErrorStringAndLogsError() {

        Passenger faultyPassenger = mock(Passenger.class);
        when(faultyPassenger.getId()).thenReturn(999L);
        when(faultyPassenger.getFullName()).thenThrow(new RuntimeException("Test exception in getFullName"));

        model = new PassengersTableModel(Collections.singletonList(faultyPassenger));

        assertEquals("ПОМИЛКА ДАНИХ", model.getValueAt(0, 1));
        assertTrue(findLogMessage(Level.ERROR, "Помилка при отриманні значення для комірки пасажирів [0, 1], пасажир ID 999"));
        assertTrue(getLogEvents().stream().anyMatch(e -> e.getThrown() != null && e.getThrown().getMessage().contains("Test exception in getFullName")));
    }
}
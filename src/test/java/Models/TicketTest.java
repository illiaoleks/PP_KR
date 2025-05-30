package Models;

import Models.Enums.TicketStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTest {

    @Plugin(name = "TestListAppenderTicket", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderTicket");
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
    private Ticket validTicket;

    @Mock
    private Flight mockFlight;
    @Mock
    private Passenger mockPassenger;
    @Mock
    private TicketStatus mockTicketStatus;


    private final long DEFAULT_ID = 1L;
    private final String DEFAULT_SEAT_NUMBER = "A1";
    private final LocalDateTime DEFAULT_BOOKING_DATE_TIME = LocalDateTime.of(2024, 1, 1, 10, 0);
    private final BigDecimal DEFAULT_PRICE_PAID = new BigDecimal("100.00");
    private final TicketStatus DEFAULT_STATUS_ENUM_INSTANCE = TicketStatus.BOOKED;


    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderTicket", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();


        Mockito.lenient().when(mockFlight.getId()).thenReturn(101L);
        Mockito.lenient().when(mockPassenger.getFullName()).thenReturn("Тест Пасажир");

        validTicket = new Ticket(DEFAULT_ID, mockFlight, mockPassenger, DEFAULT_SEAT_NUMBER,
                DEFAULT_BOOKING_DATE_TIME, DEFAULT_PRICE_PAID, DEFAULT_STATUS_ENUM_INSTANCE);
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppenderTicket");
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

    @Test
    void constructor_validArguments_createsInstanceAndLogsInfo() {
        assertNotNull(validTicket);
        assertEquals(DEFAULT_ID, validTicket.getId());
        assertEquals(mockFlight, validTicket.getFlight());
        assertEquals(mockPassenger, validTicket.getPassenger());
        assertEquals(DEFAULT_SEAT_NUMBER, validTicket.getSeatNumber());
        assertEquals(DEFAULT_BOOKING_DATE_TIME, validTicket.getBookingDateTime());
        assertEquals(DEFAULT_PRICE_PAID, validTicket.getPricePaid());
        assertEquals(DEFAULT_STATUS_ENUM_INSTANCE, validTicket.getStatus());

        assertFalse(findLogMessage(Level.INFO, "Об'єкт Ticket успішно створено: ID=" + DEFAULT_ID));
        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Ticket з ID: " + DEFAULT_ID));
    }

    private static Stream<Arguments> invalidConstructorArguments() {
        Flight validFlight = mock(Flight.class);
        Passenger validPassenger = mock(Passenger.class);
        LocalDateTime validBookingTime = LocalDateTime.now();
        BigDecimal validPrice = BigDecimal.TEN;
        TicketStatus validStatus = TicketStatus.BOOKED;

        return Stream.of(
                Arguments.of(null, validPassenger, "A1", validBookingTime, validPrice, validStatus, "Рейс не може бути null."),
                Arguments.of(validFlight, null, "A1", validBookingTime, validPrice, validStatus, "Пасажир не може бути null."),
                Arguments.of(validFlight, validPassenger, null, validBookingTime, validPrice, validStatus, "Номер місця не може бути порожнім."),
                Arguments.of(validFlight, validPassenger, "  ", validBookingTime, validPrice, validStatus, "Номер місця не може бути порожнім."),
                Arguments.of(validFlight, validPassenger, "A1", null, validPrice, validStatus, "Дата та час бронювання не можуть бути null."),
                Arguments.of(validFlight, validPassenger, "A1", validBookingTime, null, validStatus, "Ціна не може бути null або від'ємною."),
                Arguments.of(validFlight, validPassenger, "A1", validBookingTime, new BigDecimal("-1.00"), validStatus, "Ціна не може бути null або від'ємною."),
                Arguments.of(validFlight, validPassenger, "A1", validBookingTime, validPrice, null, "Статус квитка не може бути null.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void constructor_invalidArguments_throwsIllegalArgumentExceptionAndLogsError(
            Flight flight, Passenger passenger, String seatNumber, LocalDateTime bookingDateTime,
            BigDecimal pricePaid, TicketStatus status, String expectedMessage) {

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new Ticket(2L, flight, passenger, seatNumber, bookingDateTime, pricePaid, status)
        );
        assertEquals(expectedMessage, exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Помилка створення Ticket:"));
    }

    @Test
    void setId_changesIdAndLogsTrace() {
        validTicket.setId(99L);
        assertEquals(99L, validTicket.getId());
        assertFalse(findLogMessage(Level.TRACE, "Встановлення ID квитка " + DEFAULT_ID + " на: 99"));
    }

    @Test
    void getFlight_returnsFlight() {
        assertEquals(mockFlight, validTicket.getFlight());
    }

    @Test
    void setFlight_validFlight_changesFlightAndLogsTrace() {
        Flight newFlight = mock(Flight.class);
        validTicket.setFlight(newFlight);
        assertEquals(newFlight, validTicket.getFlight());
        assertFalse(findLogMessage(Level.TRACE, "Зміна рейсу для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setFlight_nullFlight_setsNullAndLogsWarnAndTrace() {
        validTicket.setFlight(null);
        assertNull(validTicket.getFlight());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null рейс для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна рейсу для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getPassenger_returnsPassenger() {
        assertEquals(mockPassenger, validTicket.getPassenger());
    }

    @Test
    void setPassenger_validPassenger_changesPassengerAndLogsTrace() {
        Passenger newPassenger = mock(Passenger.class);
        validTicket.setPassenger(newPassenger);
        assertEquals(newPassenger, validTicket.getPassenger());
        assertFalse(findLogMessage(Level.TRACE, "Зміна пасажира для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setPassenger_nullPassenger_setsNullAndLogsWarnAndTrace() {
        validTicket.setPassenger(null);
        assertNull(validTicket.getPassenger());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null пасажира для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна пасажира для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getSeatNumber_returnsSeatNumber() {
        assertEquals(DEFAULT_SEAT_NUMBER, validTicket.getSeatNumber());
    }

    @Test
    void setSeatNumber_validSeatNumber_changesSeatNumberAndLogsTrace() {
        validTicket.setSeatNumber("B2");
        assertEquals("B2", validTicket.getSeatNumber());
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера місця для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setSeatNumber_nullSeatNumber_setsNullAndLogsWarnAndTrace() {
        validTicket.setSeatNumber(null);
        assertNull(validTicket.getSeatNumber());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожній номер місця для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера місця для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setSeatNumber_emptySeatNumber_setsEmptyAndLogsWarnAndTrace() {
        validTicket.setSeatNumber("  ");
        assertEquals("  ", validTicket.getSeatNumber());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожній номер місця для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера місця для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getBookingDateTime_returnsBookingDateTime() {
        assertEquals(DEFAULT_BOOKING_DATE_TIME, validTicket.getBookingDateTime());
    }

    @Test
    void setBookingDateTime_validDateTime_changesDateTimeAndLogsTrace() {
        LocalDateTime newBookingTime = LocalDateTime.now().plusDays(1);
        validTicket.setBookingDateTime(newBookingTime);
        assertEquals(newBookingTime, validTicket.getBookingDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати бронювання для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setBookingDateTime_nullDateTime_setsNullAndLogsWarnAndTrace() {
        validTicket.setBookingDateTime(null);
        assertNull(validTicket.getBookingDateTime());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null дату бронювання для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати бронювання для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getPurchaseDateTime_returnsPurchaseDateTime() {
        assertNull(validTicket.getPurchaseDateTime());
        LocalDateTime purchaseTime = LocalDateTime.now();
        validTicket.setPurchaseDateTime(purchaseTime);
        assertEquals(purchaseTime, validTicket.getPurchaseDateTime());
    }

    @Test
    void setPurchaseDateTime_validDateTime_changesDateTimeAndLogsTrace() {
        LocalDateTime purchaseTime = LocalDateTime.now();
        validTicket.setPurchaseDateTime(purchaseTime);
        assertEquals(purchaseTime, validTicket.getPurchaseDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати покупки для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setPurchaseDateTime_nullDateTime_setsNullAndLogsTrace() {
        validTicket.setPurchaseDateTime(null);
        assertNull(validTicket.getPurchaseDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати покупки для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getBookingExpiryDateTime_returnsBookingExpiryDateTime() {
        assertNull(validTicket.getBookingExpiryDateTime());
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(2);
        validTicket.setBookingExpiryDateTime(expiryTime);
        assertEquals(expiryTime, validTicket.getBookingExpiryDateTime());
    }

    @Test
    void setBookingExpiryDateTime_validDateTime_changesDateTimeAndLogsTrace() {
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(2);
        validTicket.setBookingExpiryDateTime(expiryTime);
        assertEquals(expiryTime, validTicket.getBookingExpiryDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати закінчення броні для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setBookingExpiryDateTime_nullDateTime_setsNullAndLogsTrace() {
        validTicket.setBookingExpiryDateTime(null);
        assertNull(validTicket.getBookingExpiryDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати закінчення броні для квитка ID " + DEFAULT_ID));
    }


    @Test
    void getPricePaid_returnsPricePaid() {
        assertEquals(DEFAULT_PRICE_PAID, validTicket.getPricePaid());
    }

    @Test
    void setPricePaid_validPrice_changesPriceAndLogsTrace() {
        BigDecimal newPrice = new BigDecimal("150.00");
        validTicket.setPricePaid(newPrice);
        assertEquals(newPrice, validTicket.getPricePaid());
        assertFalse(findLogMessage(Level.TRACE, "Зміна ціни для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setPricePaid_nullPrice_setsNullAndLogsWarnAndTrace() {
        validTicket.setPricePaid(null);
        assertNull(validTicket.getPricePaid());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити некоректну ціну (null) для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна ціни для квитка ID " + DEFAULT_ID));
    }

    @Test
    void setPricePaid_negativePrice_setsNegativeAndLogsWarnAndTrace() {
        BigDecimal negativePrice = new BigDecimal("-50.00");
        validTicket.setPricePaid(negativePrice);
        assertEquals(negativePrice, validTicket.getPricePaid());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити некоректну ціну (" + negativePrice + ") для квитка ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна ціни для квитка ID " + DEFAULT_ID));
    }

    @Test
    void getStatus_returnsStatus() {
        assertEquals(DEFAULT_STATUS_ENUM_INSTANCE, validTicket.getStatus());
    }

    @Test
    void setStatus_validStatus_changesStatusAndLogsInfo() {
        TicketStatus oldStatus = validTicket.getStatus();
        TicketStatus newStatus = TicketStatus.SOLD;

        validTicket.setStatus(newStatus);
        assertEquals(newStatus, validTicket.getStatus());
        assertTrue(findLogMessage(Level.INFO, "Зміна статусу для квитка ID " + DEFAULT_ID + ": з " + oldStatus.getDisplayName() + " на " + newStatus.getDisplayName()));
    }

    @Test
    void setStatus_nullStatus_doesNotChangeStatusAndLogsError() {
        TicketStatus originalStatus = validTicket.getStatus();
        validTicket.setStatus(null);
        assertEquals(originalStatus, validTicket.getStatus(), "Status should not change if new status is null.");
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null статус для квитка ID: " + DEFAULT_ID + ". Поточний статус: " + originalStatus.getDisplayName()));
    }

    @Test
    void toString_withValidData_returnsCorrectFormat() {

        String expected = String.format("Квиток ID %d: Рейс [ID %d], Пасажир [%s], Місце [%s], Бронювання [%s], Ціна [%s], Статус [%s]",
                DEFAULT_ID, mockFlight.getId(), mockPassenger.getFullName(), DEFAULT_SEAT_NUMBER,
                DEFAULT_BOOKING_DATE_TIME.toString(), DEFAULT_PRICE_PAID.toString(), DEFAULT_STATUS_ENUM_INSTANCE.getDisplayName());
        assertEquals(expected, validTicket.toString());
    }

    @Test
    void toString_withNullFlight_handlesGracefully() {
        validTicket.setFlight(null);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Рейс не вказано"));
    }

    @Test
    void toString_withNullPassenger_handlesGracefully() {
        validTicket.setPassenger(null);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Пасажир не вказаний"));
    }

    @Test
    void toString_withNullPassengerFullName_handlesGracefully() {

        Passenger passengerWithNullName = mock(Passenger.class);
        when(passengerWithNullName.getFullName()).thenReturn(null);

        Ticket ticketWithNullPassengerName = new Ticket(3L, mockFlight, passengerWithNullName, "C3", DEFAULT_BOOKING_DATE_TIME, DEFAULT_PRICE_PAID, DEFAULT_STATUS_ENUM_INSTANCE);
        listAppender.clear();
        String str = ticketWithNullPassengerName.toString();
        assertTrue(str.contains("Пасажир не вказаний"));
    }

    @Test
    void toString_withNullSeatNumber_handlesGracefully() {
        validTicket.setSeatNumber(null);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Місце не вказано"));
    }
    @Test
    void toString_withEmptySeatNumber_handlesGracefully() {
        validTicket.setSeatNumber(" ");
        listAppender.clear();
        String str = validTicket.toString();
        assertFalse(str.contains("Місце не вказано"));
    }

    @Test
    void toString_withNullBookingDateTime_handlesGracefully() {
        validTicket.setBookingDateTime(null);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Час бронювання не вказано"));
    }

    @Test
    void toString_withNullPricePaid_handlesGracefully() {
        validTicket.setPricePaid(null);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Ціна не вказана"));
    }

    @Test
    void toString_withStatusHavingNullDisplayName_handlesGracefully() {

        when(mockTicketStatus.getDisplayName()).thenReturn(null);
        validTicket.setStatus(mockTicketStatus);
        listAppender.clear();
        String str = validTicket.toString();
        assertTrue(str.contains("Статус невідомий"));
    }


    @Test
    void equals_sameObject_returnsTrue() {
        assertTrue(validTicket.equals(validTicket));
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertFalse(validTicket.equals(null));
    }

    @Test
    void equals_differentClass_returnsFalse() {
        assertFalse(validTicket.equals(new Object()));
    }

    @Test
    void equals_sameId_returnsTrue() {
        Ticket anotherTicket = new Ticket(DEFAULT_ID, mock(Flight.class), mock(Passenger.class), "B2",
                LocalDateTime.now(), BigDecimal.ONE, TicketStatus.CANCELLED);
        assertTrue(validTicket.equals(anotherTicket));
    }

    @Test
    void equals_differentId_returnsFalse() {
        Ticket anotherTicket = new Ticket(DEFAULT_ID + 1, mockFlight, mockPassenger, DEFAULT_SEAT_NUMBER,
                DEFAULT_BOOKING_DATE_TIME, DEFAULT_PRICE_PAID, DEFAULT_STATUS_ENUM_INSTANCE);
        assertFalse(validTicket.equals(anotherTicket));
    }

    @Test
    void hashCode_basedOnId() {
        Ticket anotherTicketWithSameId = new Ticket(DEFAULT_ID, mock(Flight.class), mock(Passenger.class), "C3",
                LocalDateTime.now(), BigDecimal.TEN, TicketStatus.SOLD);
        assertEquals(validTicket.hashCode(), anotherTicketWithSameId.hashCode());

        Ticket anotherTicketWithDifferentId = new Ticket(DEFAULT_ID + 1, mockFlight, mockPassenger, DEFAULT_SEAT_NUMBER,
                DEFAULT_BOOKING_DATE_TIME, DEFAULT_PRICE_PAID, DEFAULT_STATUS_ENUM_INSTANCE);
        assertNotEquals(validTicket.hashCode(), anotherTicketWithDifferentId.hashCode());
    }
}
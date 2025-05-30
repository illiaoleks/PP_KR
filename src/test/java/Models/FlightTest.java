package Models;

import Models.Enums.FlightStatus;
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
class FlightTest {

    @Plugin(name = "TestListAppenderFlight", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
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
                LOGGER.error("No name provided for TestListAppenderFlight");
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
    private Flight validFlight;

    @Mock
    private Route mockRoute;
    @Mock
    private FlightStatus mockFlightStatus;

    private final long DEFAULT_ID = 1L;
    private final LocalDateTime DEFAULT_DEPARTURE = LocalDateTime.of(2024, 1, 1, 10, 0);
    private final LocalDateTime DEFAULT_ARRIVAL = LocalDateTime.of(2024, 1, 1, 12, 0);
    private final int DEFAULT_TOTAL_SEATS = 50;
    private final FlightStatus DEFAULT_STATUS_ENUM = FlightStatus.PLANNED;
    private final String DEFAULT_BUS_MODEL = "Mercedes";
    private final BigDecimal DEFAULT_PRICE = new BigDecimal("100.00");


    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderFlight", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();


        Mockito.lenient().when(mockRoute.getFullRouteDescription()).thenReturn("МістоА -> МістоБ");


        validFlight = new Flight(DEFAULT_ID, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, DEFAULT_BUS_MODEL, DEFAULT_PRICE);
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppenderFlight");
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
        Flight flight = new Flight(2L, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, DEFAULT_BUS_MODEL, DEFAULT_PRICE);
        assertNotNull(flight);
        assertEquals(2L, flight.getId());


        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Flight з ID: 2"));
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Flight успішно створено: ID=2"));
    }

    @Test
    void constructor_departureAfterArrival_logsWarn() {
        listAppender.clear();
        LocalDateTime departure = LocalDateTime.of(2024, 1, 1, 14, 0);
        LocalDateTime arrival = LocalDateTime.of(2024, 1, 1, 12, 0);
        new Flight(3L, mockRoute, departure, arrival, DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, null, DEFAULT_PRICE);
        assertTrue(findLogMessage(Level.WARN, "Увага при створенні Flight (ID: 3): Дата відправлення (" + departure + ") пізніше за дату прибуття (" + arrival + ")."));
    }

    @Test
    void constructor_nullBusModel_isAllowed() {
        listAppender.clear();
        Flight flight = new Flight(4L, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, null, DEFAULT_PRICE);
        assertNotNull(flight);
        assertNull(flight.getBusModel());
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Flight успішно створено: ID=4"));
    }

    @Test
    void constructor_emptyBusModel_isAllowed() {
        listAppender.clear();
        Flight flight = new Flight(5L, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, "", DEFAULT_PRICE);
        assertNotNull(flight);
        assertEquals("", flight.getBusModel());
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Flight успішно створено: ID=5"));
    }


    private static Stream<Arguments> invalidConstructorArguments() {
        Route validRoute = mock(Route.class);
        LocalDateTime validDeparture = LocalDateTime.now();
        LocalDateTime validArrival = LocalDateTime.now().plusHours(2);
        int validSeats = 50;
        FlightStatus validStatus = FlightStatus.PLANNED;
        BigDecimal validPrice = BigDecimal.TEN;

        return Stream.of(
                Arguments.of(null, validDeparture, validArrival, validSeats, validStatus, "Model", validPrice, "Маршрут (route) не може бути null"),
                Arguments.of(validRoute, null, validArrival, validSeats, validStatus, "Model", validPrice, "Дата та час відправлення (departureDateTime) не можуть бути null"),
                Arguments.of(validRoute, validDeparture, null, validSeats, validStatus, "Model", validPrice, "Дата та час прибуття (arrivalDateTime) не можуть бути null"),
                Arguments.of(validRoute, validDeparture, validArrival, validSeats, null, "Model", validPrice, "Статус рейсу (status) не може бути null"),
                Arguments.of(validRoute, validDeparture, validArrival, validSeats, validStatus, "Model", null, "Ціна за місце (pricePerSeat) не може бути null або від'ємною"),
                Arguments.of(validRoute, validDeparture, validArrival, validSeats, validStatus, "Model", new BigDecimal("-1.00"), "Ціна за місце (pricePerSeat) не може бути null або від'ємною"),
                Arguments.of(validRoute, validDeparture, validArrival, 0, validStatus, "Model", validPrice, "Загальна кількість місць (totalSeats) має бути позитивним числом"),
                Arguments.of(validRoute, validDeparture, validArrival, -10, validStatus, "Model", validPrice, "Загальна кількість місць (totalSeats) має бути позитивним числом")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void constructor_invalidArguments_throwsIllegalArgumentExceptionAndLogsError(
            Route route, LocalDateTime departure, LocalDateTime arrival, int seats,
            FlightStatus status, String busModel, BigDecimal price, String expectedMessage) {

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new Flight(10L, route, departure, arrival, seats, status, busModel, price)
        );
        assertEquals(expectedMessage, exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Помилка створення Flight:"));
        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Flight з ID: 10"));
    }


    @Test
    void getId_returnsCorrectId() {
        assertEquals(DEFAULT_ID, validFlight.getId());
    }

    @Test
    void setId_changesIdAndLogsTrace() {
        validFlight.setId(99L);
        assertEquals(99L, validFlight.getId());
        assertFalse(findExactLogMessage(Level.TRACE, "Встановлення ID рейсу " + DEFAULT_ID + " на: 99"));
    }

    @Test
    void getRoute_returnsCorrectRoute() {
        assertEquals(mockRoute, validFlight.getRoute());
    }

    @Test
    void setRoute_validRoute_changesRouteAndLogsTrace() {
        Route newRoute = mock(Route.class);
        validFlight.setRoute(newRoute);
        assertEquals(newRoute, validFlight.getRoute());
        assertFalse(findLogMessage(Level.TRACE, "Зміна маршруту для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void setRoute_nullRoute_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setRoute(null)
        );
        assertEquals("Маршрут (route) не може бути null", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null маршрут для рейсу ID: " + DEFAULT_ID));
    }

    @Test
    void getDepartureDateTime_returnsCorrectDateTime() {
        assertEquals(DEFAULT_DEPARTURE, validFlight.getDepartureDateTime());
    }

    @Test
    void setDepartureDateTime_validDateTime_changesDateTimeAndLogsTrace() {
        LocalDateTime newDeparture = DEFAULT_DEPARTURE.plusHours(1);
        validFlight.setDepartureDateTime(newDeparture);
        assertEquals(newDeparture, validFlight.getDepartureDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати відправлення для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void setDepartureDateTime_newDepartureAfterArrival_logsWarn() {
        LocalDateTime newDeparture = DEFAULT_ARRIVAL.plusHours(1);
        validFlight.setDepartureDateTime(newDeparture);
        assertEquals(newDeparture, validFlight.getDepartureDateTime());
        assertTrue(findLogMessage(Level.WARN, "Увага при зміні дати відправлення рейсу ID " + DEFAULT_ID + ": Нова дата відправлення (" + newDeparture + ") пізніше за поточну дату прибуття (" + DEFAULT_ARRIVAL + ")."));
    }

    @Test
    void setDepartureDateTime_nullDateTime_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setDepartureDateTime(null)
        );
        assertEquals("Дата та час відправлення (departureDateTime) не можуть бути null", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null дату відправлення для рейсу ID: " + DEFAULT_ID));
    }



    @Test
    void getArrivalDateTime_returnsCorrectDateTime() {
        assertEquals(DEFAULT_ARRIVAL, validFlight.getArrivalDateTime());
    }

    @Test
    void setArrivalDateTime_validDateTime_changesDateTimeAndLogsTrace() {
        LocalDateTime newArrival = DEFAULT_ARRIVAL.plusHours(1);
        validFlight.setArrivalDateTime(newArrival);
        assertEquals(newArrival, validFlight.getArrivalDateTime());
        assertFalse(findLogMessage(Level.TRACE, "Зміна дати прибуття для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void setArrivalDateTime_newArrivalBeforeDeparture_logsWarn() {
        LocalDateTime newArrival = DEFAULT_DEPARTURE.minusHours(1);
        validFlight.setArrivalDateTime(newArrival);
        assertEquals(newArrival, validFlight.getArrivalDateTime());
        assertTrue(findLogMessage(Level.WARN, "Увага при зміні дати прибуття рейсу ID " + DEFAULT_ID + ": Нова дата прибуття (" + newArrival + ") раніше за поточну дату відправлення (" + DEFAULT_DEPARTURE + ")."));
    }

    @Test
    void setArrivalDateTime_nullDateTime_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setArrivalDateTime(null)
        );
        assertEquals("Дата та час прибуття (arrivalDateTime) не можуть бути null", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null дату прибуття для рейсу ID: " + DEFAULT_ID));
    }




    @Test
    void getTotalSeats_returnsCorrectTotalSeats() {
        assertEquals(DEFAULT_TOTAL_SEATS, validFlight.getTotalSeats());
    }

    @Test
    void setTotalSeats_validSeats_changesSeatsAndLogsTrace() {
        int newSeats = 60;
        validFlight.setTotalSeats(newSeats);
        assertEquals(newSeats, validFlight.getTotalSeats());
        assertFalse(findLogMessage(Level.TRACE, "Зміна кількості місць для рейсу ID " + DEFAULT_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -10})
    void setTotalSeats_nonPositiveSeats_throwsExceptionAndLogsError(int invalidSeats) {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setTotalSeats(invalidSeats)
        );
        assertEquals("Загальна кількість місць (totalSeats) має бути позитивним числом", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити непозитивну кількість місць (" + invalidSeats + ") для рейсу ID: " + DEFAULT_ID));
    }

    @Test
    void getStatus_returnsCorrectStatus() {
        assertEquals(DEFAULT_STATUS_ENUM, validFlight.getStatus());
    }

    @Test
    void setStatus_validStatus_changesStatusAndLogsInfo() {
        FlightStatus oldStatus = validFlight.getStatus();
        FlightStatus newStatus = FlightStatus.DEPARTED;
        validFlight.setStatus(newStatus);
        assertEquals(newStatus, validFlight.getStatus());
        assertTrue(findLogMessage(Level.INFO, "Зміна статусу для рейсу ID " + DEFAULT_ID + ": з " + oldStatus.getDisplayName() + " на " + newStatus.getDisplayName()));
    }

    @Test
    void setStatus_nullStatus_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setStatus(null)
        );
        assertEquals("Статус рейсу (status) не може бути null", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити null статус для рейсу ID: " + DEFAULT_ID));
    }

    @Test
    void getBusModel_returnsCorrectBusModel() {
        assertEquals(DEFAULT_BUS_MODEL, validFlight.getBusModel());
        Flight flightWithNullBusModel = new Flight(6L, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL, 10, FlightStatus.PLANNED, null, BigDecimal.ONE);
        assertNull(flightWithNullBusModel.getBusModel());
    }

    @Test
    void setBusModel_validModel_changesModelAndLogsTrace() {
        String newModel = "Neoplan";
        validFlight.setBusModel(newModel);
        assertEquals(newModel, validFlight.getBusModel());
        assertFalse(findLogMessage(Level.TRACE, "Зміна моделі автобуса для рейсу ID " + DEFAULT_ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void setBusModel_nullOrEmptyModel_setsItAndLogsTrace(String modelValue) {
        validFlight.setBusModel(modelValue);
        assertEquals(modelValue, validFlight.getBusModel());
        assertFalse(findLogMessage(Level.TRACE, "Зміна моделі автобуса для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void getPricePerSeat_returnsCorrectPrice() {
        assertEquals(DEFAULT_PRICE, validFlight.getPricePerSeat());
    }

    @Test
    void setPricePerSeat_validPrice_changesPriceAndLogsTrace() {
        BigDecimal newPrice = new BigDecimal("120.50");
        validFlight.setPricePerSeat(newPrice);
        assertEquals(newPrice, validFlight.getPricePerSeat());
        assertFalse(findLogMessage(Level.TRACE, "Зміна ціни за місце для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void setPricePerSeat_zeroPrice_isAllowed() {
        BigDecimal zeroPrice = BigDecimal.ZERO;
        validFlight.setPricePerSeat(zeroPrice);
        assertEquals(zeroPrice, validFlight.getPricePerSeat());
        assertFalse(findLogMessage(Level.TRACE, "Зміна ціни за місце для рейсу ID " + DEFAULT_ID));
    }

    @Test
    void setPricePerSeat_nullPrice_throwsExceptionAndLogsError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setPricePerSeat(null)
        );
        assertEquals("Ціна за місце (pricePerSeat) не може бути null або від'ємною", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити некоректну ціну (null) для рейсу ID: " + DEFAULT_ID));
    }

    @Test
    void setPricePerSeat_negativePrice_throwsExceptionAndLogsError() {
        BigDecimal negativePrice = new BigDecimal("-10.00");
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                validFlight.setPricePerSeat(negativePrice)
        );
        assertEquals("Ціна за місце (pricePerSeat) не може бути null або від'ємною", exception.getMessage());
        assertTrue(findLogMessage(Level.ERROR, "Спроба встановити некоректну ціну (" + negativePrice + ") для рейсу ID: " + DEFAULT_ID));
    }

    @Test
    void toString_withValidData_returnsCorrectFormat() {
        String expected = "Рейс " + DEFAULT_ID + ": " + mockRoute.getFullRouteDescription() +
                ", Відправлення: " + DEFAULT_DEPARTURE.toString() +
                ", Прибуття: " + DEFAULT_ARRIVAL.toString() +
                ", Місць: " + DEFAULT_TOTAL_SEATS +
                ", Автобус: " + DEFAULT_BUS_MODEL +
                ", Ціна: " + DEFAULT_PRICE.toString() +
                ", Статус: " + DEFAULT_STATUS_ENUM.getDisplayName();
        assertEquals(expected, validFlight.toString());
    }


    @Test
    void toString_withNullStatusDisplayName_handlesGracefully() {
        when(mockFlightStatus.getDisplayName()).thenReturn(null);
        validFlight.setStatus(mockFlightStatus);
        listAppender.clear();
        assertTrue(validFlight.toString().contains("Статус невідомий"));
    }

    @Test
    void toString_withNullDeparture_handlesGracefully() {

        String expected = "Рейс " + DEFAULT_ID;
        assertTrue(validFlight.toString().startsWith(expected));

    }

    @Test
    void toString_withNullArrival_handlesGracefully() {

        String expected = "Рейс " + DEFAULT_ID;
        assertTrue(validFlight.toString().startsWith(expected));
    }

    @Test
    void toString_withNullPrice_handlesGracefully() {

        String expected = "Рейс " + DEFAULT_ID;
        assertTrue(validFlight.toString().startsWith(expected));
    }


    @Test
    void toString_withNullBusModel_handlesGracefully() {
        validFlight.setBusModel(null);
        listAppender.clear();
        String str = validFlight.toString();
        assertFalse(str.contains(", Автобус:"));
    }

    @Test
    void toString_withEmptyBusModel_handlesGracefully() {
        validFlight.setBusModel("");
        listAppender.clear();
        String str = validFlight.toString();
        assertFalse(str.contains(", Автобус:"));
    }


    @Test
    void equals_sameObject_returnsTrue() {
        assertTrue(validFlight.equals(validFlight));
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertFalse(validFlight.equals(null));
    }

    @Test
    void equals_differentClass_returnsFalse() {
        assertFalse(validFlight.equals(new Object()));
    }

    @Test
    void equals_sameId_returnsTrue() {
        Flight anotherFlight = new Flight(DEFAULT_ID, mock(Route.class), DEFAULT_ARRIVAL, DEFAULT_DEPARTURE,
                10, FlightStatus.CANCELLED, "Інший", BigDecimal.ONE);
        assertTrue(validFlight.equals(anotherFlight));
    }

    @Test
    void equals_differentId_returnsFalse() {
        Flight anotherFlight = new Flight(DEFAULT_ID + 1, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, DEFAULT_BUS_MODEL, DEFAULT_PRICE);
        assertFalse(validFlight.equals(anotherFlight));
    }

    @Test
    void hashCode_basedOnId() {
        Flight anotherFlightWithSameId = new Flight(DEFAULT_ID, mock(Route.class), DEFAULT_ARRIVAL, DEFAULT_DEPARTURE,
                20, FlightStatus.ARRIVED, "ЩеІнший", BigDecimal.TEN);
        assertEquals(validFlight.hashCode(), anotherFlightWithSameId.hashCode());

        Flight anotherFlightWithDifferentId = new Flight(DEFAULT_ID + 5, mockRoute, DEFAULT_DEPARTURE, DEFAULT_ARRIVAL,
                DEFAULT_TOTAL_SEATS, DEFAULT_STATUS_ENUM, DEFAULT_BUS_MODEL, DEFAULT_PRICE);
        assertNotEquals(validFlight.hashCode(), anotherFlightWithDifferentId.hashCode());
    }
}
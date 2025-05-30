package DAO;

import DB.DatabaseConnectionManager;
import Models.Enums.FlightStatus;
import Models.Flight;
import Models.Route;
import Models.Stop;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlightDAOTest {

    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private Statement mockStatement;
    @Mock
    private ResultSet mockResultSet;

    @Mock
    private RouteDAO mockRouteDAO;


    private FlightDAO flightDAO;

    @Captor
    private ArgumentCaptor<Long> longCaptor;
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<Timestamp> timestampCaptor;
    @Captor
    private ArgumentCaptor<Integer> intCaptor;
    @Captor
    private ArgumentCaptor<BigDecimal> bigDecimalCaptor;
    @Captor
    private ArgumentCaptor<java.sql.Date> dateCaptor;

    private static ListAppender listAppender;
    private static org.apache.logging.log4j.core.Logger rootLogger;
    private static MockedStatic<DatabaseConnectionManager> mockedDbManager;

    private Stop departureStop1, destinationStop1, departureStop2, destinationStop2;
    private Route testRoute1, testRoute2;
    private Flight testFlight1, testFlight2;

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
        public boolean containsMessageWithException(Level level, String partialMessage, Class<? extends Throwable> exceptionClass) {
            return events.stream().anyMatch(event ->
                    event.getLevel().equals(level) &&
                            event.getMessage().getFormattedMessage().contains(partialMessage) &&
                            event.getThrown() != null &&
                            exceptionClass.isAssignableFrom(event.getThrown().getClass())
            );
        }
    }

    @BeforeAll
    static void setupLogAppenderAndStaticMock() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);

        rootLogger = context.getLogger("insurance.log");
        listAppender = new ListAppender("TestFlightDAOAppender");
        listAppender.start();
        rootLogger.addAppender(listAppender);
        rootLogger.setLevel(Level.ALL);
        mockedDbManager = Mockito.mockStatic(DatabaseConnectionManager.class);
    }

    @AfterAll
    static void tearDownLogAppenderAndStaticMock() {
        if (listAppender != null) {
            rootLogger.removeAppender(listAppender);
            listAppender.stop();
        }
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        listAppender.clearEvents();
        mockedDbManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);


        flightDAO = new FlightDAO(mockRouteDAO);

        lenient().doNothing().when(mockResultSet).close();
        lenient().doNothing().when(mockPreparedStatement).close();
        lenient().doNothing().when(mockStatement).close();
        lenient().doNothing().when(mockConnection).close();


        departureStop1 = new Stop(10L, "Київ", "Центральний автовокзал");
        destinationStop1 = new Stop(20L, "Львів", "Автовокзал Стрийський");
        testRoute1 = new Route(1L, departureStop1, destinationStop1, Collections.emptyList());

        departureStop2 = new Stop(30L, "Одеса", "АС Привоз");
        destinationStop2 = new Stop(40L, "Харків", "АС-1");
        testRoute2 = new Route(2L, departureStop2, destinationStop2, Collections.emptyList());

        testFlight1 = new Flight(1L, testRoute1,
                LocalDateTime.of(2024, 1, 10, 10, 0),
                LocalDateTime.of(2024, 1, 10, 12, 0),
                50, FlightStatus.PLANNED, "BusModelX", new BigDecimal("25.00"));
        testFlight2 = new Flight(2L, testRoute2,
                LocalDateTime.of(2024, 1, 11, 14, 0),
                LocalDateTime.of(2024, 1, 11, 16, 0),
                50, FlightStatus.DEPARTED, "BusModelY", new BigDecimal("30.00"));
    }

    @AfterEach
    void tearDown() {

    }


    @Test
    void getAllFlights_success_oneFlight_returnsListWithOneFlight() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));

        List<Flight> flights = flightDAO.getAllFlights();

        assertNotNull(flights);
        assertEquals(1, flights.size());
        Flight retrievedFlight = flights.get(0);
        assertEquals(testFlight1.getId(), retrievedFlight.getId());
        assertEquals(testFlight1.getRoute().getId(), retrievedFlight.getRoute().getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 1 рейсів."), "Log message for successful retrieval of 1 flight not found.");
        verify(mockRouteDAO).getRouteById(testFlight1.getRoute().getId());
    }

    @Test
    void getAllFlights_success_multipleFlights_returnsList() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);

        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId()).thenReturn(testFlight2.getId());
        when(mockResultSet.getLong("route_id"))
                .thenReturn(testFlight1.getRoute().getId())
                .thenReturn(testFlight2.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time"))
                .thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()))
                .thenReturn(Timestamp.valueOf(testFlight2.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time"))
                .thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()))
                .thenReturn(Timestamp.valueOf(testFlight2.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats"))
                .thenReturn(testFlight1.getTotalSeats())
                .thenReturn(testFlight2.getTotalSeats());
        when(mockResultSet.getString("bus_model"))
                .thenReturn(testFlight1.getBusModel())
                .thenReturn(testFlight2.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat"))
                .thenReturn(testFlight1.getPricePerSeat())
                .thenReturn(testFlight2.getPricePerSeat());
        when(mockResultSet.getString("status"))
                .thenReturn(testFlight1.getStatus().name())
                .thenReturn(testFlight2.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));
        when(mockRouteDAO.getRouteById(testFlight2.getRoute().getId())).thenReturn(Optional.of(testRoute2));

        List<Flight> flights = flightDAO.getAllFlights();

        assertNotNull(flights);
        assertEquals(2, flights.size());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 2 рейсів."), "Log message for successful retrieval of 2 flights not found.");
        verify(mockRouteDAO).getRouteById(testFlight1.getRoute().getId());
        verify(mockRouteDAO).getRouteById(testFlight2.getRoute().getId());
    }

    @Test
    void getAllFlights_success_noFlights_returnsEmptyList() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<Flight> flights = flightDAO.getAllFlights();

        assertNotNull(flights);
        assertTrue(flights.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 0 рейсів."), "Log message for 0 flights not found.");
    }

    @Test
    void getAllFlights_failure_routeNotFoundForFlight_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.empty());

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getAllFlights());
        assertTrue(exception.getMessage().contains("Маршрут ID " + testFlight1.getRoute().getId() + " не знайдено для рейсу ID: " + testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.WARN, "Маршрут ID " + testFlight1.getRoute().getId() + " не знайдено"));
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні всіх рейсів", SQLException.class));
    }

    @Test
    void getAllFlights_failure_invalidFlightStatusInDB_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getString("status")).thenReturn("INVALID_STATUS");
        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getAllFlights());
        assertTrue(exception.getMessage().contains("Недійсний статус 'INVALID_STATUS' для рейсу ID " + testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Недійсний статус 'INVALID_STATUS'"));
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні всіх рейсів", SQLException.class));
    }

    @Test
    void getAllFlights_failure_nullFlightStatusInDB_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getString("status")).thenReturn(null);
        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());


        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getAllFlights());
        assertTrue(exception.getMessage().contains("Статус рейсу є null для рейсу ID " + testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Статус рейсу є null для рейсу ID " + testFlight1.getId()));
    }

    @Test
    void getAllFlights_failure_sqlExceptionOnQuery_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("DB Query Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getAllFlights());
        assertEquals("DB Query Error", exception.getMessage());
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні всіх рейсів", SQLException.class));
    }


    @Test
    void addFlight_success_returnsTrueAndSetsId() throws SQLException {
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(123L);

        Flight newFlight = new Flight(0L, testRoute1, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2),
                30, FlightStatus.PLANNED, "NewBus", BigDecimal.TEN);

        assertTrue(flightDAO.addFlight(newFlight));
        assertEquals(123L, newFlight.getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Рейс успішно додано. ID нового рейсу: 123"));

        verify(mockPreparedStatement).setLong(eq(1), longCaptor.capture());
        assertEquals(testRoute1.getId(), longCaptor.getValue());
        verify(mockPreparedStatement).setTimestamp(eq(2), timestampCaptor.capture());
        assertEquals(Timestamp.valueOf(newFlight.getDepartureDateTime()), timestampCaptor.getValue());
        verify(mockPreparedStatement).setTimestamp(eq(3), timestampCaptor.capture());
        assertEquals(Timestamp.valueOf(newFlight.getArrivalDateTime()), timestampCaptor.getValue());
        verify(mockPreparedStatement).setInt(eq(4), intCaptor.capture());
        assertEquals(newFlight.getTotalSeats(), intCaptor.getValue());
        verify(mockPreparedStatement).setString(eq(5), stringCaptor.capture());
        assertEquals(newFlight.getBusModel(), stringCaptor.getValue());
        verify(mockPreparedStatement).setBigDecimal(eq(6), bigDecimalCaptor.capture());
        assertEquals(newFlight.getPricePerSeat(), bigDecimalCaptor.getValue());
        verify(mockPreparedStatement).setString(eq(7), stringCaptor.capture());
        assertEquals(FlightStatus.PLANNED.name(), stringCaptor.getValue());
    }

    @Test
    void addFlight_failure_executeUpdateReturnsZero_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        Flight newFlight = new Flight(0L, testRoute1, LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                30, FlightStatus.PLANNED, "NewBus", BigDecimal.TEN);

        assertFalse(flightDAO.addFlight(newFlight));
        assertEquals(0L, newFlight.getId());
        assertTrue(listAppender.containsMessage(Level.WARN, "Рейс не було додано (affectedRows = 0)."));
    }

    @Test
    void addFlight_failure_noGeneratedKey_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        Flight newFlight = new Flight(0L, testRoute1, LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                30, FlightStatus.PLANNED, "NewBus", BigDecimal.TEN);

        assertFalse(flightDAO.addFlight(newFlight));
        assertEquals(0L, newFlight.getId());
        assertTrue(listAppender.containsMessage(Level.WARN, "Рейс додано (1 рядків), але не вдалося отримати згенерований ID."));
    }

    @Test
    void addFlight_failure_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenThrow(new SQLException("DB Insert Error"));

        Flight newFlight = new Flight(0L, testRoute1, LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                30, FlightStatus.PLANNED, "NewBus", BigDecimal.TEN);

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.addFlight(newFlight));
        assertEquals("DB Insert Error", exception.getMessage());
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при додаванні рейсу", SQLException.class));
    }


    @Test
    void updateFlight_success_returnsTrue() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        assertTrue(flightDAO.updateFlight(testFlight1));
        assertTrue(listAppender.containsMessage(Level.INFO, "Рейс з ID " + testFlight1.getId() + " успішно оновлено."));

        verify(mockPreparedStatement).setLong(eq(1), eq(testFlight1.getRoute().getId()));
        verify(mockPreparedStatement).setTimestamp(eq(2), eq(Timestamp.valueOf(testFlight1.getDepartureDateTime())));
        verify(mockPreparedStatement).setTimestamp(eq(3), eq(Timestamp.valueOf(testFlight1.getArrivalDateTime())));
        verify(mockPreparedStatement).setInt(eq(4), eq(testFlight1.getTotalSeats()));
        verify(mockPreparedStatement).setString(eq(5), eq(testFlight1.getBusModel()));
        verify(mockPreparedStatement).setBigDecimal(eq(6), eq(testFlight1.getPricePerSeat()));
        verify(mockPreparedStatement).setString(eq(7), eq(testFlight1.getStatus().name()));
        verify(mockPreparedStatement).setLong(eq(8), eq(testFlight1.getId()));
    }

    @Test
    void updateFlight_failure_executeUpdateReturnsZero_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        assertFalse(flightDAO.updateFlight(testFlight1));
        assertFalse(listAppender.containsMessage(Level.WARN, "Рейс з ID " + testFlight1.getId() + " не знайдено або не було оновлено."));
    }

    @Test
    void updateFlight_failure_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Update Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.updateFlight(testFlight1));
        assertEquals("DB Update Error", exception.getMessage());
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при оновленні рейсу з ID " + testFlight1.getId(), SQLException.class));
    }


    @Test
    void updateFlightStatus_success_returnsTrue() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        FlightStatus newStatus = FlightStatus.DELAYED;
        assertTrue(flightDAO.updateFlightStatus(testFlight1.getId(), newStatus));
        assertTrue(listAppender.containsMessage(Level.INFO, "Статус рейсу ID " + testFlight1.getId() + " успішно оновлено на " + newStatus));
        verify(mockPreparedStatement).setString(1, newStatus.name());
        verify(mockPreparedStatement).setLong(2, testFlight1.getId());
    }

    @Test
    void updateFlightStatus_failure_executeUpdateReturnsZero_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        assertFalse(flightDAO.updateFlightStatus(testFlight1.getId(), FlightStatus.CANCELLED));
        assertFalse(listAppender.containsMessage(Level.WARN, "Рейс з ID " + testFlight1.getId() + " не знайдено або статус не було оновлено."));
    }

    @Test
    void updateFlightStatus_failure_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Status Update Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.updateFlightStatus(testFlight1.getId(), FlightStatus.CANCELLED));
        assertEquals("DB Status Update Error", exception.getMessage());
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при оновленні статусу рейсу ID " + testFlight1.getId() + ": " + FlightStatus.CANCELLED, SQLException.class));
    }


    @Test
    void getOccupiedSeatsCount_success_returnsCount() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(15);

        assertEquals(15, flightDAO.getOccupiedSeatsCount(testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.INFO, "Кількість зайнятих місць для рейсу ID " + testFlight1.getId() + ": 15"));
        verify(mockPreparedStatement).setLong(1, testFlight1.getId());
    }

    @Test
    void getOccupiedSeatsCount_success_noOccupiedSeats_returnsZero() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(0);

        assertEquals(0, flightDAO.getOccupiedSeatsCount(testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.INFO, "Кількість зайнятих місць для рейсу ID " + testFlight1.getId() + ": 0"));
    }

    @Test
    void getOccupiedSeatsCount_failure_resultSetNextFalse_logsAndReturnsZero() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        assertEquals(0, flightDAO.getOccupiedSeatsCount(testFlight1.getId()));
        assertTrue(listAppender.containsMessage(Level.INFO, "Не знайдено даних про зайняті місця для рейсу ID " + testFlight1.getId() + ". Повертається 0."));
    }

    @Test
    void getOccupiedSeatsCount_failure_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Count Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getOccupiedSeatsCount(testFlight1.getId()));
        assertEquals("DB Count Error", exception.getMessage());
        assertTrue(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні кількості зайнятих місць для рейсу ID " + testFlight1.getId(), SQLException.class));
    }


    @Test
    void getFlightById_success_flightFound_returnsOptionalOfFlight() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));

        Optional<Flight> result = flightDAO.getFlightById(testFlight1.getId());

        assertTrue(result.isPresent());
        assertEquals(testFlight1.getId(), result.get().getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Рейс з ID " + testFlight1.getId() + " знайдено."));
        verify(mockPreparedStatement).setLong(1, testFlight1.getId());
        verify(mockRouteDAO).getRouteById(testFlight1.getRoute().getId());
    }

    @Test
    void getFlightById_success_flightNotFound_returnsEmptyOptional() throws SQLException {
        long nonExistentFlightId = 999L;
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        Optional<Flight> result = flightDAO.getFlightById(nonExistentFlightId);

        assertFalse(result.isPresent());
        assertTrue(listAppender.containsMessage(Level.INFO, "Рейс з ID " + nonExistentFlightId + " не знайдено."));
        verify(mockRouteDAO, never()).getRouteById(anyLong());
    }

    @Test
    void getFlightById_failure_routeNotFoundForFlight_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.empty());

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightById(testFlight1.getId()));
        assertTrue(exception.getMessage().contains("Маршрут ID " + testFlight1.getRoute().getId() + " не знайдено для рейсу ID: " + testFlight1.getId()));
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсу за ID " + testFlight1.getId(), SQLException.class));
    }

    @Test
    void getFlightById_failure_invalidStatusInDB_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getString("status")).thenReturn("BOGUS_STATUS");
        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));


        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightById(testFlight1.getId()));
        assertTrue(exception.getMessage().contains("Недійсний статус 'BOGUS_STATUS' для рейсу ID " + testFlight1.getId()));
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсу за ID " + testFlight1.getId(), SQLException.class));
    }

    @Test
    void getFlightById_failure_nullStatusInDB_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getString("status")).thenReturn(null);
        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));


        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightById(testFlight1.getId()));
        assertTrue(exception.getMessage().contains("Статус рейсу є null для рейсу ID " + testFlight1.getId()));
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсу за ID " + testFlight1.getId(), SQLException.class));
    }

    @Test
    void getFlightById_failure_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB GetById Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightById(testFlight1.getId()));
        assertEquals("DB GetById Error", exception.getMessage());
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсу за ID " + testFlight1.getId(), SQLException.class));

    }


    @Test
    void getFlightsByDate_success_oneFlightOnDate_returnsListWithOneFlight() throws SQLException {
        LocalDate date = testFlight1.getDepartureDateTime().toLocalDate();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());

        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.of(testRoute1));

        List<Flight> flights = flightDAO.getFlightsByDate(date);

        assertNotNull(flights);
        assertEquals(1, flights.size());
        assertEquals(testFlight1.getId(), flights.get(0).getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 1 рейсів на дату " + date));
        verify(mockPreparedStatement).setDate(eq(1), dateCaptor.capture());
        assertEquals(java.sql.Date.valueOf(date), dateCaptor.getValue());
    }

    @Test
    void getFlightsByDate_success_noFlightsOnDate_returnsEmptyList() throws SQLException {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<Flight> flights = flightDAO.getFlightsByDate(date);

        assertNotNull(flights);
        assertTrue(flights.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 0 рейсів на дату " + date));
    }

    @Test
    void getFlightsByDate_failure_routeNotFound_throwsSQLException() throws SQLException {
        LocalDate date = testFlight1.getDepartureDateTime().toLocalDate();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testFlight1.getId());
        when(mockResultSet.getLong("route_id")).thenReturn(testFlight1.getRoute().getId());
        when(mockResultSet.getString("status")).thenReturn(testFlight1.getStatus().name());
        when(mockResultSet.getTimestamp("departure_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight1.getArrivalDateTime()));
        when(mockResultSet.getInt("total_seats")).thenReturn(testFlight1.getTotalSeats());
        when(mockResultSet.getString("bus_model")).thenReturn(testFlight1.getBusModel());
        when(mockResultSet.getBigDecimal("price_per_seat")).thenReturn(testFlight1.getPricePerSeat());


        when(mockRouteDAO.getRouteById(testFlight1.getRoute().getId())).thenReturn(Optional.empty());

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightsByDate(date));
        assertTrue(exception.getMessage().contains("Маршрут ID " + testFlight1.getRoute().getId() + " не знайдено для рейсу ID: " + testFlight1.getId()));
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсів на дату " + date, SQLException.class));
    }


    @Test
    void getFlightsByDate_failure_sqlExceptionOnQuery_throwsSQLException() throws SQLException {
        LocalDate date = LocalDate.of(2024, 1, 10);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB Date Query Error"));

        SQLException exception = assertThrows(SQLException.class, () -> flightDAO.getFlightsByDate(date));
        assertEquals("DB Date Query Error", exception.getMessage());
        assertFalse(listAppender.containsMessageWithException(Level.ERROR, "Помилка при отриманні рейсів на дату " + date, SQLException.class));
    }
}
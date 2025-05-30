package DAO;

import DB.DatabaseConnectionManager;
import Models.Enums.BenefitType;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketDAOTest {

    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private Statement mockStatement;
    @Mock
    private ResultSet mockResultSet;

    @Mock
    private FlightDAO mockFlightDAO;
    @Mock
    private PassengerDAO mockPassengerDAO;
    @Mock
    private RouteDAO mockRouteDAO;

    @InjectMocks
    private TicketDAO ticketDAO;

    private static ListAppender listAppender;
    private static org.apache.logging.log4j.core.Logger appLogger;
    private static MockedStatic<DatabaseConnectionManager> mockedDbManager;

    private Flight testFlight;
    private Passenger testPassenger;
    private Route testRoute;
    private Stop departureStop, destinationStop;
    private Ticket testTicket1;
    private Ticket testTicket2;


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
    }

    @BeforeAll
    static void setupLogAppenderAndStaticMock() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);

        appLogger = context.getLogger("insurance.log");
        listAppender = new ListAppender("TestTicketDAOAppender");
        listAppender.start();
        appLogger.addAppender(listAppender);
        appLogger.setLevel(Level.ALL);
        mockedDbManager = Mockito.mockStatic(DatabaseConnectionManager.class);
    }

    @AfterAll
    static void tearDownLogAppenderAndStaticMock() {
        if (listAppender != null) {
            appLogger.removeAppender(listAppender);
            listAppender.stop();
        }
        mockedDbManager.close();
    }

    @BeforeEach
    void setUp() throws SQLException {
        listAppender.clearEvents();
        mockedDbManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);


        lenient().doNothing().when(mockResultSet).close();
        lenient().doNothing().when(mockPreparedStatement).close();
        lenient().doNothing().when(mockStatement).close();
        lenient().doNothing().when(mockConnection).close();


        departureStop = new Stop(1L, "Київ", "АС Київ");
        destinationStop = new Stop(2L, "Львів", "АС Львів");
        testRoute = new Route(10L, departureStop, destinationStop, Collections.emptyList());
        testFlight = new Flight(100L, testRoute, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(5),
                50, FlightStatus.PLANNED, "Богдан А092", BigDecimal.valueOf(250));
        testPassenger = new Passenger(1000L, "Тест Пасажирович", "АА123456", "Паспорт", "0501112233", "test@p.com", BenefitType.NONE);

        testTicket1 = new Ticket(1L, testFlight, testPassenger, "A1", LocalDateTime.now().minusHours(1),
                BigDecimal.valueOf(250), TicketStatus.BOOKED);
        testTicket1.setBookingExpiryDateTime(LocalDateTime.now().plusHours(23));

        testTicket2 = new Ticket(2L, testFlight, testPassenger, "B2", LocalDateTime.now().minusDays(1),
                BigDecimal.valueOf(200), TicketStatus.SOLD);
        testTicket2.setPurchaseDateTime(LocalDateTime.now().minusDays(1));

    }

    @AfterEach
    void tearDown() {
        try {
            verify(mockConnection, atLeast(0)).close();
            verify(mockPreparedStatement, atLeast(0)).close();
            verify(mockStatement, atLeast(0)).close();
            verify(mockResultSet, atLeast(0)).close();
        } catch (SQLException e) {

        }
        reset(mockConnection, mockPreparedStatement, mockStatement, mockResultSet,
                mockFlightDAO, mockPassengerDAO, mockRouteDAO);
    }


    @Test
    void getOccupiedSeatsForFlight_success_returnsSeats() throws SQLException {
        long flightId = testFlight.getId();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("seat_number")).thenReturn("A1").thenReturn("B2");

        List<String> seats = ticketDAO.getOccupiedSeatsForFlight(flightId);

        assertNotNull(seats);
        assertEquals(2, seats.size());
        assertTrue(seats.contains("A1"));
        assertTrue(seats.contains("B2"));
        assertTrue(listAppender.containsMessage(Level.INFO, "Знайдено 2 зайнятих місць для рейсу ID: " + flightId));
        verify(mockPreparedStatement).setLong(1, flightId);
    }

    @Test
    void getOccupiedSeatsForFlight_noSeats_returnsEmptyList() throws SQLException {
        long flightId = testFlight.getId();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<String> seats = ticketDAO.getOccupiedSeatsForFlight(flightId);

        assertNotNull(seats);
        assertTrue(seats.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Знайдено 0 зайнятих місць для рейсу ID: " + flightId));
    }

    @Test
    void getOccupiedSeatsForFlight_sqlException_throwsSQLException() throws SQLException {
        long flightId = testFlight.getId();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB Error Seats"));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getOccupiedSeatsForFlight(flightId));
        assertEquals("DB Error Seats", ex.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні зайнятих місць для рейсу ID " + flightId));
    }



    @Test
    void addTicket_success_returnsTrueAndSetsId() throws SQLException {
        long generatedId = 123L;
        testTicket1.setId(0L);

        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(generatedId);

        assertTrue(ticketDAO.addTicket(testTicket1));
        assertEquals(generatedId, testTicket1.getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Квиток успішно додано. ID нового квитка: " + generatedId));
        verify(mockPreparedStatement).setLong(1, testTicket1.getFlight().getId());
        verify(mockPreparedStatement).setLong(2, testTicket1.getPassenger().getId());
        verify(mockPreparedStatement).setString(3, testTicket1.getSeatNumber());
        verify(mockPreparedStatement).setTimestamp(4, Timestamp.valueOf(testTicket1.getBookingDateTime()));
        verify(mockPreparedStatement).setTimestamp(5, Timestamp.valueOf(testTicket1.getBookingExpiryDateTime()));
        verify(mockPreparedStatement).setBigDecimal(6, testTicket1.getPricePaid());
        verify(mockPreparedStatement).setString(7, testTicket1.getStatus().name());
    }

    @Test
    void addTicket_success_nullExpiryDate_setsNullTimestamp() throws SQLException {
        long generatedId = 124L;
        testTicket1.setId(0L);
        testTicket1.setBookingExpiryDateTime(null);

        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(generatedId);

        assertTrue(ticketDAO.addTicket(testTicket1));
        verify(mockPreparedStatement).setNull(5, Types.TIMESTAMP);
    }


    @Test
    void addTicket_failure_noGeneratedKey_returnsFalse() throws SQLException {
        testTicket1.setId(0L);
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        assertFalse(ticketDAO.addTicket(testTicket1));
        assertEquals(0L, testTicket1.getId());
        assertTrue(listAppender.containsMessage(Level.WARN, "Квиток додано (1 рядків), але не вдалося отримати згенерований ID."));
    }

    @Test
    void addTicket_failure_affectedRowsZero_returnsFalse() throws SQLException {
        testTicket1.setId(0L);
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        assertFalse(ticketDAO.addTicket(testTicket1));
        assertTrue(listAppender.containsMessage(Level.WARN, "Квиток не було додано (affectedRows = 0)."));
    }

    @Test
    void addTicket_failure_uniqueConstraintViolation_returnsFalse() throws SQLException {
        testTicket1.setId(0L);
        SQLException uniqueEx = new SQLException("Порушення uq_ticket_flight_seat", "23000");
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(uniqueEx);

        assertFalse(ticketDAO.addTicket(testTicket1));
        assertTrue(listAppender.containsMessage(Level.WARN, "Помилка додавання квитка: Місце " + testTicket1.getSeatNumber() + " на рейсі " + testTicket1.getFlight().getId() + " вже зайняте."));
    }

    @Test
    void addTicket_failure_otherSqlException_throwsSQLException() throws SQLException {
        testTicket1.setId(0L);
        SQLException otherEx = new SQLException("Інша помилка SQL", "XXXXX");
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(otherEx);

        SQLException thrown = assertThrows(SQLException.class, () -> ticketDAO.addTicket(testTicket1));
        assertSame(otherEx, thrown);
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка SQL при додаванні квитка: Рейс ID=" + testTicket1.getFlight().getId()));
    }


    @Test
    void updateTicketStatus_toSold_success_returnsTrue() throws SQLException {
        long ticketId = testTicket1.getId();
        LocalDateTime purchaseTime = LocalDateTime.now();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        assertTrue(ticketDAO.updateTicketStatus(ticketId, TicketStatus.SOLD, purchaseTime));

        verify(mockPreparedStatement).setString(1, TicketStatus.SOLD.name());
        verify(mockPreparedStatement).setTimestamp(2, Timestamp.valueOf(purchaseTime));
        verify(mockPreparedStatement).setLong(3, ticketId);
        assertFalse(listAppender.containsMessage(Level.INFO, "Статус квитка ID " + ticketId + " успішно оновлено на SOLD."));
    }

    @Test
    void updateTicketStatus_toCancelled_success_returnsTrue() throws SQLException {
        long ticketId = testTicket1.getId();
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        assertTrue(ticketDAO.updateTicketStatus(ticketId, TicketStatus.CANCELLED, null));

        verify(mockPreparedStatement).setString(1, TicketStatus.CANCELLED.name());
        verify(mockPreparedStatement).setLong(2, ticketId);
        assertFalse(listAppender.containsMessage(Level.INFO, "Статус квитка ID " + ticketId + " успішно оновлено на CANCELLED."));
    }


    @Test
    void updateTicketStatus_notFound_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        assertFalse(ticketDAO.updateTicketStatus(testTicket1.getId(), TicketStatus.SOLD, LocalDateTime.now()));
        assertTrue(listAppender.containsMessage(Level.WARN, "Квиток з ID " + testTicket1.getId() + " не знайдено або статус не було оновлено."));
    }

    @Test
    void updateTicketStatus_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Update Status Error"));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.updateTicketStatus(testTicket1.getId(), TicketStatus.SOLD, LocalDateTime.now()));
        assertEquals("DB Update Status Error", ex.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при оновленні статусу квитка ID " + testTicket1.getId()));
    }


    @Test
    void getTicketsByPassengerId_success_returnsTickets() throws SQLException {
        long passengerId = testPassenger.getId();
        when(mockPassengerDAO.findById(passengerId)).thenReturn(Optional.of(testPassenger));
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        LocalDateTime bookingTime1 = testTicket1.getBookingDateTime();
        LocalDateTime bookingTime2 = testTicket2.getBookingDateTime();
        LocalDateTime purchaseTime2 = testTicket2.getPurchaseDateTime();
        LocalDateTime expiryTime1 = testTicket1.getBookingExpiryDateTime();

        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);

        when(mockResultSet.getLong("id"))
                .thenReturn(testTicket1.getId())
                .thenReturn(testTicket1.getId())
                .thenReturn(testTicket2.getId())
                .thenReturn(testTicket2.getId());

        when(mockResultSet.getLong("flight_id")).thenReturn(testFlight.getId());
        lenient().when(mockResultSet.getLong("passenger_id")).thenReturn(passengerId);

        when(mockResultSet.getString("seat_number")).thenReturn(testTicket1.getSeatNumber()).thenReturn(testTicket2.getSeatNumber());
        when(mockResultSet.getTimestamp("booking_date_time"))
                .thenReturn(Timestamp.valueOf(bookingTime1))
                .thenReturn(Timestamp.valueOf(bookingTime2));
        when(mockResultSet.getBigDecimal("price_paid"))
                .thenReturn(testTicket1.getPricePaid())
                .thenReturn(testTicket2.getPricePaid());
        when(mockResultSet.getString("status"))
                .thenReturn(testTicket1.getStatus().name())
                .thenReturn(testTicket2.getStatus().name());
        when(mockResultSet.getTimestamp("purchase_date_time"))
                .thenReturn(null)
                .thenReturn(Timestamp.valueOf(purchaseTime2));
        when(mockResultSet.getTimestamp("booking_expiry_date_time"))
                .thenReturn(Timestamp.valueOf(expiryTime1))
                .thenReturn(null);

        when(mockResultSet.getTimestamp("flight_departure_date_time")).thenReturn(Timestamp.valueOf(testFlight.getDepartureDateTime()));
        when(mockResultSet.getTimestamp("flight_arrival_date_time")).thenReturn(Timestamp.valueOf(testFlight.getArrivalDateTime()));
        when(mockResultSet.getInt("flight_total_seats")).thenReturn(testFlight.getTotalSeats());
        when(mockResultSet.getString("flight_bus_model")).thenReturn(testFlight.getBusModel());
        when(mockResultSet.getBigDecimal("flight_price_per_seat")).thenReturn(testFlight.getPricePerSeat());
        when(mockResultSet.getString("flight_status")).thenReturn(testFlight.getStatus().name());

        when(mockResultSet.getLong("route_id")).thenReturn(testRoute.getId());
        when(mockResultSet.getLong("departure_stop_id")).thenReturn(departureStop.getId());
        when(mockResultSet.getLong("destination_stop_id")).thenReturn(destinationStop.getId());
        when(mockResultSet.getString("dep_stop_name")).thenReturn(departureStop.getName());
        when(mockResultSet.getString("dep_stop_city")).thenReturn(departureStop.getCity());
        when(mockResultSet.getString("arr_stop_name")).thenReturn(destinationStop.getName());
        when(mockResultSet.getString("arr_stop_city")).thenReturn(destinationStop.getCity());

        List<Ticket> tickets = ticketDAO.getTicketsByPassengerId(passengerId);

        assertNotNull(tickets);
        assertEquals(2, tickets.size(), "Should find 2 tickets for the passenger.");
        assertTrue(listAppender.containsMessage(Level.INFO, "Знайдено 2 квитків для історії поїздок пасажира ID " + passengerId + "."));

        Ticket resultTicket1 = tickets.stream().filter(t -> t.getId() == testTicket1.getId()).findFirst().orElse(null);
        assertNotNull(resultTicket1, "Ticket with ID " + testTicket1.getId() + " should be found.");
        assertEquals(testTicket1.getSeatNumber(), resultTicket1.getSeatNumber());
        assertEquals(testTicket1.getStatus(), resultTicket1.getStatus());
        assertNull(resultTicket1.getPurchaseDateTime());
        assertNotNull(resultTicket1.getBookingExpiryDateTime());
        assertEquals(expiryTime1, resultTicket1.getBookingExpiryDateTime());

        Ticket resultTicket2 = tickets.stream().filter(t -> t.getId() == testTicket2.getId()).findFirst().orElse(null);
        assertNotNull(resultTicket2, "Ticket with ID " + testTicket2.getId() + " should be found.");
        assertEquals(testTicket2.getSeatNumber(), resultTicket2.getSeatNumber());
        assertEquals(testTicket2.getStatus(), resultTicket2.getStatus());
        assertNotNull(resultTicket2.getPurchaseDateTime());
        assertEquals(purchaseTime2, resultTicket2.getPurchaseDateTime());
        assertNull(resultTicket2.getBookingExpiryDateTime());

        verify(mockPreparedStatement).setLong(1, passengerId);
    }

    @Test
    void getTicketsByPassengerId_passengerNotFound_throwsSQLException() throws SQLException {
        long passengerId = 999L;
        when(mockPassengerDAO.findById(passengerId)).thenReturn(Optional.empty());

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getTicketsByPassengerId(passengerId));
        assertTrue(ex.getMessage().contains("Пасажира з ID " + passengerId + " не знайдено для історії поїздок."));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Пасажира з ID " + passengerId + " не знайдено для історії поїздок."));
        verify(mockConnection, never()).prepareStatement(anyString());
    }

    @Test
    void getTicketsByPassengerId_noTicketsFound_returnsEmptyList() throws SQLException {
        long passengerId = testPassenger.getId();
        when(mockPassengerDAO.findById(passengerId)).thenReturn(Optional.of(testPassenger));
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<Ticket> tickets = ticketDAO.getTicketsByPassengerId(passengerId);

        assertNotNull(tickets);
        assertTrue(tickets.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Знайдено 0 квитків для історії поїздок пасажира ID " + passengerId + "."));
        verify(mockPreparedStatement).setLong(1, passengerId);
    }


    @Test
    void getTicketsByPassengerId_sqlExceptionOnQuery_throwsSQLException() throws SQLException {
        long passengerId = testPassenger.getId();
        when(mockPassengerDAO.findById(passengerId)).thenReturn(Optional.of(testPassenger));
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB Query Error"));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getTicketsByPassengerId(passengerId));
        assertEquals("DB Query Error", ex.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні історії поїздок для пасажира ID " + passengerId));
    }




    private void setupMockResultSetForGetAllTickets(ResultSet rs, Ticket... tickets) throws SQLException {

        Boolean[] nextCalls = new Boolean[tickets.length + 1];
        for (int i = 0; i < tickets.length; i++) {
            nextCalls[i] = true;
        }
        nextCalls[tickets.length] = false;
        when(rs.next()).thenReturn(nextCalls[0], java.util.Arrays.copyOfRange(nextCalls, 1, nextCalls.length));


        if (tickets.length > 0) {
            Long[] ids = new Long[tickets.length * 2];
            Long[] fIds = new Long[tickets.length];
            Long[] pIds = new Long[tickets.length];
            String[] seats = new String[tickets.length];
            Timestamp[] bookings = new Timestamp[tickets.length];
            BigDecimal[] prices = new BigDecimal[tickets.length];
            String[] statuses = new String[tickets.length];
            Timestamp[] purchases = new Timestamp[tickets.length];
            Timestamp[] expiries = new Timestamp[tickets.length];

            for (int i = 0; i < tickets.length; i++) {
                Ticket t = tickets[i];
                ids[i * 2] = t.getId();
                ids[i * 2 + 1] = t.getId();
                fIds[i] = t.getFlight().getId();
                pIds[i] = t.getPassenger().getId();
                seats[i] = t.getSeatNumber();
                bookings[i] = Timestamp.valueOf(t.getBookingDateTime());
                prices[i] = t.getPricePaid();
                statuses[i] = t.getStatus().name();
                purchases[i] = t.getPurchaseDateTime() != null ? Timestamp.valueOf(t.getPurchaseDateTime()) : null;
                expiries[i] = t.getBookingExpiryDateTime() != null ? Timestamp.valueOf(t.getBookingExpiryDateTime()) : null;


                when(mockFlightDAO.getFlightById(fIds[i])).thenReturn(Optional.of(t.getFlight()));
                when(mockPassengerDAO.findById(pIds[i])).thenReturn(Optional.of(t.getPassenger()));
            }

            when(rs.getLong("id")).thenReturn(ids[0], java.util.Arrays.copyOfRange(ids, 1, ids.length));
            when(rs.getLong("flight_id")).thenReturn(fIds[0], java.util.Arrays.copyOfRange(fIds, 1, fIds.length));
            when(rs.getLong("passenger_id")).thenReturn(pIds[0], java.util.Arrays.copyOfRange(pIds, 1, pIds.length));
            when(rs.getString("seat_number")).thenReturn(seats[0], java.util.Arrays.copyOfRange(seats, 1, seats.length));
            when(rs.getTimestamp("booking_date_time")).thenReturn(bookings[0], java.util.Arrays.copyOfRange(bookings, 1, bookings.length));
            when(rs.getBigDecimal("price_paid")).thenReturn(prices[0], java.util.Arrays.copyOfRange(prices, 1, prices.length));
            when(rs.getString("status")).thenReturn(statuses[0], java.util.Arrays.copyOfRange(statuses, 1, statuses.length));
            when(rs.getTimestamp("purchase_date_time")).thenReturn(purchases[0], java.util.Arrays.copyOfRange(purchases, 1, purchases.length));
            when(rs.getTimestamp("booking_expiry_date_time")).thenReturn(expiries[0], java.util.Arrays.copyOfRange(expiries, 1, expiries.length));
        }
    }


    @Test
    void getAllTickets_noFilter_success_returnsAllTickets() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        setupMockResultSetForGetAllTickets(mockResultSet, testTicket1, testTicket2);

        List<Ticket> tickets = ticketDAO.getAllTickets(null);

        assertNotNull(tickets);
        assertEquals(2, tickets.size());
        assertTrue(tickets.contains(testTicket1));
        assertFalse(tickets.contains(testTicket2));
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 2 квитків. Фільтр за статусом: немає"));
        verify(mockPreparedStatement, never()).setObject(anyInt(), any());
        verify(mockPreparedStatement).executeQuery();
    }

    @Test
    void getAllTickets_withFilter_success_returnsFilteredTickets() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);


        setupMockResultSetForGetAllTickets(mockResultSet, testTicket2);

        List<Ticket> tickets = ticketDAO.getAllTickets(TicketStatus.SOLD);

        assertNotNull(tickets);
        assertEquals(1, tickets.size());
        assertTrue(tickets.contains(testTicket2));
        assertFalse(tickets.contains(testTicket1));
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 1 квитків. Фільтр за статусом: SOLD"));
        verify(mockPreparedStatement).setObject(1, TicketStatus.SOLD.name());
        verify(mockPreparedStatement).executeQuery();
    }

    @Test
    void getAllTickets_noTicketsFound_returnsEmptyList() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<Ticket> tickets = ticketDAO.getAllTickets(null);

        assertNotNull(tickets);
        assertTrue(tickets.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 0 квитків. Фільтр за статусом: немає"));
    }

    @Test
    void getAllTickets_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB Read All Error"));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getAllTickets(null));
        assertEquals("DB Read All Error", ex.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх квитків."));
    }

    @Test
    void getAllTickets_flightNotFoundForTicket_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);


        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testTicket1.getId());
        when(mockResultSet.getLong("flight_id")).thenReturn(testTicket1.getFlight().getId());
        when(mockResultSet.getLong("passenger_id")).thenReturn(testTicket1.getPassenger().getId());
        when(mockResultSet.getString("seat_number")).thenReturn(testTicket1.getSeatNumber());
        when(mockResultSet.getTimestamp("booking_date_time")).thenReturn(Timestamp.valueOf(testTicket1.getBookingDateTime()));
        when(mockResultSet.getBigDecimal("price_paid")).thenReturn(testTicket1.getPricePaid());
        when(mockResultSet.getString("status")).thenReturn(testTicket1.getStatus().name());
        when(mockResultSet.getTimestamp("purchase_date_time")).thenReturn(null);
        when(mockResultSet.getTimestamp("booking_expiry_date_time")).thenReturn(Timestamp.valueOf(testTicket1.getBookingExpiryDateTime()));


        when(mockFlightDAO.getFlightById(testTicket1.getFlight().getId())).thenReturn(Optional.empty());
        when(mockPassengerDAO.findById(testTicket1.getPassenger().getId())).thenReturn(Optional.of(testPassenger));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getAllTickets(null));
        assertTrue(ex.getMessage().contains("Рейс ID " + testTicket1.getFlight().getId() + " не знайдено для квитка ID: " + testTicket1.getId()));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Рейс ID " + testTicket1.getFlight().getId() + " не знайдено для квитка ID: " + testTicket1.getId()));
    }

    @Test
    void getAllTickets_passengerNotFoundForTicket_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);


        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("id")).thenReturn(testTicket1.getId());
        when(mockResultSet.getLong("flight_id")).thenReturn(testTicket1.getFlight().getId());
        when(mockResultSet.getLong("passenger_id")).thenReturn(testTicket1.getPassenger().getId());
        when(mockResultSet.getString("seat_number")).thenReturn(testTicket1.getSeatNumber());
        when(mockResultSet.getTimestamp("booking_date_time")).thenReturn(Timestamp.valueOf(testTicket1.getBookingDateTime()));
        when(mockResultSet.getBigDecimal("price_paid")).thenReturn(testTicket1.getPricePaid());
        when(mockResultSet.getString("status")).thenReturn(testTicket1.getStatus().name());
        when(mockResultSet.getTimestamp("purchase_date_time")).thenReturn(null);
        when(mockResultSet.getTimestamp("booking_expiry_date_time")).thenReturn(Timestamp.valueOf(testTicket1.getBookingExpiryDateTime()));


        when(mockFlightDAO.getFlightById(testTicket1.getFlight().getId())).thenReturn(Optional.of(testFlight));
        when(mockPassengerDAO.findById(testTicket1.getPassenger().getId())).thenReturn(Optional.empty());

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getAllTickets(null));
        assertTrue(ex.getMessage().contains("Пасажира ID " + testTicket1.getPassenger().getId() + " не знайдено для квитка ID: " + testTicket1.getId()));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Пасажира ID " + testTicket1.getPassenger().getId() + " не знайдено для квитка ID: " + testTicket1.getId()));
    }



    @Test
    void getSalesByRouteForPeriod_success_returnsSalesData() throws SQLException {
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        long routeId = testRoute.getId();

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("route_id")).thenReturn(routeId);
        when(mockResultSet.getBigDecimal("total_amount")).thenReturn(BigDecimal.valueOf(1000));
        when(mockResultSet.getInt("tickets_sold")).thenReturn(5);

        when(mockRouteDAO.getRouteById(routeId)).thenReturn(Optional.of(testRoute));

        Map<String, Map<String, Object>> salesData = ticketDAO.getSalesByRouteForPeriod(startDate, endDate);

        assertNotNull(salesData);
        assertEquals(1, salesData.size());
        assertTrue(salesData.containsKey(testRoute.getFullRouteDescription()));
        Map<String, Object> routeData = salesData.get(testRoute.getFullRouteDescription());
        assertEquals(BigDecimal.valueOf(1000), routeData.get("totalSales"));
        assertEquals(5, routeData.get("ticketCount"));
        verify(mockPreparedStatement).setDate(1, Date.valueOf(startDate));
        verify(mockPreparedStatement).setDate(2, Date.valueOf(endDate));
    }


    @Test
    void getSalesByRouteForPeriod_routeForSaleNotFound_usesDefaultDescription() throws SQLException {
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        long unknownRouteId = 999L;

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getLong("route_id")).thenReturn(unknownRouteId);
        when(mockResultSet.getBigDecimal("total_amount")).thenReturn(BigDecimal.valueOf(1000));
        when(mockResultSet.getInt("tickets_sold")).thenReturn(5);

        when(mockRouteDAO.getRouteById(unknownRouteId)).thenReturn(Optional.empty());

        Map<String, Map<String, Object>> salesData = ticketDAO.getSalesByRouteForPeriod(startDate, endDate);

        String expectedKey = "Невідомий або видалений маршрут (ID: " + unknownRouteId + ")";
        assertTrue(salesData.containsKey(expectedKey));
        assertTrue(listAppender.containsMessage(Level.WARN, "Маршрут з ID " + unknownRouteId + " не знайдено під час генерації звіту продажів"));
    }


    @Test
    void getTicketCountsByStatus_success_returnsCounts() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("status")).thenReturn(TicketStatus.BOOKED.name()).thenReturn(TicketStatus.SOLD.name());
        when(mockResultSet.getInt("count")).thenReturn(5).thenReturn(10);

        Map<TicketStatus, Integer> counts = ticketDAO.getTicketCountsByStatus();

        assertNotNull(counts);
        assertEquals(Integer.valueOf(5), counts.get(TicketStatus.BOOKED));
        assertEquals(Integer.valueOf(10), counts.get(TicketStatus.SOLD));
        for (TicketStatus ts : TicketStatus.values()) {
            assertTrue(counts.containsKey(ts));
            assertNotNull(counts.get(ts));
        }
        assertEquals(Integer.valueOf(0), counts.get(TicketStatus.CANCELLED));
        assertTrue(listAppender.containsMessage(Level.INFO, "Кількість квитків за статусами отримана"));
    }

    @Test
    void getTicketCountsByStatus_unknownStatusInDb_logsWarning() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("status")).thenReturn("INVALID_DB_STATUS");
        when(mockResultSet.getInt("count")).thenReturn(3);

        Map<TicketStatus, Integer> counts = ticketDAO.getTicketCountsByStatus(); // Викликаємо метод
        assertTrue(listAppender.containsMessage(Level.WARN, "Невідомий статус квитка 'INVALID_DB_STATUS' знайдено в базі даних під час підрахунку."));
        assertTrue(counts.containsKey(TicketStatus.valueOf("BOOKED")));
        assertEquals(0, counts.get(TicketStatus.BOOKED));
        assertEquals(0, counts.get(TicketStatus.SOLD));
    }

    @Test
    void getTicketCountsByStatus_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("DB Count Error"));

        SQLException ex = assertThrows(SQLException.class, () -> ticketDAO.getTicketCountsByStatus());
        assertEquals("DB Count Error", ex.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні кількості квитків за статусами."));
    }
}
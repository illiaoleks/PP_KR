package DAO;

import DB.DatabaseConnectionManager;
import Models.Enums.BenefitType;
import Models.Passenger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerDAOTest {

    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private Statement mockStatement;
    @Mock
    private ResultSet mockResultSet;


    @Spy
    @InjectMocks
    private PassengerDAO passengerDAO;

    private static ListAppender listAppender;
    private static org.apache.logging.log4j.core.Logger insuranceLogger;
    private static MockedStatic<DatabaseConnectionManager> mockedDbManager;


    private Passenger testPassenger1;
    private Passenger testPassenger2;

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
        insuranceLogger = context.getLogger("insurance.log");
        listAppender = new ListAppender("TestPassengerDAOAppender");
        listAppender.start();
        insuranceLogger.addAppender(listAppender);
        insuranceLogger.setLevel(Level.ALL);
        mockedDbManager = Mockito.mockStatic(DatabaseConnectionManager.class);
    }

    @AfterAll
    static void tearDownLogAppenderAndStaticMock() {
        if (listAppender != null) {
            insuranceLogger.removeAppender(listAppender);
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

        // Ініціалізація тестових даних
        testPassenger1 = new Passenger(1L, "Іван Іванов", "АА123456", "Паспорт",
                "0501234567", "ivan@example.com", BenefitType.NONE);
        testPassenger2 = new Passenger(2L, "Марія Петренко", "ВВ654321", "ID-карта",
                "0679876543", "maria@example.com", BenefitType.STUDENT);
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
        reset(mockConnection, mockPreparedStatement, mockStatement, mockResultSet);
    }

    private void mockPassengerResultSetRow(Passenger passenger) throws SQLException {
        lenient().when(mockResultSet.getLong("id")).thenReturn(passenger.getId());
        lenient().when(mockResultSet.getString("full_name")).thenReturn(passenger.getFullName());
        lenient().when(mockResultSet.getString("document_number")).thenReturn(passenger.getDocumentNumber());
        lenient().when(mockResultSet.getString("document_type")).thenReturn(passenger.getDocumentType());
        lenient().when(mockResultSet.getString("phone_number")).thenReturn(passenger.getPhoneNumber());
        lenient().when(mockResultSet.getString("email")).thenReturn(passenger.getEmail());
        lenient().when(mockResultSet.getString("benefit_type")).thenReturn(passenger.getBenefitType().name());
    }


    @Test
    void addOrGetPassenger_existingPassenger_returnsExistingId() throws SQLException {

        doReturn(Optional.of(testPassenger1)).when(passengerDAO)
                .findByDocument(testPassenger1.getDocumentType(), testPassenger1.getDocumentNumber());

        long passengerId = passengerDAO.addOrGetPassenger(testPassenger1);

        assertEquals(testPassenger1.getId(), passengerId);
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажир з документом Тип=" + testPassenger1.getDocumentType() + ", Номер=" + testPassenger1.getDocumentNumber() + " вже існує з ID=" + testPassenger1.getId()));
        verify(passengerDAO).findByDocument(testPassenger1.getDocumentType(), testPassenger1.getDocumentNumber());
        verify(mockConnection, never()).prepareStatement(anyString(), anyInt());
    }

    @Test
    void addOrGetPassenger_newPassenger_addsAndReturnsNewId() throws SQLException {
        Passenger newPassengerDetails = new Passenger(0L, "Новий Пасажир", "XX999888", "Паспорт",
                "0991112233", "new@example.com", BenefitType.NONE);
        long generatedId = 100L;


        doReturn(Optional.empty()).when(passengerDAO)
                .findByDocument(newPassengerDetails.getDocumentType(), newPassengerDetails.getDocumentNumber());


        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1); // 1 рядок додано
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(generatedId);

        long passengerId = passengerDAO.addOrGetPassenger(newPassengerDetails);

        assertEquals(generatedId, passengerId);
        assertTrue(listAppender.containsMessage(Level.INFO, "Нового пасажира успішно додано. ID нового пасажира: " + generatedId));
        verify(passengerDAO).findByDocument(newPassengerDetails.getDocumentType(), newPassengerDetails.getDocumentNumber());
        verify(mockPreparedStatement).setString(1, newPassengerDetails.getFullName());
        verify(mockPreparedStatement).setString(6, newPassengerDetails.getBenefitType().name());
        verify(mockPreparedStatement).executeUpdate();
    }

    @Test
    void addOrGetPassenger_newPassenger_insertFailsNoGeneratedKeys_throwsSQLException() throws SQLException {
        Passenger newPassengerDetails = new Passenger(0L, "Проблемний Пасажир", "ZZ000111", "ID-карта",
                "0990000000", "problem@example.com", BenefitType.PENSIONER);

        doReturn(Optional.empty()).when(passengerDAO)
                .findByDocument(newPassengerDetails.getDocumentType(), newPassengerDetails.getDocumentNumber());

        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        SQLException exception = assertThrows(SQLException.class, () -> passengerDAO.addOrGetPassenger(newPassengerDetails));
        assertTrue(exception.getMessage().contains("Не вдалося створити пасажира, ключі не згенеровано."));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Не вдалося створити пасажира, ключі не згенеровано, хоча affectedRows > 0."));
    }

    @Test
    void addOrGetPassenger_newPassenger_insertFailsAffectedRowsZero_throwsSQLException() throws SQLException {
        Passenger newPassengerDetails = new Passenger(0L, "Невдалий Пасажир", "YY111222", "Паспорт",
                "0992223344", "fail@example.com", BenefitType.NONE);

        doReturn(Optional.empty()).when(passengerDAO)
                .findByDocument(newPassengerDetails.getDocumentType(), newPassengerDetails.getDocumentNumber());

        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        SQLException exception = assertThrows(SQLException.class, () -> passengerDAO.addOrGetPassenger(newPassengerDetails));
        assertTrue(exception.getMessage().contains("Не вдалося створити пасажира, жоден рядок не було змінено."));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Пасажира не було додано (affectedRows = 0)."));
    }


    @Test
    void addOrGetPassenger_unexpectedSQLExceptionOnInsert_rethrows() throws SQLException {
        Passenger newPassenger = new Passenger(0L, "Unexpected SQL", "UE123", "ID", "050", "ue@ex.com", BenefitType.NONE);
        SQLException unexpectedException = new SQLException("Непередбачена помилка БД", "99999");

        doReturn(Optional.empty()).when(passengerDAO).findByDocument(newPassenger.getDocumentType(), newPassenger.getDocumentNumber());
        when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(unexpectedException);

        SQLException actualException = assertThrows(SQLException.class, () -> passengerDAO.addOrGetPassenger(newPassenger));
        assertSame(unexpectedException, actualException);
        assertTrue(listAppender.containsMessage(Level.ERROR, "Непередбачена помилка SQL при додаванні пасажира."));
    }



    @Test
    void findByDocument_passengerExists_returnsOptionalOfPassenger() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        mockPassengerResultSetRow(testPassenger1);

        Optional<Passenger> result = passengerDAO.findByDocument(testPassenger1.getDocumentType(), testPassenger1.getDocumentNumber());

        assertTrue(result.isPresent());
        assertEquals(testPassenger1.getId(), result.get().getId());
        assertEquals(testPassenger1.getFullName(), result.get().getFullName());
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажира знайдено за документом: Тип=" + testPassenger1.getDocumentType()));
        verify(mockPreparedStatement).setString(1, testPassenger1.getDocumentType());
        verify(mockPreparedStatement).setString(2, testPassenger1.getDocumentNumber());
    }

    @Test
    void findByDocument_passengerNotExists_returnsEmptyOptional() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        Optional<Passenger> result = passengerDAO.findByDocument("НеіснуючийТип", "000000");

        assertFalse(result.isPresent());
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажира не знайдено за документом: Тип=НеіснуючийТип"));
    }

    @Test
    void findByDocument_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Find Error"));

        SQLException exception = assertThrows(SQLException.class,
                () -> passengerDAO.findByDocument(testPassenger1.getDocumentType(), testPassenger1.getDocumentNumber()));
        assertEquals("DB Find Error", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при пошуку пасажира за документом: Тип=" + testPassenger1.getDocumentType()));
    }


    @Test
    void findById_passengerExists_returnsOptionalOfPassenger() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        mockPassengerResultSetRow(testPassenger1);

        Optional<Passenger> result = passengerDAO.findById(testPassenger1.getId());

        assertTrue(result.isPresent());
        assertEquals(testPassenger1.getId(), result.get().getId());
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажира знайдено за ID " + testPassenger1.getId()));
        verify(mockPreparedStatement).setLong(1, testPassenger1.getId());
    }

    @Test
    void findById_passengerNotExists_returnsEmptyOptional() throws SQLException {
        long nonExistentId = 999L;
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        Optional<Passenger> result = passengerDAO.findById(nonExistentId);

        assertFalse(result.isPresent());
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажира з ID " + nonExistentId + " не знайдено."));
    }

    @Test
    void findById_sqlException_throwsSQLException() throws SQLException {
        long passengerId = 1L;
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Find By ID Error"));

        SQLException exception = assertThrows(SQLException.class, () -> passengerDAO.findById(passengerId));
        assertEquals("DB Find By ID Error", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при пошуку пасажира за ID " + passengerId));
    }



    @Test
    void getAllPassengers_success_returnsListOfPassengers() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);


        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);

        when(mockResultSet.getLong("id")).thenReturn(testPassenger1.getId()).thenReturn(testPassenger2.getId());
        when(mockResultSet.getString("full_name")).thenReturn(testPassenger1.getFullName()).thenReturn(testPassenger2.getFullName());
        when(mockResultSet.getString("document_number")).thenReturn(testPassenger1.getDocumentNumber()).thenReturn(testPassenger2.getDocumentNumber());
        when(mockResultSet.getString("document_type")).thenReturn(testPassenger1.getDocumentType()).thenReturn(testPassenger2.getDocumentType());
        when(mockResultSet.getString("phone_number")).thenReturn(testPassenger1.getPhoneNumber()).thenReturn(testPassenger2.getPhoneNumber());
        when(mockResultSet.getString("email")).thenReturn(testPassenger1.getEmail()).thenReturn(testPassenger2.getEmail());
        when(mockResultSet.getString("benefit_type")).thenReturn(testPassenger1.getBenefitType().name()).thenReturn(testPassenger2.getBenefitType().name());

        List<Passenger> passengers = passengerDAO.getAllPassengers();

        assertNotNull(passengers);
        assertEquals(2, passengers.size());
        assertEquals(testPassenger1.getFullName(), passengers.get(0).getFullName());
        assertEquals(testPassenger2.getFullName(), passengers.get(1).getFullName());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 2 пасажирів."));
    }

    @Test
    void getAllPassengers_success_noPassengers_returnsEmptyList() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        List<Passenger> passengers = passengerDAO.getAllPassengers();

        assertNotNull(passengers);
        assertTrue(passengers.isEmpty());
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 0 пасажирів."));
    }

    @Test
    void getAllPassengers_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("DB Get All Error"));

        SQLException exception = assertThrows(SQLException.class, () -> passengerDAO.getAllPassengers());
        assertEquals("DB Get All Error", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх пасажирів"));
    }


    @Test
    void updatePassenger_success_returnsTrue() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        assertTrue(passengerDAO.updatePassenger(testPassenger1));
        assertTrue(listAppender.containsMessage(Level.INFO, "Пасажира з ID " + testPassenger1.getId() + " успішно оновлено."));
        verify(mockPreparedStatement).setString(1, testPassenger1.getFullName());
        verify(mockPreparedStatement).setString(6, testPassenger1.getBenefitType().name());
        verify(mockPreparedStatement).setLong(7, testPassenger1.getId());
    }

    @Test
    void updatePassenger_passengerNotFoundOrNotUpdated_returnsFalse() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        assertFalse(passengerDAO.updatePassenger(testPassenger1));
        assertTrue(listAppender.containsMessage(Level.WARN, "Пасажира з ID " + testPassenger1.getId() + " не знайдено або не було оновлено."));
    }

    @Test
    void updatePassenger_sqlException_throwsSQLException() throws SQLException {
        when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB Update Error"));

        SQLException exception = assertThrows(SQLException.class, () -> passengerDAO.updatePassenger(testPassenger1));
        assertEquals("DB Update Error", exception.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при оновленні пасажира з ID " + testPassenger1.getId()));
    }


    @Test
    void mapRowToPassenger_validBenefitType_mapsCorrectly() throws SQLException {
        when(mockResultSet.getLong("id")).thenReturn(testPassenger1.getId());
        when(mockResultSet.getString("full_name")).thenReturn(testPassenger1.getFullName());
        when(mockResultSet.getString("document_number")).thenReturn(testPassenger1.getDocumentNumber());
        when(mockResultSet.getString("document_type")).thenReturn(testPassenger1.getDocumentType());
        when(mockResultSet.getString("phone_number")).thenReturn(testPassenger1.getPhoneNumber());
        when(mockResultSet.getString("email")).thenReturn(testPassenger1.getEmail());
        when(mockResultSet.getString("benefit_type")).thenReturn(BenefitType.NONE.name());

        Passenger mapped = passengerDAO.mapRowToPassenger(mockResultSet);

        assertEquals(testPassenger1.getId(), mapped.getId());
        assertEquals(BenefitType.NONE, mapped.getBenefitType());
    }

}
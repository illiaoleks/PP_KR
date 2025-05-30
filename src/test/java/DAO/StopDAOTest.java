package DAO;

import DB.DatabaseConnectionManager;
import Models.Stop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StopDAOTest {

    @Mock
    private Connection mockConnection;
    @Mock
    private Statement mockStatement;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private ResultSet mockResultSet;

    private StopDAO stopDAO;

    private MockedStatic<DatabaseConnectionManager> mockedStaticDBManager;

    @BeforeEach
    void setUp() throws SQLException {
        mockedStaticDBManager = Mockito.mockStatic(DatabaseConnectionManager.class);
        lenient().when(mockConnection.isClosed()).thenReturn(false); // Дозволяємо виклик isClosed

        stopDAO = new StopDAO();
    }

    @AfterEach
    void tearDown() {
        mockedStaticDBManager.close();
    }



    @Test
    void getAllStops_shouldReturnListOfStops_whenDataExists() throws SQLException {
        // Arrange
        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getLong("id")).thenReturn(1L, 2L);
        when(mockResultSet.getString("name")).thenReturn("Stop A", "Stop B");
        when(mockResultSet.getString("city")).thenReturn("City X", "City Y");


        List<Stop> stops = stopDAO.getAllStops();


        assertNotNull(stops);
        assertEquals(2, stops.size());


        verify(mockConnection, times(1)).createStatement();
        verify(mockStatement, times(1)).executeQuery(anyString());


        verify(mockResultSet, times(1)).close();
        verify(mockStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getAllStops_shouldReturnEmptyList_whenNoDataExists() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);


        List<Stop> stops = stopDAO.getAllStops();


        assertNotNull(stops);
        assertTrue(stops.isEmpty());

        verify(mockResultSet, times(1)).next();
        verify(mockResultSet, never()).getLong(anyString());

        verify(mockResultSet, times(1)).close();
        verify(mockStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getAllStops_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("Database query failed"));


        SQLException exception = assertThrows(SQLException.class, () -> stopDAO.getAllStops());
        assertEquals("Database query failed", exception.getMessage());

        verify(mockResultSet, never()).close();
        verify(mockStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getAllStops_shouldThrowSQLException_whenGetConnectionFails() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection)
                .thenThrow(new SQLException("Failed to get connection"));


        SQLException exception = assertThrows(SQLException.class, () -> stopDAO.getAllStops());
        assertEquals("Failed to get connection", exception.getMessage());

        verify(mockConnection, never()).createStatement();
        verify(mockConnection, never()).isClosed();
        verify(mockConnection, never()).close();
        verify(mockStatement, never()).close();
    }




    @Test
    void getStopById_shouldReturnStop_whenFound() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        long testId = 1L;
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(testId);
        when(mockResultSet.getString("name")).thenReturn("Test Stop");
        when(mockResultSet.getString("city")).thenReturn("Test City");

        Optional<Stop> result = stopDAO.getStopById(testId);


        assertTrue(result.isPresent());


        verify(mockConnection, times(1)).prepareStatement(anyString());
        verify(mockPreparedStatement, times(1)).setLong(1, testId);
        verify(mockPreparedStatement, times(1)).executeQuery();

        verify(mockResultSet, times(1)).close();
        verify(mockPreparedStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getStopById_shouldReturnEmptyOptional_whenNotFound() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        long testId = 99L;
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);


        Optional<Stop> result = stopDAO.getStopById(testId);


        assertFalse(result.isPresent());

        verify(mockPreparedStatement, times(1)).setLong(1, testId);
        verify(mockResultSet, times(1)).next();
        verify(mockResultSet, never()).getLong(anyString());

        verify(mockResultSet, times(1)).close();
        verify(mockPreparedStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getStopById_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        long testId = 1L;
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Query failed for ID"));


        SQLException exception = assertThrows(SQLException.class, () -> stopDAO.getStopById(testId));
        assertEquals("Query failed for ID", exception.getMessage());

        verify(mockPreparedStatement, times(1)).setLong(1, testId);
        verify(mockResultSet, never()).close();
        verify(mockPreparedStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getStopById_shouldThrowSQLException_whenSetLongFails() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        long testId = 1L;
        doThrow(new SQLException("Failed to set long parameter")).when(mockPreparedStatement).setLong(1, testId);


        SQLException exception = assertThrows(SQLException.class, () -> stopDAO.getStopById(testId));
        assertEquals("Failed to set long parameter", exception.getMessage());

        verify(mockPreparedStatement, never()).executeQuery();
        verify(mockPreparedStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
        verify(mockResultSet, never()).close();
    }

    @Test
    void getAllStops_shouldCloseResources_evenIfResultSetNextThrowsException() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenThrow(new SQLException("Error during rs.next()"));


        assertThrows(SQLException.class, () -> stopDAO.getAllStops());

        verify(mockResultSet, times(1)).close();
        verify(mockStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }

    @Test
    void getStopById_shouldCloseResources_evenIfResultSetNextThrowsException() throws SQLException {

        mockedStaticDBManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        long testId = 1L;
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenThrow(new SQLException("Error during rs.next() for getById"));


        assertThrows(SQLException.class, () -> stopDAO.getStopById(testId));

        verify(mockPreparedStatement, times(1)).setLong(1, testId);
        verify(mockResultSet, times(1)).close();
        verify(mockPreparedStatement, times(1)).close();
        verify(mockConnection, times(1)).close();
    }
}
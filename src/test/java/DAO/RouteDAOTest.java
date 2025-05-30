package DAO;

import DB.DatabaseConnectionManager;
import Models.Route;
import Models.Stop;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteDAOTest {

    @Plugin(name = "TestListAppenderRouteDAO", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
    public static class ListAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());
        protected ListAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
            super(name, filter, layout, ignoreExceptions, properties);
        }
        @PluginFactory
        public static ListAppender createAppender(@PluginAttribute("name") String name, @PluginElement("Layout") Layout<? extends Serializable> layout, @PluginElement("Filter") final Filter filter) {
            if (name == null) { LOGGER.error("No name provided for ListAppender"); return null; }
            return new ListAppender(name, filter, layout, true, null);
        }
        @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        public List<LogEvent> getEvents() { return new ArrayList<>(events); }
        public void clearEvents() { events.clear(); }
        public boolean containsMessage(Level level, String partialMessage) {
            return events.stream().anyMatch(event ->
                    event.getLevel().equals(level) &&
                            event.getMessage().getFormattedMessage().contains(partialMessage));
        }
        public boolean containsExactMessage(Level level, String exactMessage) {
            return events.stream().anyMatch(event ->
                    event.getLevel().equals(level) &&
                            event.getMessage().getFormattedMessage().equals(exactMessage));
        }
    }

    @Mock private Connection mockConnection;
    @Mock private PreparedStatement mockPsRoutes;
    @Mock private PreparedStatement mockPsIntermediateStops;
    @Mock private Statement mockStatement;
    @Mock private ResultSet mockRsRoutes;
    @Mock private ResultSet mockRsIntermediateStops;
    @Mock private ResultSet mockGeneratedKeys;


    @Mock private PreparedStatement mockPsStopGetById;
    @Mock private ResultSet mockRsStopGetById;



    @Mock private StopDAO mockStopDAO_UnusedDueToDirectInstantiationInSUT;

    @InjectMocks
    private RouteDAO routeDAO;

    private static ListAppender listAppender;
    private static org.apache.logging.log4j.core.Logger insuranceLogger;
    private static MockedStatic<DatabaseConnectionManager> mockedDbManager;

    private Stop stop1, stop2, stop3, stop4, stop5;
    private Route routeKyivLviv, routeKyivOdesa;

    private final String SQL_GET_ALL_ROUTES = "SELECT id, departure_stop_id, destination_stop_id FROM routes ORDER BY id";
    private final String SQL_GET_ROUTE_BY_ID = "SELECT id, departure_stop_id, destination_stop_id FROM routes WHERE id = ?";
    private final String SQL_INSERT_ROUTE = "INSERT INTO routes (departure_stop_id, destination_stop_id) VALUES (?, ?)";
    private final String SQL_GET_INTERMEDIATE_STOPS = "SELECT stop_id FROM route_intermediate_stops WHERE route_id = ? ORDER BY stop_order";
    private final String SQL_INSERT_INTERMEDIATE_STOP = "INSERT INTO route_intermediate_stops (route_id, stop_id, stop_order) VALUES (?, ?, ?)";
    private final String SQL_GET_STOP_BY_ID_IN_STOPDAO = "SELECT id, name, city FROM stops WHERE id = ?";


    @BeforeAll
    static void setupLogAppenderAndStaticMock() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        listAppender = new ListAppender("TestRouteDAOAppender", null, PatternLayout.createDefaultLayout(config), true, Property.EMPTY_ARRAY);
        listAppender.start();
        insuranceLogger = context.getLogger("insurance.log");

        insuranceLogger.addAppender(listAppender);
        insuranceLogger.setLevel(Level.ALL);
        mockedDbManager = Mockito.mockStatic(DatabaseConnectionManager.class);
    }

    @AfterAll
    static void tearDownLogAppenderAndStaticMock() {
        if (listAppender != null && insuranceLogger != null) {
            insuranceLogger.removeAppender(listAppender);
            listAppender.stop();
        }

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        if (context != null && context.getRootLogger() != null && listAppender != null) {
            context.getRootLogger().removeAppender(listAppender);
        }


        if (mockedDbManager != null && !mockedDbManager.isClosed()) {
            mockedDbManager.close();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        listAppender.clearEvents();
        mockedDbManager.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);

        lenient().when(mockConnection.prepareStatement(eq(SQL_GET_ROUTE_BY_ID))).thenReturn(mockPsRoutes);
        lenient().when(mockPsRoutes.executeQuery()).thenReturn(mockRsRoutes);

        lenient().when(mockConnection.createStatement()).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery(eq(SQL_GET_ALL_ROUTES))).thenReturn(mockRsRoutes);

        lenient().when(mockConnection.prepareStatement(eq(SQL_INSERT_ROUTE), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPsRoutes);
        lenient().when(mockPsRoutes.getGeneratedKeys()).thenReturn(mockGeneratedKeys);
        lenient().when(mockConnection.prepareStatement(eq(SQL_INSERT_INTERMEDIATE_STOP))).thenReturn(mockPsIntermediateStops);

        lenient().when(mockConnection.prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS))).thenReturn(mockPsIntermediateStops);
        lenient().when(mockPsIntermediateStops.executeQuery()).thenReturn(mockRsIntermediateStops);

        mockPsStopGetById = mock(PreparedStatement.class);
        mockRsStopGetById = mock(ResultSet.class);
        lenient().when(mockConnection.prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO))).thenReturn(mockPsStopGetById);
        lenient().when(mockPsStopGetById.executeQuery()).thenReturn(mockRsStopGetById);


        stop1 = new Stop(1L, "Київ-Вокзал", "Київ");
        stop2 = new Stop(2L, "Житомир-Центр", "Житомир");
        stop3 = new Stop(3L, "Рівне-Ринок", "Рівне");
        stop4 = new Stop(4L, "Львів-Аеропорт", "Львів");
        stop5 = new Stop(5L, "Одеса-Привоз", "Одеса");

        routeKyivLviv = new Route(101L, stop1, stop4, Arrays.asList(stop2, stop3));
        routeKyivOdesa = new Route(102L, stop1, stop5, Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        reset(mockConnection, mockPsRoutes, mockPsIntermediateStops,
                mockStatement, mockRsRoutes, mockRsIntermediateStops, mockGeneratedKeys,
                mockPsStopGetById, mockRsStopGetById, mockStopDAO_UnusedDueToDirectInstantiationInSUT);
    }

    @SuppressWarnings("unchecked")
    private List<Stop> invokeGetIntermediateStopsForRoute(Connection conn, long routeId) throws Exception {
        Method method = RouteDAO.class.getDeclaredMethod("getIntermediateStopsForRoute", Connection.class, long.class);
        method.setAccessible(true);
        try {
            return (List<Stop>) method.invoke(routeDAO, conn, routeId);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException) throw (SQLException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause != null) throw new RuntimeException("InvocationTargetException with unexpected cause: " + cause.getClass().getName(), cause);
            throw new RuntimeException("InvocationTargetException with null cause", e);
        }
    }




    @Test
    @DisplayName("[GISFR] Повинен повертати список проміжних зупинок, якщо вони існують")
    void getIntermediateStopsForRoute_shouldReturnListOfStops_whenStopsExist() throws Exception {
        long routeId = 1L;
        when(mockRsIntermediateStops.next()).thenReturn(true, true, false);
        when(mockRsIntermediateStops.getLong("stop_id")).thenReturn(stop1.getId(), stop2.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId())
                .thenReturn(stop2.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName())
                .thenReturn(stop2.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity())
                .thenReturn(stop2.getCity());

        List<Stop> result = invokeGetIntermediateStopsForRoute(mockConnection, routeId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getId() == stop1.getId()));
        assertTrue(result.stream().anyMatch(s -> s.getId() == stop2.getId()));
        verify(mockPsIntermediateStops).setLong(1, routeId);
        verify(mockConnection, times(2)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.DEBUG, "Завантаження проміжних зупинок для маршруту ID: " + routeId));
        assertTrue(listAppender.containsMessage(Level.DEBUG, "Знайдено 2 проміжних зупинок для маршруту ID: " + routeId));
    }


    @Test
    @DisplayName("[GISFR] Повинен повертати порожній список, якщо немає проміжних зупинок")
    void getIntermediateStopsForRoute_shouldReturnEmptyList_whenNoStopsExist() throws Exception {
        long routeId = 2L;
        when(mockRsIntermediateStops.next()).thenReturn(false);

        List<Stop> result = invokeGetIntermediateStopsForRoute(mockConnection, routeId);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Список зупинок має бути порожнім");
        verify(mockConnection, never()).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.DEBUG, "Знайдено 0 проміжних зупинок для маршруту ID: " + routeId));
    }

    @Test
    @DisplayName("[GISFR] Повинен логувати попередження, якщо проміжна зупинка не знайдена в StopDAO")
    void getIntermediateStopsForRoute_shouldLogWarning_whenIntermediateStopNotFound() throws Exception {
        long routeId = 3L;
        long missingStopId = 99L;
        when(mockRsIntermediateStops.next()).thenReturn(true, false);
        when(mockRsIntermediateStops.getLong("stop_id")).thenReturn(missingStopId);

        when(mockRsStopGetById.next()).thenReturn(false);

        List<Stop> result = invokeGetIntermediateStopsForRoute(mockConnection, routeId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(mockConnection, times(1)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        verify(mockPsStopGetById).setLong(1, missingStopId);
        assertTrue(listAppender.containsMessage(Level.WARN, "Проміжна зупинка з ID " + missingStopId + " для маршруту ID " + routeId + " не знайдена в таблиці зупинок, але на неї є посилання."));
    }

    @Test
    @DisplayName("[GISFR] Повинен кидати SQLException, якщо prepareStatement для проміжних зупинок кидає виняток")
    void getIntermediateStopsForRoute_shouldThrowSQLException_whenPrepareStatementFails() throws SQLException {
        long routeId = 5L;
        SQLException expectedException = new SQLException("Помилка підготовки PreparedStatement для проміжних");
        when(mockConnection.prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS))).thenThrow(expectedException);

        SQLException actualException = assertThrows(SQLException.class,
                () -> invokeGetIntermediateStopsForRoute(mockConnection, routeId));
        assertEquals(expectedException.getMessage(), actualException.getMessage());
    }

    @Test
    @DisplayName("[GISFR] Повинен кидати SQLException, якщо executeQuery для проміжних зупинок кидає виняток")
    void getIntermediateStopsForRoute_shouldThrowSQLException_whenExecuteQueryFails() throws SQLException {
        long routeId = 6L;
        SQLException expectedException = new SQLException("Помилка виконання запиту для проміжних");
        when(mockPsIntermediateStops.executeQuery()).thenThrow(expectedException);

        SQLException actualException = assertThrows(SQLException.class,
                () -> invokeGetIntermediateStopsForRoute(mockConnection, routeId));
        assertEquals(expectedException.getMessage(), actualException.getMessage());
    }

    @Test
    @DisplayName("[GISFR] Повинен кидати SQLException, якщо rs.next для проміжних зупинок кидає виняток")
    void getIntermediateStopsForRoute_shouldThrowSQLException_whenResultSetNextFails() throws SQLException {
        long routeId = 7L;
        SQLException expectedException = new SQLException("Помилка ResultSet.next() для проміжних");
        when(mockRsIntermediateStops.next()).thenThrow(expectedException);

        SQLException actualException = assertThrows(SQLException.class,
                () -> invokeGetIntermediateStopsForRoute(mockConnection, routeId));
        assertEquals(expectedException.getMessage(), actualException.getMessage());
    }

    @Test
    @DisplayName("[GISFR] Повинен кидати SQLException, якщо rs.getLong для проміжних зупинок кидає виняток")
    void getIntermediateStopsForRoute_shouldThrowSQLException_whenResultSetGetLongFails() throws SQLException {
        long routeId = 8L;
        SQLException expectedException = new SQLException("Помилка ResultSet.getLong() для проміжних");
        when(mockRsIntermediateStops.next()).thenReturn(true);
        when(mockRsIntermediateStops.getLong("stop_id")).thenThrow(expectedException);

        SQLException actualException = assertThrows(SQLException.class,
                () -> invokeGetIntermediateStopsForRoute(mockConnection, routeId));
        assertEquals(expectedException.getMessage(), actualException.getMessage());
    }



    @Test
    @DisplayName("[GRBID] Повинен повертати маршрут, якщо він існує та всі зупинки знайдені")
    void getRouteById_success_routeFound() throws SQLException {
        long routeId = routeKyivLviv.getId();
        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop4.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId())
                .thenReturn(stop4.getId())
                .thenReturn(stop2.getId())
                .thenReturn(stop3.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName())
                .thenReturn(stop4.getName())
                .thenReturn(stop2.getName())
                .thenReturn(stop3.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity())
                .thenReturn(stop4.getCity())
                .thenReturn(stop2.getCity())
                .thenReturn(stop3.getCity());

        when(mockRsIntermediateStops.next()).thenReturn(true, true, false);
        when(mockRsIntermediateStops.getLong("stop_id")).thenReturn(stop2.getId(), stop3.getId());

        Optional<Route> resultOpt = routeDAO.getRouteById(routeId);

        assertTrue(resultOpt.isPresent());
        Route resultRoute = resultOpt.get();
        assertEquals(routeId, resultRoute.getId());
        assertEquals(stop1.getId(), resultRoute.getDepartureStop().getId());
        assertEquals(stop4.getId(), resultRoute.getDestinationStop().getId());
        assertEquals(2, resultRoute.getIntermediateStops().size());
        assertTrue(resultRoute.getIntermediateStops().stream().anyMatch(s -> s.getId() == stop2.getId()));
        assertTrue(resultRoute.getIntermediateStops().stream().anyMatch(s -> s.getId() == stop3.getId()));

        verify(mockPsRoutes).setLong(1, routeId);
        verify(mockPsIntermediateStops).setLong(1, routeId);
        verify(mockConnection, times(4)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.INFO, "Маршрут з ID " + routeId + " успішно отримано."));
    }

    @Test
    @DisplayName("[GRBID] Повинен повертати маршрут без проміжних зупинок, якщо їх немає")
    void getRouteById_success_routeFound_noIntermediateStops() throws SQLException {
        long routeId = routeKyivOdesa.getId();
        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop5.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId())
                .thenReturn(stop5.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName())
                .thenReturn(stop5.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity())
                .thenReturn(stop5.getCity());

        when(mockRsIntermediateStops.next()).thenReturn(false);

        Optional<Route> resultOpt = routeDAO.getRouteById(routeId);

        assertTrue(resultOpt.isPresent());
        Route resultRoute = resultOpt.get();
        assertEquals(routeId, resultRoute.getId());
        assertEquals(stop1.getId(), resultRoute.getDepartureStop().getId());
        assertEquals(stop5.getId(), resultRoute.getDestinationStop().getId());
        assertTrue(resultRoute.getIntermediateStops().isEmpty());

        verify(mockConnection, times(2)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.DEBUG, "Знайдено 0 проміжних зупинок для маршруту ID: " + routeId));
    }


    @Test
    @DisplayName("[GRBID] Повинен повертати порожній Optional, якщо маршрут не знайдено")
    void getRouteById_routeNotFound_returnsEmptyOptional() throws SQLException {
        long nonExistentRouteId = 999L;
        when(mockRsRoutes.next()).thenReturn(false);

        Optional<Route> result = routeDAO.getRouteById(nonExistentRouteId);

        assertFalse(result.isPresent());
        verify(mockConnection, never()).prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS));
        assertTrue(listAppender.containsMessage(Level.INFO, "Маршрут з ID " + nonExistentRouteId + " не знайдено."));
    }

    @Test
    @DisplayName("[GRBID] Повинен кидати SQLException, якщо зупинка відправлення не знайдена")
    void getRouteById_departureStopNotFound_throwsSQLException() throws SQLException {
        long routeId = 1L;
        long departureStopId = stop1.getId();
        long destinationStopId = stop4.getId();

        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(departureStopId);
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(destinationStopId);

        when(mockRsStopGetById.next()).thenReturn(false);

        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.getRouteById(routeId));
        String expectedErrorMsg = "Зупинка відправлення ID " + departureStopId + " не знайдена для маршруту ID: " + routeId;
        assertEquals(expectedErrorMsg, exception.getMessage());
        verify(mockPsStopGetById).setLong(1, departureStopId);
        assertTrue(listAppender.containsMessage(Level.ERROR, expectedErrorMsg));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні маршруту за ID " + routeId + "."));
    }

    @Test
    @DisplayName("[GRBID] Повинен кидати SQLException, якщо зупинка призначення не знайдена")
    void getRouteById_destinationStopNotFound_throwsSQLException() throws SQLException {
        long routeId = 1L;
        long departureStopId = stop1.getId();
        long destinationStopId = stop4.getId();

        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(departureStopId);
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(destinationStopId);

        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(false);
        when(mockRsStopGetById.getLong("id")).thenReturn(departureStopId);
        when(mockRsStopGetById.getString("name")).thenReturn(stop1.getName());
        when(mockRsStopGetById.getString("city")).thenReturn(stop1.getCity());

        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.getRouteById(routeId));
        String expectedErrorMsg = "Зупинка призначення ID " + destinationStopId + " не знайдена для маршруту ID: " + routeId;
        assertEquals(expectedErrorMsg, exception.getMessage());
        verify(mockPsStopGetById).setLong(1, departureStopId);
        verify(mockPsStopGetById).setLong(1, destinationStopId);
        assertTrue(listAppender.containsMessage(Level.ERROR, expectedErrorMsg));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні маршруту за ID " + routeId + "."));
    }

    @Test
    @DisplayName("[GRBID] Повинен кидати SQLException, якщо getIntermediateStopsForRoute кидає виняток")
    void getRouteById_getIntermediateStopsThrowsSQLException_propagatesException() throws SQLException {
        long routeId = routeKyivLviv.getId();
        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop4.getId());


        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId())
                .thenReturn(stop4.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName())
                .thenReturn(stop4.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity())
                .thenReturn(stop4.getCity());


        SQLException intermediateException = new SQLException("DB error fetching intermediate stops");
        when(mockConnection.prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS))).thenThrow(intermediateException);

        SQLException actualException = assertThrows(SQLException.class, () -> routeDAO.getRouteById(routeId));
        assertEquals(intermediateException.getMessage(), actualException.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні маршруту за ID " + routeId + "."));
    }


    @Test
    @DisplayName("[GRBID] Повинен кидати SQLException під час основного запиту SQL")
    void getRouteById_sqlExceptionDuringMainQuery_throwsSQLException() throws SQLException {
        long routeId = 1L;
        when(mockConnection.prepareStatement(eq(SQL_GET_ROUTE_BY_ID)))
                .thenThrow(new SQLException("DB Main Route Error"));
        assertThrows(SQLException.class, () -> routeDAO.getRouteById(routeId));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні маршруту за ID " + routeId + "."));
    }


    @Test
    @DisplayName("[GAR] Повинен повертати порожній список, якщо немає маршрутів в БД")
    void getAllRoutes_success_noRoutes_returnsEmptyList() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(false);
        List<Route> routes = routeDAO.getAllRoutes();
        assertTrue(routes.isEmpty());
        verify(mockConnection, never()).prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS));
        assertTrue(listAppender.containsMessage(Level.INFO, "Спроба отримати всі маршрути."));
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 0 маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен кидати SQLException, якщо connection.createStatement кидає виняток")
    void getAllRoutes_sqlExceptionOnCreateStatement_throwsSQLException() throws SQLException {
        when(mockConnection.createStatement()).thenThrow(new SQLException("DB Error on createStatement"));
        assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен кидати SQLException, якщо statement.executeQuery кидає виняток")
    void getAllRoutes_sqlExceptionOnStatementExecuteQuery_throwsSQLException() throws SQLException {
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("DB Error on executeQuery"));
        assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен кидати SQLException, якщо rsRoutes.next() кидає виняток")
    void getAllRoutes_sqlExceptionOnRoutesResultSetNext_throwsSQLException() throws SQLException {
        when(mockRsRoutes.next()).thenThrow(new SQLException("DB Error on rsRoutes.next()"));
        assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен кидати SQLException, якщо rsRoutes.getLong() кидає виняток")
    void getAllRoutes_sqlExceptionOnRoutesResultSetGetLong_throwsSQLException() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(true);
        when(mockRsRoutes.getLong("id")).thenThrow(new SQLException("DB Error on rsRoutes.getLong() for id"));
        assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен успішно отримати один маршрут з проміжними зупинками")
    void getAllRoutes_success_oneRouteWithIntermediateStops() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(true, false);
        when(mockRsRoutes.getLong("id")).thenReturn(routeKyivLviv.getId());
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop4.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId()).thenReturn(stop4.getId()).thenReturn(stop2.getId()).thenReturn(stop3.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName()).thenReturn(stop4.getName()).thenReturn(stop2.getName()).thenReturn(stop3.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity()).thenReturn(stop4.getCity()).thenReturn(stop2.getCity()).thenReturn(stop3.getCity());


        when(mockRsIntermediateStops.next()).thenReturn(true, true, false);
        when(mockRsIntermediateStops.getLong("stop_id")).thenReturn(stop2.getId(), stop3.getId());

        List<Route> routes = routeDAO.getAllRoutes();

        assertEquals(1, routes.size());
        Route actualRoute = routes.get(0);
        assertEquals(routeKyivLviv.getId(), actualRoute.getId());
        assertEquals(stop1.getId(), actualRoute.getDepartureStop().getId());
        assertEquals(stop4.getId(), actualRoute.getDestinationStop().getId());
        assertEquals(2, actualRoute.getIntermediateStops().size());

        verify(mockStatement).executeQuery(SQL_GET_ALL_ROUTES);
        verify(mockPsIntermediateStops).setLong(1, routeKyivLviv.getId());
        verify(mockConnection, times(4)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 1 маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Повинен успішно отримати один маршрут без проміжних зупинок")
    void getAllRoutes_success_oneRouteNoIntermediateStops() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(true, false);
        when(mockRsRoutes.getLong("id")).thenReturn(routeKyivOdesa.getId());
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop5.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true).thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId()).thenReturn(stop5.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName()).thenReturn(stop5.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity()).thenReturn(stop5.getCity());

        when(mockRsIntermediateStops.next()).thenReturn(false);

        List<Route> routes = routeDAO.getAllRoutes();

        assertEquals(1, routes.size());
        Route actualRoute = routes.get(0);
        assertEquals(routeKyivOdesa.getId(), actualRoute.getId());
        assertEquals(stop1.getId(), actualRoute.getDepartureStop().getId());
        assertEquals(stop5.getId(), actualRoute.getDestinationStop().getId());
        assertTrue(actualRoute.getIntermediateStops().isEmpty());
        verify(mockConnection, times(2)).prepareStatement(eq(SQL_GET_STOP_BY_ID_IN_STOPDAO));
        assertTrue(listAppender.containsMessage(Level.DEBUG, "Знайдено 0 проміжних зупинок для маршруту ID: " + routeKyivOdesa.getId()));
    }

    @Test
    @DisplayName("[GAR] Повинен успішно отримати декілька маршрутів (з/без проміжних зупинок)")
    void getAllRoutes_success_multipleRoutes_mixedIntermediateStops() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(true, true, false);
        when(mockRsRoutes.getLong("id"))
                .thenReturn(routeKyivLviv.getId())
                .thenReturn(routeKyivOdesa.getId());
        when(mockRsRoutes.getLong("departure_stop_id"))
                .thenReturn(stop1.getId())
                .thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id"))
                .thenReturn(stop4.getId())
                .thenReturn(stop5.getId());


        when(mockRsStopGetById.next())
                .thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(true)
                .thenReturn(true).thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId()).thenReturn(stop4.getId()).thenReturn(stop2.getId()).thenReturn(stop3.getId())
                .thenReturn(stop1.getId()).thenReturn(stop5.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName()).thenReturn(stop4.getName()).thenReturn(stop2.getName()).thenReturn(stop3.getName())
                .thenReturn(stop1.getName()).thenReturn(stop5.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity()).thenReturn(stop4.getCity()).thenReturn(stop2.getCity()).thenReturn(stop3.getCity())
                .thenReturn(stop1.getCity()).thenReturn(stop5.getCity());


        when(mockRsIntermediateStops.next())
                .thenReturn(true, true, false)
                .thenReturn(false);
        when(mockRsIntermediateStops.getLong("stop_id"))
                .thenReturn(stop2.getId())
                .thenReturn(stop3.getId());

        List<Route> routes = routeDAO.getAllRoutes();

        assertEquals(2, routes.size());

        assertTrue(listAppender.containsMessage(Level.INFO, "Успішно отримано 2 маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Кидає SQLException і логує помилку, якщо зупинка відправлення не знайдена")
    void getAllRoutes_departureStopNotFound_throwsSQLExceptionAndLogsError() throws SQLException {
        long routeId = 1L;
        long missingDepartureStopId = 998L;
        when(mockRsRoutes.next()).thenReturn(true, false);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(missingDepartureStopId);
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop5.getId());

        when(mockRsStopGetById.next()).thenReturn(false);
        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        String expectedErrorMsg = "Зупинка відправлення ID " + missingDepartureStopId + " не знайдена для маршруту ID: " + routeId;
        assertEquals(expectedErrorMsg, exception.getMessage());
        verify(mockPsStopGetById).setLong(1, missingDepartureStopId);
        assertTrue(listAppender.containsMessage(Level.ERROR, expectedErrorMsg));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Кидає SQLException і логує помилку, якщо зупинка призначення не знайдена")
    void getAllRoutes_destinationStopNotFound_throwsSQLExceptionAndLogsError() throws SQLException {
        long routeId = 2L;
        long missingDestinationStopId = 999L;
        when(mockRsRoutes.next()).thenReturn(true, false);
        when(mockRsRoutes.getLong("id")).thenReturn(routeId);
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(missingDestinationStopId);

        when(mockRsStopGetById.next())
                .thenReturn(true)
                .thenReturn(false);
        when(mockRsStopGetById.getLong("id")).thenReturn(stop1.getId());
        when(mockRsStopGetById.getString("name")).thenReturn(stop1.getName());
        when(mockRsStopGetById.getString("city")).thenReturn(stop1.getCity());


        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        String expectedErrorMsg = "Зупинка призначення ID " + missingDestinationStopId + " не знайдена для маршруту ID: " + routeId;
        assertEquals(expectedErrorMsg, exception.getMessage());
        verify(mockPsStopGetById).setLong(1, missingDestinationStopId);
        assertTrue(listAppender.containsMessage(Level.ERROR, expectedErrorMsg));
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }

    @Test
    @DisplayName("[GAR] Кидає SQLException, якщо getIntermediateStopsForRoute кидає виняток")
    void getAllRoutes_getIntermediateStopsForRouteThrowsSQLException_throwsSQLException() throws SQLException {
        when(mockRsRoutes.next()).thenReturn(true, false);
        when(mockRsRoutes.getLong("id")).thenReturn(routeKyivLviv.getId());
        when(mockRsRoutes.getLong("departure_stop_id")).thenReturn(stop1.getId());
        when(mockRsRoutes.getLong("destination_stop_id")).thenReturn(stop4.getId());

        when(mockRsStopGetById.next())
                .thenReturn(true).thenReturn(true);
        when(mockRsStopGetById.getLong("id"))
                .thenReturn(stop1.getId()).thenReturn(stop4.getId());
        when(mockRsStopGetById.getString("name"))
                .thenReturn(stop1.getName()).thenReturn(stop4.getName());
        when(mockRsStopGetById.getString("city"))
                .thenReturn(stop1.getCity()).thenReturn(stop4.getCity());


        SQLException intermediateException = new SQLException("DB error during intermediate stop fetch");
        when(mockConnection.prepareStatement(eq(SQL_GET_INTERMEDIATE_STOPS))).thenThrow(intermediateException);

        SQLException actualException = assertThrows(SQLException.class, () -> routeDAO.getAllRoutes());
        assertEquals(intermediateException.getMessage(), actualException.getMessage());
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при отриманні всіх маршрутів."));
    }




    @Test
    @DisplayName("[AR] Успішне додавання маршруту без проміжних зупинок")
    void addRoute_noIntermediateStops_success() throws SQLException {
        Route newRoute = new Route(0, stop1, stop2, Collections.emptyList());
        long generatedRouteId = 201L;

        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);

        boolean result = routeDAO.addRoute(newRoute);

        assertTrue(result);
        assertEquals(generatedRouteId, newRoute.getId());
        verify(mockConnection).commit();
        verify(mockPsIntermediateStops, never()).addBatch();
        assertTrue(listAppender.containsMessage(Level.INFO, "Основний маршрут успішно додано. ID нового маршруту: " + generatedRouteId));
        assertTrue(listAppender.containsMessage(Level.INFO, "Для маршруту ID: " + generatedRouteId + " немає проміжних зупинок."));
        assertTrue(listAppender.containsMessage(Level.INFO, "Маршрут " + newRoute.getFullRouteDescription() + " успішно додано до бази даних."));
    }



    @Test
    @DisplayName("[AR] Повертає false і робить rollback, якщо вставка основного маршруту не вдалася")
    void addRoute_mainRouteInsertFails_returnsFalseAndRollbacks() throws SQLException {
        Route newRoute = new Route(0, stop1, stop2, Collections.emptyList());
        when(mockPsRoutes.executeUpdate()).thenReturn(0);

        boolean result = routeDAO.addRoute(newRoute);

        assertFalse(result);
        verify(mockConnection).rollback();
        verify(mockConnection, never()).commit();
        assertTrue(listAppender.containsMessage(Level.WARN, "Не вдалося додати основний маршрут, жоден рядок не змінено."));

    }

    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо не вдалося отримати generated ID")
    void addRoute_getGeneratedKeysFails_throwsSQLExceptionAndRollbacks() throws SQLException {
        Route newRoute = new Route(0, stop1, stop2, Collections.emptyList());
        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(false);

        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals("Не вдалося отримати згенерований ID для маршруту.", exception.getMessage());
        verify(mockConnection, atLeastOnce()).rollback();
        verify(mockConnection, never()).commit();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Не вдалося отримати згенерований ID для нового маршруту."));
    }


    @Test
    @DisplayName("[AR] Пропускає null проміжну зупинку та логує попередження")
    void addRoute_withNullIntermediateStop_skipsItAndLogsWarn() throws SQLException {
        Route newRoute = new Route(0, stop1, stop4, Arrays.asList(stop2, null, stop3));
        long generatedRouteId = 203L;
        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);
        when(mockPsIntermediateStops.executeBatch()).thenReturn(new int[]{1, 1});

        boolean result = routeDAO.addRoute(newRoute);

        assertTrue(result);
        verify(mockPsIntermediateStops, times(2)).addBatch();
        assertTrue(listAppender.containsMessage(Level.WARN, "Проміжна зупинка є null або має ID 0 для маршруту ID " + generatedRouteId + ", пропуск."));
        verify(mockConnection).commit();
    }

    @Test
    @DisplayName("[AR] Пропускає проміжну зупинку з ID 0 та логує попередження")
    void addRoute_withIntermediateStopIdZero_skipsItAndLogsWarn() throws SQLException {
        Stop stopWithIdZero = new Stop(0L, "Zero ID Stop", "City Zero");
        Route newRoute = new Route(0, stop1, stop4, Arrays.asList(stop2, stopWithIdZero, stop3));
        long generatedRouteId = 203L;
        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);
        when(mockPsIntermediateStops.executeBatch()).thenReturn(new int[]{1, 1});

        boolean result = routeDAO.addRoute(newRoute);

        assertTrue(result);
        verify(mockPsIntermediateStops, times(2)).addBatch();
        assertTrue(listAppender.containsMessage(Level.WARN, "Проміжна зупинка є null або має ID 0 для маршруту ID " + generatedRouteId + ", пропуск."));
        verify(mockConnection).commit();
    }


    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо пакетне додавання проміжних зупинок не вдалося")
    void addRoute_intermediateBatchExecuteFails_throwsSQLExceptionAndRollbacks() throws SQLException {
        Route newRoute = new Route(0, stop1, stop4, Arrays.asList(stop2, stop3));
        long generatedRouteId = 204L;
        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);
        when(mockPsIntermediateStops.executeBatch()).thenReturn(new int[]{1, Statement.EXECUTE_FAILED});

        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals("Помилка при пакетному додаванні проміжних зупинок.", exception.getMessage());
        verify(mockConnection, atLeastOnce()).rollback();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при пакетному додаванні проміжних зупинок для маршруту ID: " + generatedRouteId + ". Операція для зупинки " + stop3.getName() + " не вдалася."));

    }

    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо SQL помилка при вставці основного маршруту")
    void addRoute_sqlExceptionOnMainInsert_throwsSQLExceptionAndRollbacks() throws SQLException {
        Route newRoute = new Route(0, stop1, stop2, Collections.emptyList());
        SQLException dbException = new SQLException("DB Route Insert Error");
        when(mockPsRoutes.executeUpdate()).thenThrow(dbException);

        SQLException actualException = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals(dbException, actualException);
        verify(mockConnection, atLeastOnce()).rollback();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка SQL при додаванні маршруту: " + dbException.getMessage()));
    }

    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо SQL помилка при вставці проміжних зупинок")
    void addRoute_sqlExceptionOnIntermediateInsert_throwsSQLExceptionAndRollbacks() throws SQLException {
        Route newRoute = new Route(0, stop1, stop4, Arrays.asList(stop2, stop3));
        long generatedRouteId = 205L;
        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);
        SQLException dbException = new SQLException("DB Intermediate Batch Error");
        when(mockPsIntermediateStops.executeBatch()).thenThrow(dbException);

        SQLException actualException = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals(dbException, actualException);
        verify(mockConnection, atLeastOnce()).rollback();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка SQL при додаванні маршруту: " + dbException.getMessage()));
    }

    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо ID зупинки відправлення = 0")
    void addRoute_departureStopIdIsZero_throwsSQLExceptionAndRollbacks() throws SQLException {
        Stop departureStopWithNoId = new Stop(0, "No ID Depart", "City");
        Route newRoute = new Route(0, departureStopWithNoId, stop2, Collections.emptyList());


        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals("Зупинка відправлення не може бути null або мати ID 0.", exception.getMessage());
        verify(mockConnection, atLeastOnce()).rollback();
        verify(mockConnection, never()).commit();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка SQL при додаванні маршруту: Зупинка відправлення не може бути null або мати ID 0."));
    }

    @Test
    @DisplayName("[AR] Кидає SQLException і робить rollback, якщо ID зупинки призначення = 0")
    void addRoute_destinationStopIdIsZero_throwsSQLExceptionAndRollbacks() throws SQLException {
        Stop destinationStopWithNoId = new Stop(0, "No ID Dest", "City");
        Route newRoute = new Route(0, stop1, destinationStopWithNoId, Collections.emptyList());

        SQLException exception = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals("Зупинка призначення не може бути null або мати ID 0.", exception.getMessage());
        verify(mockConnection, atLeastOnce()).rollback();
        verify(mockConnection, never()).commit();
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка SQL при додаванні маршруту: Зупинка призначення не може бути null або мати ID 0."));
    }

    @Test
    @DisplayName("[AR] Кидає IllegalArgumentException, якщо зупинка відправлення є null при створенні Route")
    void addRoute_nullDepartureStop_throwsIllegalArgumentException() throws SQLException {

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Route(0, null, stop2, Collections.emptyList()));
        assertEquals("Зупинка відправлення не може бути null.", exception.getMessage());

    }

    @Test
    @DisplayName("[AR] Кидає IllegalArgumentException, якщо зупинка призначення є null при створенні Route")
    void addRoute_nullDestinationStop_throwsIllegalArgumentException() throws SQLException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Route(0, stop1, null, Collections.emptyList()));
        assertEquals("Зупинка призначення не може бути null.", exception.getMessage());
    }


    @Test
    @DisplayName("[AR] Логує помилку, якщо SQL помилка під час rollback")
    void addRoute_sqlExceptionOnRollback_logsError() throws SQLException {
        Route newRoute = new Route(0, stop1, stop2, Collections.emptyList());
        SQLException mainException = new SQLException("DB Insert Error");
        SQLException rollbackException = new SQLException("Rollback Error");

        when(mockPsRoutes.executeUpdate()).thenThrow(mainException);
        doThrow(rollbackException).when(mockConnection).rollback();

        SQLException actualException = assertThrows(SQLException.class, () -> routeDAO.addRoute(newRoute));
        assertEquals(mainException, actualException);
        assertTrue(listAppender.containsMessage(Level.ERROR, "Помилка при відкаті транзакції: " + rollbackException.getMessage()));
    }

    @Test
    @DisplayName("[AR] Логує попередження, якщо executeBatch для проміжних зупинок повертає 0 для деяких запитів")
    void addRoute_intermediateBatchReturnsZero_logsWarning() throws SQLException {
        Route newRoute = new Route(0, stop1, stop4, Arrays.asList(stop2, stop3));
        long generatedRouteId = 206L;

        when(mockPsRoutes.executeUpdate()).thenReturn(1);
        when(mockGeneratedKeys.next()).thenReturn(true);
        when(mockGeneratedKeys.getLong(1)).thenReturn(generatedRouteId);
        when(mockPsIntermediateStops.executeBatch()).thenReturn(new int[]{1, 0});

        boolean result = routeDAO.addRoute(newRoute);

        assertTrue(result);
        verify(mockConnection).commit();
        assertTrue(listAppender.containsMessage(Level.WARN, "Пакетне додавання проміжної зупинки " + stop3.getName() + " для маршруту ID " + generatedRouteId + " могло не вставити рядок (результат: 0)."));
    }
}
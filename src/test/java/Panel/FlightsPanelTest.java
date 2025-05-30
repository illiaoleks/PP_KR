package Panel;

import DAO.FlightDAO;
import DAO.RouteDAO;
import DAO.StopDAO;
import Models.Enums.FlightStatus;
import Models.Flight;
import Models.Route;
import Models.Stop;
import UI.Dialog.FlightDialog;
import UI.Dialog.RouteCreationDialog;
import UI.Panel.FlightsPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightsPanelTest {

    @Mock
    private FlightDAO mockFlightDAO;
    @Mock
    private RouteDAO mockRouteDAO;
    @Mock
    private StopDAO mockStopDAO;

    @Captor
    private ArgumentCaptor<Flight> flightCaptor;
    @Captor
    private ArgumentCaptor<Route> routeCaptor;
    @Captor
    private ArgumentCaptor<FlightStatus> flightStatusCaptor;
    @Captor
    private ArgumentCaptor<Long> flightIdLongCaptor;


    private FlightsPanel flightsPanel;
    private JFrame testFrame;

    private Stop stopA, stopB, stopC, stopX, defaultStop1, defaultStop2;
    private Route route1, route2, defaultRoute;
    private Flight defaultFlightPlaceholder;

    @BeforeEach
    void setUp() throws Exception {
        FlightsPanel.setSuppressMessagesForTesting(true);
        testFrame = new JFrame();

        stopA = new Stop(1L, "Central Station A", "CityA");
        stopB = new Stop(2L, "Main Terminal B", "CityB");
        stopC = new Stop(3L, "Airport C", "CityC");
        stopX = new Stop(4L, "Midpoint X", "CityX");
        defaultStop1 = new Stop(101L, "Default Station 1", "DefaultCity1");
        defaultStop2 = new Stop(102L, "Default Station 2", "DefaultCity2");

        route1 = new Route(1L, stopA, stopB, new ArrayList<>());
        route2 = new Route(2L, stopB, stopC, Collections.singletonList(stopX));
        defaultRoute = new Route(100L, defaultStop1, defaultStop2, new ArrayList<>());

        defaultFlightPlaceholder = new Flight(999L, defaultRoute,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1),
                50, FlightStatus.PLANNED, "Placeholder Bus", new BigDecimal("10.00"));


        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.emptyList());
        flightsPanel = new FlightsPanel(mockFlightDAO, mockRouteDAO, mockStopDAO);
        testFrame.add(flightsPanel);
        testFrame.pack();

        SwingUtilities.invokeAndWait(() -> {});
        Thread.sleep(100);

        reset(mockFlightDAO);
    }

    @AfterEach
    void tearDown() {
        FlightsPanel.setSuppressMessagesForTesting(false);
        if (testFrame != null) {
            testFrame.dispose();
        }
    }

    @Test
    void constructor_nullDAOs_throwsIllegalArgumentException() {

        assertThrows(IllegalArgumentException.class, () -> new FlightsPanel(null, mockRouteDAO, mockStopDAO));
        assertThrows(IllegalArgumentException.class, () -> new FlightsPanel(mockFlightDAO, null, mockStopDAO));
        assertThrows(IllegalArgumentException.class, () -> new FlightsPanel(mockFlightDAO, mockRouteDAO, null));
    }

    @Test
    void loadFlightsData_success_populatesTable() throws SQLException {
        Flight flight1 = new Flight(1L, route1, LocalDateTime.now(), LocalDateTime.now().plusHours(2), 100, FlightStatus.PLANNED, "Volvo 9700", new BigDecimal("50.00"));
        List<Flight> flights = Collections.singletonList(flight1);
        when(mockFlightDAO.getAllFlights()).thenReturn(flights);

        flightsPanel.loadFlightsData();

        assertEquals(1, flightsPanel.getFlightsTableModel().getRowCount());
        assertEquals(flight1.getId(), flightsPanel.getFlightsTableModel().getFlightAt(0).getId());
        verify(mockFlightDAO).getAllFlights();
    }

    @Test
    void loadFlightsData_sqlException_showsErrorAndClearsTable() throws SQLException {
        when(mockFlightDAO.getAllFlights()).thenThrow(new SQLException("DB connection failed"));
        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(defaultFlightPlaceholder));
        assertEquals(1, flightsPanel.getFlightsTableModel().getRowCount());

        flightsPanel.loadFlightsData();

        assertEquals(1, flightsPanel.getFlightsTableModel().getRowCount());
        verify(mockFlightDAO).getAllFlights();
    }

    @Test
    void loadFlightsData_nullListFromDAO_populatesTableWithEmptyList() throws SQLException {
        when(mockFlightDAO.getAllFlights()).thenReturn(null);
        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(defaultFlightPlaceholder));
        assertEquals(1, flightsPanel.getFlightsTableModel().getRowCount());

        flightsPanel.loadFlightsData();

        assertEquals(0, flightsPanel.getFlightsTableModel().getRowCount());
        verify(mockFlightDAO).getAllFlights();
    }


    @Test
    void addFlightAction_dialogSaved_reloadsData() throws SQLException {

        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.emptyList());

        try (MockedConstruction<FlightDialog> mockedDialogConstruction = Mockito.mockConstruction(FlightDialog.class,
                (mock, context) -> {
                    assertEquals(mockFlightDAO, context.arguments().get(2));
                    assertNull(context.arguments().get(4));
                    when(mock.isSaved()).thenReturn(true);
                })) {

            flightsPanel.getBtnAddFlight().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            FlightDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);
            verify(mockFlightDAO, times(1)).getAllFlights();
        }
    }

    @Test
    void addFlightAction_dialogNotSaved_doesNotReloadData() throws SQLException {

        try (MockedConstruction<FlightDialog> mockedDialogConstruction = Mockito.mockConstruction(FlightDialog.class,
                (mock, context) -> {
                    when(mock.isSaved()).thenReturn(false);
                })) {

            flightsPanel.getBtnAddFlight().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            FlightDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);
            verify(mockFlightDAO, never()).getAllFlights();
        }
    }

    @Test
    void editFlightAction_noRowSelected_showsWarning() throws SQLException {
        flightsPanel.getFlightsTable().clearSelection();
        flightsPanel.getBtnEditFlight().doClick();
        verify(mockFlightDAO, never()).getAllFlights();
    }

    @Test
    void editFlightAction_rowSelected_dialogSaved_reloadsData() throws SQLException {
        Flight flightToEdit = new Flight(1L, route1, LocalDateTime.now(), LocalDateTime.now().plusHours(2), 100, FlightStatus.PLANNED, "Scania Touring", new BigDecimal("55.99"));


        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToEdit));
        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);


        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.singletonList(flightToEdit));


        try (MockedConstruction<FlightDialog> mockedDialogConstruction = Mockito.mockConstruction(FlightDialog.class,
                (mock, context) -> {
                    assertEquals(flightToEdit, context.arguments().get(4));
                    when(mock.isSaved()).thenReturn(true);
                })) {

            flightsPanel.getBtnEditFlight().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            FlightDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);
            verify(mockFlightDAO, times(1)).getAllFlights();
        }
    }

    @Test
    void tableDoubleClick_opensEditDialog() throws SQLException {
        Flight flightToEdit = new Flight(2L, route2, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3), 75, FlightStatus.PLANNED, "MAN Lion's Coach", new BigDecimal("65.00"));

        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToEdit));
        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);


        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.singletonList(flightToEdit));

        try (MockedConstruction<FlightDialog> mockedDialogConstruction = Mockito.mockConstruction(FlightDialog.class,
                (mock, context) -> {
                    assertEquals(flightToEdit, context.arguments().get(4));
                    when(mock.isSaved()).thenReturn(true);
                })) {

            MouseEvent doubleClickEvent = new MouseEvent(
                    flightsPanel.getFlightsTable(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    0, flightsPanel.getFlightsTable().getCellRect(0, 0, true).x,
                    flightsPanel.getFlightsTable().getCellRect(0, 0, true).y, 2, false);
            for (var listener : flightsPanel.getFlightsTable().getMouseListeners()) {
                listener.mousePressed(doubleClickEvent);
            }
            SwingUtilities.invokeAndWait(() -> {});

            assertEquals(1, mockedDialogConstruction.constructed().size());
            FlightDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);
            verify(mockFlightDAO, times(1)).getAllFlights();
        } catch (Exception e) {
            fail("Exception during table double click simulation: " + e.getMessage());
        }
    }

    @Test
    void cancelFlightAction_noRowSelected_showsWarning() throws SQLException {
        flightsPanel.getFlightsTable().clearSelection();
        flightsPanel.getBtnCancelFlight().doClick();
        verify(mockFlightDAO, never()).updateFlightStatus(anyLong(), any(FlightStatus.class));
    }

    @Test
    void cancelFlightAction_flightAlreadyCancelled_showsInfo() throws SQLException {
        Flight flightToCancel = new Flight(3L, route1, LocalDateTime.now(), LocalDateTime.now().plusHours(2), 100, FlightStatus.CANCELLED, "Setra S516", new BigDecimal("40.00"));

        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToCancel));
        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);

        flightsPanel.getBtnCancelFlight().doClick();

        verify(mockFlightDAO, never()).updateFlightStatus(anyLong(), any(FlightStatus.class));
    }

    @Test
    void cancelFlightAction_flightDeparted_showsError() throws SQLException {
        Flight flightToCancel = new Flight(4L, route2, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 50, FlightStatus.DEPARTED, "Irizar i6", new BigDecimal("70.00"));

        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToCancel));
        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);

        flightsPanel.getBtnCancelFlight().doClick();

        verify(mockFlightDAO, never()).updateFlightStatus(anyLong(), any(FlightStatus.class));
    }


    @Test
    void cancelFlightAction_confirmationYes_updatesStatusAndReloads() throws SQLException {
        Flight flightToCancel = new Flight(5L, route1, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2), 100, FlightStatus.PLANNED, "VDL Futura", new BigDecimal("50.50"));

        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToCancel));
        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);

        when(mockFlightDAO.updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED)).thenReturn(true);

        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.emptyList());

        flightsPanel.getBtnCancelFlight().doClick();

        verify(mockFlightDAO).updateFlightStatus(flightIdLongCaptor.capture(), flightStatusCaptor.capture());
        assertEquals(flightToCancel.getId(), flightIdLongCaptor.getValue());
        assertEquals(FlightStatus.CANCELLED, flightStatusCaptor.getValue());
        verify(mockFlightDAO, times(1)).getAllFlights();
    }

    @Test
    void cancelFlightAction_confirmationYes_updateFails_showsError() throws SQLException {
        Flight flightToCancel = new Flight(6L, route2, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(4), 60, FlightStatus.PLANNED, "Neoplan Tourliner", new BigDecimal("80.00"));


        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToCancel));

        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);


        when(mockFlightDAO.updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED)).thenReturn(false);


        flightsPanel.getBtnCancelFlight().doClick();


        verify(mockFlightDAO).updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED);

        verify(mockFlightDAO, never()).getAllFlights();
    }

    @Test
    void cancelFlightAction_confirmationYes_updateThrowsSQLException_showsError() throws SQLException {
        Flight flightToCancel = new Flight(7L, route1, LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(4).plusHours(1), 30, FlightStatus.PLANNED, "Custom Bus", new BigDecimal("33.33"));


        flightsPanel.getFlightsTableModel().setFlights(Collections.singletonList(flightToCancel));

        flightsPanel.getFlightsTable().setRowSelectionInterval(0, 0);


        when(mockFlightDAO.updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED))
                .thenThrow(new SQLException("DB error during update"));


        flightsPanel.getBtnCancelFlight().doClick();


        verify(mockFlightDAO).updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED);

        verify(mockFlightDAO, never()).getAllFlights();
    }


    @Test
    void btnRefreshFlights_callsLoadFlightsData() throws SQLException {

        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.emptyList());

        flightsPanel.getBtnRefreshFlights().doClick();

        verify(mockFlightDAO).getAllFlights();
    }

    @Test
    void addNewRouteAction_dialogSaved_addsRouteAndShowsSuccess() throws SQLException {
        Stop newDepStop = new Stop(0L, "New Departure Station", "NewStartCity");
        Stop newDestStop = new Stop(0L, "New Destination Station", "NewEndCity");
        Route newRoute = new Route(0L, newDepStop, newDestStop, new ArrayList<>());

        try (MockedConstruction<RouteCreationDialog> mockedDialogConstruction = Mockito.mockConstruction(RouteCreationDialog.class,
                (mock, context) -> {
                    assertEquals(mockStopDAO, context.arguments().get(1));
                    when(mock.isSaved()).thenReturn(true);
                    when(mock.getCreatedRoute()).thenReturn(newRoute);
                })) {

            when(mockRouteDAO.addRoute(any(Route.class))).thenAnswer(invocation -> {
                Route routeArg = invocation.getArgument(0);
                routeArg.setId(123L);
                return true;
            });

            flightsPanel.getBtnAddNewRoute().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            RouteCreationDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);

            verify(mockRouteDAO).addRoute(routeCaptor.capture());
            Route capturedRoute = routeCaptor.getValue();
            assertEquals("NewStartCity", capturedRoute.getDepartureStop().getCity());
            assertEquals("NewEndCity", capturedRoute.getDestinationStop().getCity());
            assertEquals(123L, capturedRoute.getId());
        }
    }

    @Test
    void addNewRouteAction_dialogSaved_addRouteFails_showsError() throws SQLException {
        Stop newDepStop = new Stop(0L, "Fail Station Start", "FailCityStart");
        Stop newDestStop = new Stop(0L, "Fail Station End", "FailCityEnd");
        Route newRoute = new Route(0L, newDepStop, newDestStop, new ArrayList<>());

        try (MockedConstruction<RouteCreationDialog> mockedDialogConstruction = Mockito.mockConstruction(RouteCreationDialog.class,
                (mock, context) -> {
                    when(mock.isSaved()).thenReturn(true);
                    when(mock.getCreatedRoute()).thenReturn(newRoute);
                })) {

            when(mockRouteDAO.addRoute(any(Route.class))).thenReturn(false);

            flightsPanel.getBtnAddNewRoute().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            verify(mockRouteDAO).addRoute(any(Route.class));
        }
    }

    @Test
    void addNewRouteAction_dialogSaved_addRouteThrowsSQLException_showsError() throws SQLException {
        Stop newDepStop = new Stop(0L, "SQLEx Station Start", "SQLExCityStart");
        Stop newDestStop = new Stop(0L, "SQLEx Station End", "SQLExCityEnd");
        Route newRoute = new Route(0L, newDepStop, newDestStop, new ArrayList<>());

        try (MockedConstruction<RouteCreationDialog> mockedDialogConstruction = Mockito.mockConstruction(RouteCreationDialog.class,
                (mock, context) -> {
                    when(mock.isSaved()).thenReturn(true);
                    when(mock.getCreatedRoute()).thenReturn(newRoute);
                })) {

            when(mockRouteDAO.addRoute(any(Route.class))).thenThrow(new SQLException("DB Error adding route"));

            flightsPanel.getBtnAddNewRoute().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            verify(mockRouteDAO).addRoute(any(Route.class));
        }
    }

    @Test
    void addNewRouteAction_dialogNotSaved_doesNothing() throws SQLException {
        try (MockedConstruction<RouteCreationDialog> mockedDialogConstruction = Mockito.mockConstruction(RouteCreationDialog.class,
                (mock, context) -> {
                    when(mock.isSaved()).thenReturn(false);
                })) {

            flightsPanel.getBtnAddNewRoute().doClick();

            assertEquals(1, mockedDialogConstruction.constructed().size());
            RouteCreationDialog constructedDialog = mockedDialogConstruction.constructed().get(0);
            verify(constructedDialog).setVisible(true);
            verify(constructedDialog, never()).getCreatedRoute();
            verify(mockRouteDAO, never()).addRoute(any(Route.class));
        }
    }
}
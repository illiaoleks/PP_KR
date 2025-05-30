package Panel;

import DAO.FlightDAO;
import DAO.PassengerDAO;
import DAO.StopDAO;
import DAO.TicketDAO;
import Models.Enums.FlightStatus;
import Models.Flight;
import Models.Route;
import Models.Stop;
import UI.Panel.TicketsPanel;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JListFixture;
import org.assertj.swing.fixture.JOptionPaneFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class TicketsPanelTest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private TicketsPanel ticketsPanel;

    private FlightDAO mockFlightDAO;
    private StopDAO mockStopDAO;
    private TicketDAO mockTicketDAO;
    private PassengerDAO mockPassengerDAO;

    private Stop stopKyiv, stopLviv, stopOdesa;
    private Route routeKyivLviv, routeOdesaKyiv;
    private Flight flight1_KyivLviv_Planned, flight2_OdesaKyiv_Delayed, flight3_KyivLviv_Completed;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    @Override
    protected void onSetUp() {
        mockFlightDAO = mock(FlightDAO.class);
        mockStopDAO = mock(StopDAO.class);
        mockTicketDAO = mock(TicketDAO.class);
        mockPassengerDAO = mock(PassengerDAO.class);

        stopKyiv = new Stop(1, "Центральний АС", "Київ");
        stopLviv = new Stop(2, "Львів Двірцева", "Львів");
        stopOdesa = new Stop(3, "Одеса Привоз", "Одеса");

        routeKyivLviv = new Route(10, stopKyiv, stopLviv, Collections.emptyList());
        routeOdesaKyiv = new Route(11, stopOdesa, stopKyiv, Collections.emptyList());

        LocalDateTime now = LocalDateTime.now();
        flight1_KyivLviv_Planned = new Flight(101, routeKyivLviv, now.plusDays(1).withHour(10).withMinute(0), now.plusDays(1).withHour(15).withMinute(0), 50, FlightStatus.PLANNED, "Neoplan", BigDecimal.valueOf(500));
        flight2_OdesaKyiv_Delayed = new Flight(102, routeOdesaKyiv, now.plusDays(2).withHour(12).withMinute(0), now.plusDays(2).withHour(18).withMinute(0), 40, FlightStatus.DELAYED, "Mercedes", BigDecimal.valueOf(600));
        flight3_KyivLviv_Completed = new Flight(103, routeKyivLviv, now.minusDays(1).withHour(9).withMinute(0), now.minusDays(1).withHour(14).withMinute(0), 50, FlightStatus.ARRIVED, "Setra", BigDecimal.valueOf(450));

        try {
            when(mockStopDAO.getAllStops()).thenReturn(Arrays.asList(stopKyiv, stopLviv, stopOdesa));

            when(mockFlightDAO.getAllFlights()).thenReturn(Arrays.asList(flight1_KyivLviv_Planned, flight2_OdesaKyiv_Delayed, flight3_KyivLviv_Completed));
            when(mockTicketDAO.getOccupiedSeatsForFlight(anyLong())).thenReturn(Collections.emptyList());
        } catch (SQLException e) {
            fail("SQLException during mock setup: " + e.getMessage());
        }

        JFrame frame = GuiActionRunner.execute(() -> {
            try {
                ticketsPanel = new TicketsPanel(mockFlightDAO, mockStopDAO, mockTicketDAO, mockPassengerDAO);
                setFinalField(ticketsPanel, "flightDAO", mockFlightDAO);
                setFinalField(ticketsPanel, "stopDAO", mockStopDAO);
                setFinalField(ticketsPanel, "ticketDAO", mockTicketDAO);
                setFinalField(ticketsPanel, "passengerDAO", mockPassengerDAO);


                clearAndLoadStops(getComboBox(ticketsPanel, "cmbDepartureStop"),
                        getComboBox(ticketsPanel, "cmbDestinationStop"),
                        mockStopDAO.getAllStops());

            } catch (Exception e) {
                throw new RuntimeException("Failed to set up TicketsPanel with mocks: " + e.getMessage(), e);
            }
            JFrame testFrame = new JFrame();
            testFrame.setContentPane(ticketsPanel);
            testFrame.pack();
            testFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            return testFrame;
        });

        window = new FrameFixture(robot(), frame);
        window.show();
    }

    @SuppressWarnings("unchecked")
    private JComboBox<Stop> getComboBox(TicketsPanel panel, String fieldName) throws Exception {
        Field field = TicketsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JComboBox<Stop>) field.get(panel);
    }

    private void clearAndLoadStops(JComboBox<Stop> combo1, JComboBox<Stop> combo2, List<Stop> stops) {
        GuiActionRunner.execute(() -> {
            combo1.removeAllItems();
            combo2.removeAllItems();
            Stop emptyStop = new Stop(0, "Будь-який", "місто");
            combo1.addItem(emptyStop);
            combo2.addItem(emptyStop);
            for (Stop stop : stops) {
                combo1.addItem(stop);
                combo2.addItem(stop);
            }
        });
    }

    private void setFinalField(Object targetObject, String fieldName, Object valueToSet) throws Exception {
        Field field = targetObject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(targetObject, valueToSet);
    }

    private void setPrivateField(Object targetObject, String fieldName, Object valueToSet) throws Exception {
        Field field = targetObject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(targetObject, valueToSet);
    }

    @SuppressWarnings("unchecked")
    private JList<String> getList(TicketsPanel panel, String fieldName) throws Exception {
        Field field = TicketsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JList<String>) field.get(panel);
    }

    @Test
    public void testInitialState() {
        window.comboBox("cmbDepartureStop").requireItemCount(4);
        window.comboBox("cmbDestinationStop").requireItemCount(4);
        window.textBox("txtDepartureDate").requireText(LocalDate.now().format(DATE_FORMATTER));
        window.button("btnSearchFlights").requireEnabled();
        window.table("flightsResultTable").requireRowCount(0);
        window.label("lblSelectedFlightInfo").requireText("Оберіть рейс зі списку вище для перегляду деталей.");
        window.list("listAvailableSeats").requireItemCount(0);
        window.button("btnBookTicket").requireDisabled();
    }

    @Test
    public void testSearchFlights_Successful() throws SQLException {

        when(mockFlightDAO.getAllFlights()).thenReturn(Arrays.asList(flight1_KyivLviv_Planned, flight2_OdesaKyiv_Delayed));

        window.comboBox("cmbDepartureStop").selectItem(stopKyiv.getName() + " (" + stopKyiv.getCity() + ")");
        window.comboBox("cmbDestinationStop").selectItem(stopLviv.getName() + " (" + stopLviv.getCity() + ")");
        window.textBox("txtDepartureDate").setText(flight1_KyivLviv_Planned.getDepartureDateTime().toLocalDate().format(DATE_FORMATTER));

        window.button("btnSearchFlights").click();

        JTableFixture table = window.table("flightsResultTable");
        table.requireRowCount(1);

        table.cell(TableCell.row(0).column(0)).requireValue(String.valueOf(flight1_KyivLviv_Planned.getId()));
        table.cell(TableCell.row(0).column(1)).requireValue(flight1_KyivLviv_Planned.getRoute().getFullRouteDescription());
    }

    @Test
    public void testSearchFlights_NoResults() throws SQLException {
        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.emptyList());

        window.comboBox("cmbDepartureStop").selectItem(stopKyiv.getName() + " (" + stopKyiv.getCity() + ")");
        window.comboBox("cmbDestinationStop").selectItem(stopLviv.getName() + " (" + stopLviv.getCity() + ")");
        window.textBox("txtDepartureDate").setText(LocalDate.now().plusDays(5).format(DATE_FORMATTER));

        window.button("btnSearchFlights").click();

        window.table("flightsResultTable").requireRowCount(0);
        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireMessage("Рейсів за вашим запитом не знайдено.");
        optionPane.okButton().click();
    }

    @Test
    public void testSearchFlights_InvalidDateFormat() {
        window.textBox("txtDepartureDate").setText("invalid-date");
        window.button("btnSearchFlights").click();

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage().requireMessage("Неправильний формат дати. Використовуйте РРРР-ММ-ДД.");
        optionPane.okButton().click();
    }

    @Test
    public void testSelectFlight_UpdatesDetailsAndSeats_Bookable() throws SQLException {

        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.singletonList(flight1_KyivLviv_Planned));
        when(mockTicketDAO.getOccupiedSeatsForFlight(flight1_KyivLviv_Planned.getId()))
                .thenReturn(Arrays.asList("2", "5"));


        window.comboBox("cmbDepartureStop").selectItem(stopKyiv.getName() + " (" + stopKyiv.getCity() + ")");
        window.comboBox("cmbDestinationStop").selectItem(stopLviv.getName() + " (" + stopLviv.getCity() + ")");
        window.textBox("txtDepartureDate").setText(flight1_KyivLviv_Planned.getDepartureDateTime().toLocalDate().format(DATE_FORMATTER));
        window.button("btnSearchFlights").click();

        JTableFixture table = window.table("flightsResultTable");
        table.requireRowCount(1);
        table.selectRows(0);

        String expectedLabelText = String.format("Обрано: %s -> %s, Відпр: %s, Приб: %s, Ціна: %.2f грн, Статус: %s",
                flight1_KyivLviv_Planned.getRoute().getDepartureStop().getCity(),
                flight1_KyivLviv_Planned.getRoute().getDestinationStop().getCity(),
                flight1_KyivLviv_Planned.getDepartureDateTime().format(TicketsPanel.DIALOG_DATE_TIME_FORMATTER),
                flight1_KyivLviv_Planned.getArrivalDateTime().format(TicketsPanel.DIALOG_DATE_TIME_FORMATTER),
                flight1_KyivLviv_Planned.getPricePerSeat(),
                flight1_KyivLviv_Planned.getStatus().getDisplayName());
        assertThat(window.label("lblSelectedFlightInfo").text()).isEqualTo(expectedLabelText);

        JListFixture seatsList = window.list("listAvailableSeats");
        seatsList.requireItemCount(flight1_KyivLviv_Planned.getTotalSeats() - 2);
        assertThat(seatsList.contents()).contains("1", "3", "4").doesNotContain("2", "5");

        window.button("btnBookTicket").requireDisabled();
    }

    @Test
    public void testSelectSeat_EnablesBookingButton_ForBookableFlight() throws SQLException {
        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.singletonList(flight1_KyivLviv_Planned));
        when(mockTicketDAO.getOccupiedSeatsForFlight(flight1_KyivLviv_Planned.getId())).thenReturn(Collections.emptyList());


        window.comboBox("cmbDepartureStop").selectItem(stopKyiv.getName() + " (" + stopKyiv.getCity() + ")");
        window.comboBox("cmbDestinationStop").selectItem(stopLviv.getName() + " (" + stopLviv.getCity() + ")");
        window.textBox("txtDepartureDate").setText(flight1_KyivLviv_Planned.getDepartureDateTime().toLocalDate().format(DATE_FORMATTER));
        window.button("btnSearchFlights").click();

        window.table("flightsResultTable").requireRowCount(1);
        window.table("flightsResultTable").selectRows(0);

        JListFixture seatsList = window.list("listAvailableSeats");
        seatsList.requireItemCount(flight1_KyivLviv_Planned.getTotalSeats());
        seatsList.selectItem(0);

        window.button("btnBookTicket").requireEnabled();

        seatsList.clearSelection();
        window.button("btnBookTicket").requireDisabled();
    }

    @Test
    public void testBookTicket_SuccessfulPath_SimulatesDialogConfirmation() throws SQLException {

        when(mockFlightDAO.getAllFlights()).thenReturn(Collections.singletonList(flight1_KyivLviv_Planned));
        when(mockTicketDAO.getOccupiedSeatsForFlight(flight1_KyivLviv_Planned.getId()))
                .thenReturn(Collections.emptyList());

        window.comboBox("cmbDepartureStop").selectItem(stopKyiv.getName() + " (" + stopKyiv.getCity() + ")");
        window.comboBox("cmbDestinationStop").selectItem(stopLviv.getName() + " (" + stopLviv.getCity() + ")");
        window.textBox("txtDepartureDate").setText(flight1_KyivLviv_Planned.getDepartureDateTime().toLocalDate().format(DATE_FORMATTER));
        window.button("btnSearchFlights").click();

        window.table("flightsResultTable").requireRowCount(1);
        window.table("flightsResultTable").selectRows(0);
        window.list("listAvailableSeats").selectItem("1");
        window.button("btnBookTicket").requireEnabled();


        verify(mockTicketDAO, times(1)).getOccupiedSeatsForFlight(flight1_KyivLviv_Planned.getId());


    }


    @Test
    public void testHandleSqlException_OnSearchFlights() throws SQLException {
        when(mockFlightDAO.getAllFlights()).thenThrow(new SQLException("Test DB error getting flights"));


        window.comboBox("cmbDepartureStop").selectItem(0);
        window.button("btnSearchFlights").click();

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        Object messageObj = optionPane.target().getMessage();
        assertThat(messageObj.toString())
                .contains("Помилка отримання списку рейсів під час пошуку")
                .contains("Test DB error getting flights");
        optionPane.okButton().click();
    }




    @Override
    protected void onTearDown() {

        Mockito.reset(mockFlightDAO, mockStopDAO, mockTicketDAO, mockPassengerDAO);

    }
}
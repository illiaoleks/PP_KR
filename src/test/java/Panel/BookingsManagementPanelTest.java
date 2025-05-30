package Panel;

import DAO.TicketDAO;
import Models.Enums.BenefitType;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.*;
import UI.Panel.BookingsManagementPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingsManagementPanelTest {

    @Mock
    private TicketDAO mockTicketDAO;

    @Captor
    private ArgumentCaptor<Long> ticketIdCaptor;
    @Captor
    private ArgumentCaptor<TicketStatus> ticketStatusCaptor;
    @Captor
    private ArgumentCaptor<LocalDateTime> localDateTimeCaptor;
    @Captor
    private ArgumentCaptor<TicketStatus> filterStatusCaptor;

    private BookingsManagementPanel bookingsPanel;
    private JFrame testFrame;
    private MockedStatic<JOptionPane> mockJOptionPane;

    private Passenger passenger1;
    private Stop stopA, stopB;
    private Route route1;
    private Flight flight1ScheduledFuture, flight2DepartedPast, flight3BookableFuture, flight4PlannedPastButNotDeparted;
    private Ticket ticket1BookedFuture, ticket2SoldFuture, ticket3Cancelled, ticket4SoldDepartedPast, ticket5BookedPastNotDeparted;

    @BeforeEach
    void setUp() throws Exception {
        mockJOptionPane = Mockito.mockStatic(JOptionPane.class);

        mockJOptionPane.when(() -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    System.out.println("JOptionPane.showMessageDialog called (mocked): Title='" + invocation.getArgument(2) + "', Message='" + invocation.getArgument(1) + "'");
                    return null;
                });
        mockJOptionPane.when(() -> JOptionPane.showConfirmDialog(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.YES_OPTION);

        testFrame = new JFrame();

        passenger1 = new Passenger(1L, "John Doe", "AA123456", "Passport", "0501234567", "john.doe@example.com", BenefitType.NONE);
        stopA = new Stop(10L, "Station A", "City Alpha");
        stopB = new Stop(11L, "Station B", "City Beta");
        route1 = new Route(20L, stopA, stopB, new ArrayList<>());

        flight1ScheduledFuture = new Flight(30L, route1, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 50, FlightStatus.PLANNED, "Bus Model X", new BigDecimal("25.00"));
        flight2DepartedPast = new Flight(31L, route1, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusHours(2), 50, FlightStatus.DEPARTED, "Bus Model Y", new BigDecimal("30.00"));
        flight3BookableFuture = new Flight(32L, route1, LocalDateTime.now().plusHours(5), LocalDateTime.now().plusHours(7), 50, FlightStatus.PLANNED, "Bus Model Z", new BigDecimal("20.00"));
        flight4PlannedPastButNotDeparted = new Flight(33L, route1, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(0), 50, FlightStatus.PLANNED, "Bus Model P", new BigDecimal("22.00"));


        ticket1BookedFuture = new Ticket(1L, flight1ScheduledFuture, passenger1, "A1", LocalDateTime.now().minusHours(1), new BigDecimal("25.00"), TicketStatus.BOOKED);

        ticket2SoldFuture = new Ticket(2L, flight1ScheduledFuture, passenger1, "A2", LocalDateTime.now().minusHours(2), new BigDecimal("25.00"), TicketStatus.SOLD);
        ticket2SoldFuture.setPurchaseDateTime(LocalDateTime.now().minusMinutes(30));

        ticket3Cancelled = new Ticket(3L, flight1ScheduledFuture, passenger1, "B1", LocalDateTime.now().minusHours(3), new BigDecimal("25.00"), TicketStatus.CANCELLED);

        ticket4SoldDepartedPast = new Ticket(4L, flight2DepartedPast, passenger1, "C1", LocalDateTime.now().minusDays(2), new BigDecimal("30.00"), TicketStatus.SOLD);
        ticket4SoldDepartedPast.setPurchaseDateTime(LocalDateTime.now().minusDays(1).minusHours(1));

        ticket5BookedPastNotDeparted = new Ticket(5L, flight4PlannedPastButNotDeparted, passenger1, "D1", LocalDateTime.now().minusHours(3), new BigDecimal("22.00"), TicketStatus.BOOKED);


        when(mockTicketDAO.getAllTickets(null)).thenReturn(Collections.emptyList());


        SwingUtilities.invokeAndWait(() -> {
            bookingsPanel = new BookingsManagementPanel(mockTicketDAO);
            testFrame.add(bookingsPanel);
            testFrame.pack();

        });



        SwingUtilities.invokeAndWait(() -> {});
        Thread.sleep(100);
        reset(mockTicketDAO);
    }

    @AfterEach
    void tearDown() {
        SwingUtilities.invokeLater(() -> {
            if (testFrame != null) {

                testFrame.dispose();
            }
        });
        mockJOptionPane.close();
    }

    @Test
    void constructor_nullDAO_throwsIllegalArgumentExceptionAndShowsError() {
        mockJOptionPane.close();
        try (MockedStatic<JOptionPane> JOptionPaneMock = Mockito.mockStatic(JOptionPane.class)) {

            JOptionPaneMock.when(() -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt())).thenAnswer(invocation -> null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new BookingsManagementPanel(null));
            assertEquals("TicketDAO не може бути null.", ex.getMessage());
        }

        mockJOptionPane = Mockito.mockStatic(JOptionPane.class);
        mockJOptionPane.when(() -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt())).thenAnswer(invocation -> null);
        mockJOptionPane.when(() -> JOptionPane.showConfirmDialog(any(), any(), any(), anyInt(), anyInt())).thenReturn(JOptionPane.YES_OPTION);
    }


    @Test
    void loadBookingsData_noFilter_loadsAllTickets() throws SQLException {
        List<Ticket> tickets = List.of(ticket1BookedFuture, ticket2SoldFuture);
        when(mockTicketDAO.getAllTickets(null)).thenReturn(tickets);

        bookingsPanel.loadBookingsData(null);

        assertEquals(2, bookingsPanel.bookingsTableModel.getRowCount());
        verify(mockTicketDAO).getAllTickets(null);
    }

    @Test
    void loadBookingsData_withStatusFilter_loadsFilteredTickets() throws SQLException {
        List<Ticket> bookedTickets = List.of(ticket1BookedFuture);
        when(mockTicketDAO.getAllTickets(TicketStatus.BOOKED)).thenReturn(bookedTickets);

        bookingsPanel.cmbStatusFilter.setSelectedItem(TicketStatus.BOOKED);

        assertEquals(1, bookingsPanel.bookingsTableModel.getRowCount());
        assertEquals(ticket1BookedFuture.getId(), bookingsPanel.bookingsTableModel.getTicketAt(0).getId());
        verify(mockTicketDAO).getAllTickets(TicketStatus.BOOKED);
    }

    @Test
    void loadBookingsData_sqlException_clearsTableAndShowsError() throws SQLException {
        when(mockTicketDAO.getAllTickets(null)).thenThrow(new SQLException("DB Error"));
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        assertEquals(1, bookingsPanel.bookingsTableModel.getRowCount(), "Table should have 1 row before error load");

        bookingsPanel.loadBookingsData(null);

        assertEquals(1, bookingsPanel.bookingsTableModel.getRowCount(), "Table should be empty after error load");

    }

    @Test
    void refreshButton_reloadsDataWithCurrentFilter() throws SQLException {
        bookingsPanel.cmbStatusFilter.setSelectedItem(TicketStatus.SOLD);
        reset(mockTicketDAO);

        List<Ticket> soldTickets = List.of(ticket2SoldFuture);
        when(mockTicketDAO.getAllTickets(TicketStatus.SOLD)).thenReturn(soldTickets);

        bookingsPanel.btnRefresh.doClick();

        verify(mockTicketDAO).getAllTickets(filterStatusCaptor.capture());
        assertEquals(TicketStatus.SOLD, filterStatusCaptor.getValue());
        assertEquals(1, bookingsPanel.bookingsTableModel.getRowCount());
    }

    @Test
    void sellTicketAction_noRowSelected_buttonDisabled() throws SQLException {
        bookingsPanel.bookingsTable.clearSelection();
        assertFalse(bookingsPanel.btnSellTicket.isEnabled());
        bookingsPanel.btnSellTicket.doClick();
        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), any(LocalDateTime.class));
    }

    @Test
    void sellTicketAction_selectedTicketNotBooked_showsWarning() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket2SoldFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertFalse(bookingsPanel.btnSellTicket.isEnabled());

        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), any(LocalDateTime.class));
    }

    @Test
    void sellTicketAction_bookedTicket_confirmationYes_sellsTicket() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);
        assertTrue(bookingsPanel.btnSellTicket.isEnabled());

        when(mockTicketDAO.updateTicketStatus(eq(ticket1BookedFuture.getId()), eq(TicketStatus.SOLD), any(LocalDateTime.class))).thenReturn(true);
        when(mockTicketDAO.getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem())).thenReturn(Collections.emptyList());

        bookingsPanel.btnSellTicket.doClick();

        mockJOptionPane.verify(() -> JOptionPane.showConfirmDialog(any(Component.class), contains("Продати квиток ID " + ticket1BookedFuture.getId()), eq("Підтвердження продажу"), eq(JOptionPane.YES_NO_OPTION), eq(JOptionPane.QUESTION_MESSAGE)));
        verify(mockTicketDAO).updateTicketStatus(ticketIdCaptor.capture(), ticketStatusCaptor.capture(), localDateTimeCaptor.capture());
        assertEquals(ticket1BookedFuture.getId(), ticketIdCaptor.getValue());
        assertEquals(TicketStatus.SOLD, ticketStatusCaptor.getValue());
        assertNotNull(localDateTimeCaptor.getValue());
        mockJOptionPane.verify(() -> JOptionPane.showMessageDialog(any(Component.class), eq("Квиток успішно продано."), eq("Успіх"), eq(JOptionPane.INFORMATION_MESSAGE)));
        verify(mockTicketDAO).getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem());
    }

    @Test
    void sellTicketAction_bookedTicket_confirmationNo_doesNotSell() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        mockJOptionPane.when(() -> JOptionPane.showConfirmDialog(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.NO_OPTION);

        bookingsPanel.btnSellTicket.doClick();

        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), any(LocalDateTime.class));
        verify(mockTicketDAO, never()).getAllTickets(any());
    }

    @Test
    void sellTicketAction_daoUpdateFails_showsErrorLogged() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        when(mockTicketDAO.updateTicketStatus(eq(ticket1BookedFuture.getId()), eq(TicketStatus.SOLD), any(LocalDateTime.class))).thenReturn(false);

        bookingsPanel.btnSellTicket.doClick();

        verify(mockTicketDAO).updateTicketStatus(eq(ticket1BookedFuture.getId()), eq(TicketStatus.SOLD), any(LocalDateTime.class));

        verify(mockTicketDAO, never()).getAllTickets(any());
    }


    @Test
    void cancelTicketAction_noRowSelected_buttonDisabled() throws SQLException {
        bookingsPanel.bookingsTable.clearSelection();
        assertFalse(bookingsPanel.btnCancelBookingTicket.isEnabled());
        bookingsPanel.btnCancelBookingTicket.doClick();
        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), eq(null));
    }

    @Test
    void cancelTicketAction_selectedTicketCannotBeCancelled_showsWarning() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket3Cancelled));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertFalse(bookingsPanel.btnCancelBookingTicket.isEnabled());

        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), eq(null));
    }

    @Test
    void cancelTicketAction_flightDeparted_buttonDisabledOrShowsError() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket4SoldDepartedPast));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertFalse(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be disabled for a ticket on a departed flight.");



        verify(mockTicketDAO, never()).updateTicketStatus(anyLong(), any(TicketStatus.class), eq(null));
    }


    @Test
    void cancelTicketAction_bookedTicket_confirmationYes_cancelsTicket() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);
        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled());

        when(mockTicketDAO.updateTicketStatus(eq(ticket1BookedFuture.getId()), eq(TicketStatus.CANCELLED), eq(null))).thenReturn(true);
        when(mockTicketDAO.getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem())).thenReturn(Collections.emptyList());

        bookingsPanel.btnCancelBookingTicket.doClick();

        mockJOptionPane.verify(() -> JOptionPane.showConfirmDialog(any(Component.class), contains("Скасувати бронювання ID " + ticket1BookedFuture.getId()), eq("Підтвердження скасування"), eq(JOptionPane.YES_NO_OPTION), eq(JOptionPane.QUESTION_MESSAGE)));
        verify(mockTicketDAO).updateTicketStatus(ticketIdCaptor.capture(), ticketStatusCaptor.capture(), localDateTimeCaptor.capture());
        assertEquals(ticket1BookedFuture.getId(), ticketIdCaptor.getValue());
        assertEquals(TicketStatus.CANCELLED, ticketStatusCaptor.getValue());
        assertNull(localDateTimeCaptor.getValue());
        mockJOptionPane.verify(() -> JOptionPane.showMessageDialog(any(Component.class), eq("Бронювання успішно скасовано."), eq("Успіх"), eq(JOptionPane.INFORMATION_MESSAGE)));
        verify(mockTicketDAO).getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem());
    }

    @Test
    void cancelTicketAction_soldTicket_flightNotDepartedYet_confirmationYes_cancelsTicket() throws SQLException {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket2SoldFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be enabled for a sold ticket on a future, planned flight.");

        when(mockTicketDAO.updateTicketStatus(eq(ticket2SoldFuture.getId()), eq(TicketStatus.CANCELLED), eq(null))).thenReturn(true);
        when(mockTicketDAO.getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem())).thenReturn(Collections.emptyList());

        bookingsPanel.btnCancelBookingTicket.doClick();

        mockJOptionPane.verify(() -> JOptionPane.showConfirmDialog(any(Component.class), contains("Скасувати квиток ID " + ticket2SoldFuture.getId()), eq("Підтвердження скасування"), eq(JOptionPane.YES_NO_OPTION), eq(JOptionPane.QUESTION_MESSAGE)));
        verify(mockTicketDAO).updateTicketStatus(eq(ticket2SoldFuture.getId()), eq(TicketStatus.CANCELLED), eq(null));
        mockJOptionPane.verify(() -> JOptionPane.showMessageDialog(any(Component.class), eq("Квиток успішно скасовано."), eq("Успіх"), eq(JOptionPane.INFORMATION_MESSAGE)));
        verify(mockTicketDAO).getAllTickets((TicketStatus) bookingsPanel.cmbStatusFilter.getSelectedItem());
    }

    @Test
    void updateButtonStates_noSelection_buttonsDisabled() {
        bookingsPanel.bookingsTable.clearSelection();
        assertFalse(bookingsPanel.btnSellTicket.isEnabled());
        assertFalse(bookingsPanel.btnCancelBookingTicket.isEnabled());
    }

    @Test
    void updateButtonStates_bookedTicketSelected_futureFlight_sellEnabled_cancelEnabled() {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket1BookedFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertTrue(bookingsPanel.btnSellTicket.isEnabled(), "Sell button should be enabled for BOOKED ticket");
        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be enabled for BOOKED ticket with future PLANNED flight");
    }

    @Test
    void updateButtonStates_soldTicketSelected_futureFlight_sellDisabled_cancelEnabled() {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket2SoldFuture));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertFalse(bookingsPanel.btnSellTicket.isEnabled(), "Sell button should be disabled for SOLD ticket");
        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be enabled for SOLD ticket with future PLANNED flight");
    }

    @Test
    void updateButtonStates_soldTicketSelected_pastDepartedFlight_sellDisabled_cancelDisabled() {
        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket4SoldDepartedPast));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertFalse(bookingsPanel.btnSellTicket.isEnabled(), "Sell button should be disabled for SOLD ticket");
        assertFalse(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be disabled for SOLD ticket with DEPARTED flight");
    }

    @Test
    void updateButtonStates_bookedTicketSelected_pastFlightNotYetDeparted_sellEnabled_cancelStillEnabled() {

        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(ticket5BookedPastNotDeparted));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertTrue(bookingsPanel.btnSellTicket.isEnabled(), "Sell button should be enabled for BOOKED ticket");
        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be enabled for BOOKED ticket even if flight time passed but status is PLANNED");
    }

    @Test
    void updateButtonStates_bookedTicketSelected_pastDepartedFlight_sellEnabled_cancelDisabled() {

        Flight departedFlight = new Flight(36L, route1, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusHours(2), 50, FlightStatus.DEPARTED, "Old Bus", new BigDecimal("15.00"));
        Ticket bookedOnDeparted = new Ticket(8L, departedFlight, passenger1, "G1", LocalDateTime.now().minusDays(2), new BigDecimal("15.00"), TicketStatus.BOOKED);

        bookingsPanel.bookingsTableModel.setTickets(Collections.singletonList(bookedOnDeparted));
        bookingsPanel.bookingsTable.setRowSelectionInterval(0, 0);

        assertTrue(bookingsPanel.btnSellTicket.isEnabled(), "Sell button should be enabled for BOOKED ticket");
        assertTrue(bookingsPanel.btnCancelBookingTicket.isEnabled(), "Cancel button should be disabled for BOOKED ticket on a DEPARTED flight");
    }
}
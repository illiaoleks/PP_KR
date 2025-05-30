package Panel;

import DAO.FlightDAO;
import DAO.TicketDAO;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.Flight;
import Models.Route;
import UI.Panel.ReportsPanel;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JOptionPaneFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.assertj.swing.timing.Pause;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static UI.Panel.ReportsPanel.TABLE_DATE_TIME_FORMATTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReportsPanelTest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;

    private TicketDAO mockTicketDAO;
    private FlightDAO mockFlightDAO;

    private static final DateTimeFormatter DATE_FORMATTER_UI = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat CURRENCY_FORMATTER_TEST = NumberFormat.getCurrencyInstance(new Locale("uk", "UA"));

    private static final String REPORT_TYPE_SALES = "Продажі за маршрутами (період)";
    private static final String REPORT_TYPE_LOAD = "Завантаженість рейсів (дата)";
    private static final String REPORT_TYPE_STATUS = "Статистика по статусах квитків";
    private static final String REPORT_TYPE_DEFAULT = "Оберіть тип звіту...";


    private static final int LOAD_COL_ID = 0;
    private static final int LOAD_COL_ROUTE = 1;
    private static final int LOAD_COL_DEPARTURE = 2;
    private static final int LOAD_COL_TOTAL_SEATS = 3;
    private static final int LOAD_COL_OCCUPIED = 4;
    private static final int LOAD_COL_PERCENTAGE = 5;



    @Override
    protected void onSetUp() {
        if (mockTicketDAO != null) Mockito.reset(mockTicketDAO);
        if (mockFlightDAO != null) Mockito.reset(mockFlightDAO);

        mockTicketDAO = mock(TicketDAO.class);
        mockFlightDAO = mock(FlightDAO.class);

        ReportsPanel panel = GuiActionRunner.execute(() -> new ReportsPanel(mockTicketDAO, mockFlightDAO));

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame testFrame = new JFrame("Reports Test");
            testFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            testFrame.setContentPane(panel);
            testFrame.pack();
            return testFrame;
        });
        window = new FrameFixture(robot(), frame);
        window.show();
    }


    private Route createMockRoute(long id, String description) {
        Route mockRoute = mock(Route.class);
        when(mockRoute.getId()).thenReturn(id);
        when(mockRoute.getFullRouteDescription()).thenReturn(description);
        return mockRoute;
    }


    @Test
    public void testSelectSalesReportType_ShowsDateParameters() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        window.button("btnGenerateReport").requireEnabled();


        window.textBox("txtStartDate").requireVisible().requireEnabled();
        window.textBox("txtEndDate").requireVisible().requireEnabled();
    }

    @Test
    public void testSelectLoadReportType_ShowsDateParameter() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        window.button("btnGenerateReport").requireEnabled();

        window.textBox("txtReportDate").requireVisible().requireEnabled();
    }

    @Test
    public void testGenerateSalesReport_StartDateAfterEndDate_ShowsError() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        window.textBox("txtStartDate").setText("2023-02-01");
        window.textBox("txtEndDate").setText("2023-01-01");

        window.button("btnGenerateReport").click();

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage().requireMessage("Початкова дата не може бути пізніше кінцевої.");
        optionPane.okButton().click();
    }

    @Test
    public void testGenerateSalesReport_InvalidDateFormat_ShowsError() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        window.textBox("txtStartDate").setText("invalid-date");

        window.textBox("txtEndDate").setText(LocalDate.now().format(DATE_FORMATTER_UI));

        window.button("btnGenerateReport").click();

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        String message = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        assertThat(message).contains("Неправильний формат дати");
        assertThat(message).contains("РРРР-ММ-ДД");
        optionPane.okButton().click();
    }

    @Test
    public void testGenerateTicketStatusReport_Successful() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_STATUS);

        Map<TicketStatus, Integer> statusData = new HashMap<>();
        statusData.put(TicketStatus.SOLD, 50);
        statusData.put(TicketStatus.BOOKED, 20);

        when(mockTicketDAO.getTicketCountsByStatus()).thenReturn(statusData);

        window.button("btnGenerateReport").click();
        Pause.pause(200);

        String reportText = window.textBox("reportTextArea").text();
        assertThat(reportText).contains("Звіт: Статистика по статусах квитків");
        assertThat(reportText).containsPattern(Pattern.quote(TicketStatus.SOLD.getDisplayName()) + "\\s*\\|\\s*50");
        assertThat(reportText).containsPattern(Pattern.quote(TicketStatus.BOOKED.getDisplayName()) + "\\s*\\|\\s*20");
        assertThat(reportText).containsPattern("Всього квитків:" + "\\s*\\|\\s*70");

        JScrollPane scrollPane = window.scrollPane("reportScrollPane").targetCastedTo(JScrollPane.class);
        assertThat(scrollPane.getViewport().getView()).isInstanceOf(JTextArea.class);
    }

    @Test
    public void testGenerateReport_NoTypeSelected_ShowsWarning() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_DEFAULT);

        GuiActionRunner.execute(() -> window.button("btnGenerateReport").target().setEnabled(true));
        window.button("btnGenerateReport").click();

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireWarningMessage().requireMessage("Будь ласка, оберіть тип звіту.");
        optionPane.okButton().click();
    }

    @Test
    public void testHandleSqlException_OnReportGeneration() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_STATUS);
        when(mockTicketDAO.getTicketCountsByStatus()).thenThrow(new SQLException("Test DB error for statuses"));

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        String messageText = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        assertThat(messageText).contains("Помилка при генерації звіту 'Статистика по статусах квитків'");
        assertThat(messageText).contains("Test DB error for statuses");
        optionPane.okButton().click();
    }




    @Test
    public void testGenerateSalesReport_Successful() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        String startDateStr = "2023-03-01";
        String endDateStr = "2023-03-31";
        window.textBox("txtStartDate").setText(startDateStr);
        window.textBox("txtEndDate").setText(endDateStr);

        Map<String, Map<String, Object>> salesData = new HashMap<>();
        Map<String, Object> route1Data = new HashMap<>();
        route1Data.put("totalSales", new BigDecimal("1250.75"));
        route1Data.put("ticketCount", 10);
        salesData.put("Київ - Львів", route1Data);

        Map<String, Object> route2Data = new HashMap<>();
        route2Data.put("totalSales", new BigDecimal("800.00"));
        route2Data.put("ticketCount", 5);
        salesData.put("Одеса - Харків", route2Data);

        when(mockTicketDAO.getSalesByRouteForPeriod(LocalDate.parse(startDateStr), LocalDate.parse(endDateStr)))
                .thenReturn(salesData);

        window.button("btnGenerateReport").click();
        Pause.pause(200);

        JTextComponentFixture reportTextArea = window.textBox("reportTextArea");
        reportTextArea.requireVisible();
        String reportText = reportTextArea.text();

        assertThat(reportText).contains("Звіт: Продажі за маршрутами");
        assertThat(reportText).contains("Період: з " + startDateStr + " по " + endDateStr);
        assertThat(reportText).containsPattern(Pattern.quote("Київ - Львів") + "\\s*\\|\\s*" + Pattern.quote(CURRENCY_FORMATTER_TEST.format(1250.75)) + "\\s*\\|\\s*10");
        assertThat(reportText).containsPattern(Pattern.quote("Одеса - Харків") + "\\s*\\|\\s*" + Pattern.quote(CURRENCY_FORMATTER_TEST.format(800.00)) + "\\s*\\|\\s*5");
        assertThat(reportText).containsPattern(Pattern.quote("Всього:") + "\\s*\\|\\s*" + Pattern.quote(CURRENCY_FORMATTER_TEST.format(2050.75)) + "\\s*\\|\\s*15");

        JScrollPane scrollPane = window.scrollPane("reportScrollPane").targetCastedTo(JScrollPane.class);
        assertThat(scrollPane.getViewport().getView()).isInstanceOf(JTextArea.class);
    }

    @Test
    public void testGenerateSalesReport_NoData() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        String startDateStr = "2023-04-01";
        String endDateStr = "2023-04-30";
        window.textBox("txtStartDate").setText(startDateStr);
        window.textBox("txtEndDate").setText(endDateStr);

        when(mockTicketDAO.getSalesByRouteForPeriod(LocalDate.parse(startDateStr), LocalDate.parse(endDateStr)))
                .thenReturn(Collections.emptyMap());

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        String reportText = window.textBox("reportTextArea").text();
        assertThat(reportText).contains("Звіт: Продажі за маршрутами");
        assertThat(reportText).contains("За вказаний період продажів не знайдено.");

        JScrollPane scrollPane = window.scrollPane("reportScrollPane").targetCastedTo(JScrollPane.class);
        assertThat(scrollPane.getViewport().getView()).isInstanceOf(JTextArea.class);
    }

    @Test
    public void testGenerateSalesReport_SQLException() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_SALES);
        String startDateStr = "2023-05-01";
        String endDateStr = "2023-05-31";
        window.textBox("txtStartDate").setText(startDateStr);
        window.textBox("txtEndDate").setText(endDateStr);

        when(mockTicketDAO.getSalesByRouteForPeriod(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new SQLException("DB error fetching sales by route"));

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        String message = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        assertThat(message).contains("Помилка при генерації звіту '" + REPORT_TYPE_SALES + "'");
        assertThat(message).contains("DB error fetching sales by route");
        optionPane.okButton().click();
    }



    @Test
    public void testGenerateFlightLoadReport_Successful() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        String reportDateStr = "2023-06-15";
        window.textBox("txtReportDate").setText(reportDateStr);

        Route route1 = createMockRoute(1L, "Львів - Київ");
        Flight flight1 = new Flight(101L, route1, LocalDateTime.of(2023, 6, 15, 10, 0), LocalDateTime.of(2023, 6, 15, 18, 0), 50, FlightStatus.PLANNED, "Mercedes", new BigDecimal("300"));
        Route route2 = createMockRoute(2L, "Одеса - Дніпро");
        Flight flight2 = new Flight(102L, route2, LocalDateTime.of(2023, 6, 15, 12, 30), LocalDateTime.of(2023, 6, 15, 20, 0), 30, FlightStatus.PLANNED, "Neoplan", new BigDecimal("250"));

        List<Flight> flights = Arrays.asList(flight1, flight2);
        when(mockFlightDAO.getFlightsByDate(LocalDate.parse(reportDateStr))).thenReturn(flights);
        when(mockFlightDAO.getOccupiedSeatsCount(101L)).thenReturn(25); // 50% load
        when(mockFlightDAO.getOccupiedSeatsCount(102L)).thenReturn(30); // 100% load

        window.button("btnGenerateReport").click();
        Pause.pause(500);

        JScrollPane scrollPane = window.scrollPane("reportScrollPane").targetCastedTo(JScrollPane.class);
        assertThat(scrollPane.getViewport().getView()).isInstanceOf(JTable.class);

        JTableFixture table = window.table("reportTable");
        table.requireVisible();
        table.requireRowCount(2);
        table.requireColumnCount(6);

        String[] expectedColumnHeaders = {"ID Рейсу", "Маршрут", "Відправлення", "Місць всього", "Зайнято", "Завантаженість (%)"};
        assertThat(table.target().getColumnName(LOAD_COL_ID)).isEqualTo(expectedColumnHeaders[LOAD_COL_ID]);
        assertThat(table.target().getColumnName(LOAD_COL_ROUTE)).isEqualTo(expectedColumnHeaders[LOAD_COL_ROUTE]);
        assertThat(table.target().getColumnName(LOAD_COL_DEPARTURE)).isEqualTo(expectedColumnHeaders[LOAD_COL_DEPARTURE]);
        assertThat(table.target().getColumnName(LOAD_COL_TOTAL_SEATS)).isEqualTo(expectedColumnHeaders[LOAD_COL_TOTAL_SEATS]);
        assertThat(table.target().getColumnName(LOAD_COL_OCCUPIED)).isEqualTo(expectedColumnHeaders[LOAD_COL_OCCUPIED]);
        assertThat(table.target().getColumnName(LOAD_COL_PERCENTAGE)).isEqualTo(expectedColumnHeaders[LOAD_COL_PERCENTAGE]);



        table.cell(TableCell.row(0).column(LOAD_COL_ID)).requireValue(String.valueOf(flight1.getId()));
        table.cell(TableCell.row(0).column(LOAD_COL_ROUTE)).requireValue(flight1.getRoute().getFullRouteDescription());
        table.cell(TableCell.row(0).column(LOAD_COL_DEPARTURE)).requireValue(flight1.getDepartureDateTime().format(TABLE_DATE_TIME_FORMATTER));
        table.cell(TableCell.row(0).column(LOAD_COL_TOTAL_SEATS)).requireValue(String.valueOf(flight1.getTotalSeats()));
        table.cell(TableCell.row(0).column(LOAD_COL_OCCUPIED)).requireValue(String.valueOf(25));
        table.cell(TableCell.row(0).column(LOAD_COL_PERCENTAGE)).requireValue("50.00 %");


        table.cell(TableCell.row(1).column(LOAD_COL_ID)).requireValue(String.valueOf(flight2.getId()));
        table.cell(TableCell.row(1).column(LOAD_COL_ROUTE)).requireValue(flight2.getRoute().getFullRouteDescription());
        table.cell(TableCell.row(1).column(LOAD_COL_DEPARTURE)).requireValue(flight2.getDepartureDateTime().format(TABLE_DATE_TIME_FORMATTER));
        table.cell(TableCell.row(1).column(LOAD_COL_TOTAL_SEATS)).requireValue(String.valueOf(flight2.getTotalSeats()));
        table.cell(TableCell.row(1).column(LOAD_COL_OCCUPIED)).requireValue(String.valueOf(30));
        table.cell(TableCell.row(1).column(LOAD_COL_PERCENTAGE)).requireValue("100.00 %");
    }

    @Test
    public void testGenerateFlightLoadReport_NoFlights() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        String reportDateStr = "2023-07-01";
        window.textBox("txtReportDate").setText(reportDateStr);

        when(mockFlightDAO.getFlightsByDate(LocalDate.parse(reportDateStr))).thenReturn(Collections.emptyList());

        window.button("btnGenerateReport").click();
        Pause.pause(200);

        JScrollPane scrollPane = window.scrollPane("reportScrollPane").targetCastedTo(JScrollPane.class);
        assertThat(scrollPane.getViewport().getView()).isInstanceOf(JTable.class);

        JTableFixture table = window.table("reportTable");
        table.requireVisible();
        table.requireRowCount(0);
        assertThat(table.target().getModel().getColumnCount()).isEqualTo(6);
        String[] expectedColumnHeaders = {"ID Рейсу", "Маршрут", "Відправлення", "Місць всього", "Зайнято", "Завантаженість (%)"};
        assertThat(table.target().getColumnName(LOAD_COL_ID)).isEqualTo(expectedColumnHeaders[LOAD_COL_ID]);
    }

    @Test
    public void testGenerateFlightLoadReport_GetFlightsSQLException() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        String reportDateStr = "2023-08-01";
        window.textBox("txtReportDate").setText(reportDateStr);

        when(mockFlightDAO.getFlightsByDate(LocalDate.parse(reportDateStr)))
                .thenThrow(new SQLException("DB error fetching flights by date"));

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        String message = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        assertThat(message).contains("Помилка при генерації звіту '" + REPORT_TYPE_LOAD + "'");
        assertThat(message).contains("DB error fetching flights by date");
        optionPane.okButton().click();
    }

    @Test
    public void testGenerateFlightLoadReport_GetOccupiedSeatsSQLException() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        String reportDateStr = "2023-09-01";
        window.textBox("txtReportDate").setText(reportDateStr);

        Route route1 = createMockRoute(3L, "Тернопіль - Івано-Франківськ");
        Flight flight1 = new Flight(201L, route1, LocalDateTime.of(2023, 9, 1, 14, 0), LocalDateTime.of(2023, 9, 1, 16, 0), 40, FlightStatus.PLANNED, "Setra", new BigDecimal("150"));

        when(mockFlightDAO.getFlightsByDate(LocalDate.parse(reportDateStr))).thenReturn(Collections.singletonList(flight1));
        when(mockFlightDAO.getOccupiedSeatsCount(201L)).thenThrow(new SQLException("DB error fetching occupied seats"));

        window.button("btnGenerateReport").click();
        Pause.pause(200);

        JTableFixture table = window.table("reportTable");
        table.requireRowCount(1);
        table.cell(TableCell.row(0).column(LOAD_COL_ID)).requireValue(String.valueOf(flight1.getId()));
        table.cell(TableCell.row(0).column(LOAD_COL_OCCUPIED)).requireValue(String.valueOf(0));
        table.cell(TableCell.row(0).column(LOAD_COL_PERCENTAGE)).requireValue("0.00 %");
    }


    @Test
    public void testGenerateFlightLoadReport_InvalidDateFormat_ShowsError() {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_LOAD);
        window.textBox("txtReportDate").setText("invalid-date-format");

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        String message = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        assertThat(message).contains("Неправильний формат дати");
        assertThat(message).contains("invalid-date-format");
        assertThat(message).contains("РРРР-ММ-ДД");
        optionPane.okButton().click();
    }

    @Test
    public void testHandleGenericException_WhenPanelIsShowing_ShowsJOptionPane() throws SQLException {
        window.comboBox("cmbReportType").selectItem(REPORT_TYPE_STATUS);
        String exceptionMessage = "Generic test error for showing panel";

        when(mockTicketDAO.getTicketCountsByStatus()).thenThrow(new RuntimeException(exceptionMessage));

        window.button("btnGenerateReport").click();
        Pause.pause(100);

        JOptionPaneFixture optionPane = JOptionPaneFinder.findOptionPane().using(robot());
        optionPane.requireErrorMessage();
        optionPane.requireTitle("Внутрішня помилка програми");

        String dialogMessage = GuiActionRunner.execute(() -> optionPane.target().getMessage().toString());
        String expectedUserMessagePart = "Непередбачена помилка при генерації звіту '" + REPORT_TYPE_STATUS + "'";

        assertThat(dialogMessage).isEqualTo(expectedUserMessagePart + ":\n" + exceptionMessage);
        optionPane.okButton().click();
    }



    @Override
    protected void onTearDown() {
        window.cleanUp();
        Mockito.reset(mockTicketDAO, mockFlightDAO);
    }
}
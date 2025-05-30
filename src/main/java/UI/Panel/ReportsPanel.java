package UI.Panel;

import DAO.FlightDAO;
import DAO.TicketDAO;
import Models.Flight;
import Models.Enums.TicketStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

/**
 * Панель для генерації та відображення звітів у системі автовокзалу.
 * Дозволяє користувачеві вибирати тип звіту, вказувати параметри (якщо потрібно)
 * та переглядати сформовані звіти у текстовому або табличному вигляді.
 * Підтримувані звіти:
 * <ul>
 *     <li>Продажі за маршрутами за вказаний період.</li>
 *     <li>Завантаженість рейсів на конкретну дату.</li>
 *     <li>Статистика квитків за їх статусами.</li>
 * </ul>
 */
public class ReportsPanel extends JPanel {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(new Locale("uk", "UA"));
    public static final DateTimeFormatter TABLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private JComboBox<String> cmbReportType;
    private JPanel parametersPanel;
    private JButton btnGenerateReport;
    private JTextArea reportTextArea;
    private JTable reportTable; // Зберігаємо посилання на таблицю
    private JScrollPane reportScrollPane;

    private final TicketDAO ticketDAO;
    private final FlightDAO flightDAO;

    private JTextField txtStartDate, txtEndDate, txtReportDate;

    /**
     * Конструктор панелі звітів для використання в програмі.
     * Ініціалізує DAO, компоненти UI.
     *
     * @throws RuntimeException якщо не вдалося ініціалізувати {@link TicketDAO} або {@link FlightDAO}.
     */
    public ReportsPanel() {
        this(createTicketDAOInternal(), createFlightDAOInternal());
        logger.info("ReportsPanel створено з DAO за замовчуванням.");
    }

    private static TicketDAO createTicketDAOInternal() {
        try {
            return new TicketDAO();
        } catch (Exception e) {

            throw new RuntimeException("Не вдалося ініціалізувати TicketDAO", e);
        }
    }

    private static FlightDAO createFlightDAOInternal() {
        try {
            return new FlightDAO();
        } catch (Exception e) {
            throw new RuntimeException("Не вдалося ініціалізувати FlightDAO", e);
        }
    }


    /**
     * Конструктор панелі звітів для тестування та ін'єкції залежностей.
     * @param ticketDAO DAO для роботи з квитками.
     * @param flightDAO DAO для роботи з рейсами.
     * @throws IllegalArgumentException якщо будь-який з наданих DAO є null.
     */
    public ReportsPanel(TicketDAO ticketDAO, FlightDAO flightDAO) {
        logger.info("Ініціалізація ReportsPanel з наданими DAO.");
        if (ticketDAO == null || flightDAO == null) {
            String missingDAO = (ticketDAO == null ? "TicketDAO" : "") + (flightDAO == null ? (ticketDAO == null ? " та " : "") + "FlightDAO" : "");
            logger.fatal("Наданий {} не може бути null при створенні ReportsPanel.", missingDAO);

            if (this.isDisplayable()){
                JOptionPane.showMessageDialog(this, "Критична помилка: не вдалося ініціалізувати сервіси даних ("+missingDAO+").", "Помилка ініціалізації", JOptionPane.ERROR_MESSAGE);
            }
            throw new IllegalArgumentException(missingDAO + " не може бути null.");
        }
        this.ticketDAO = ticketDAO;
        this.flightDAO = flightDAO;
        logger.debug("TicketDAO та FlightDAO успішно присвоєні.");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        initComponents();
        logger.info("ReportsPanel успішно ініціалізовано.");
    }

    /**
     * Ініціалізує та розміщує компоненти користувацького інтерфейсу панелі.
     */
    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для ReportsPanel.");
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setName("topPanel");

        JPanel reportSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reportSelectionPanel.add(new JLabel("Тип звіту:"));
        cmbReportType = new JComboBox<>(new String[]{
                "Оберіть тип звіту...",
                "Продажі за маршрутами (період)",
                "Завантаженість рейсів (дата)",
                "Статистика по статусах квитків"
        });
        cmbReportType.setName("cmbReportType");
        cmbReportType.addActionListener(this::onReportTypeChange);
        reportSelectionPanel.add(cmbReportType);

        btnGenerateReport = new JButton("Сформувати звіт");
        btnGenerateReport.setName("btnGenerateReport");
        btnGenerateReport.setEnabled(false);
        btnGenerateReport.addActionListener(this::generateReportAction);
        reportSelectionPanel.add(btnGenerateReport);

        topPanel.add(reportSelectionPanel, BorderLayout.NORTH);

        parametersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        parametersPanel.setName("parametersPanel");
        topPanel.add(parametersPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

        reportTextArea = new JTextArea(15, 70);
        reportTextArea.setName("reportTextArea");
        reportTextArea.setEditable(false);
        reportTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        reportScrollPane = new JScrollPane(reportTextArea);
        reportScrollPane.setName("reportScrollPane");

        add(reportScrollPane, BorderLayout.CENTER);
        logger.debug("Компоненти UI для ReportsPanel успішно створені та додані.");
    }

    /**
     * Обробник події зміни вибраного типу звіту в JComboBox.
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void onReportTypeChange(ActionEvent e) {
        String selectedReport = (String) cmbReportType.getSelectedItem();
        logger.info("Змінено тип звіту на: '{}'", selectedReport != null ? selectedReport : "null");
        parametersPanel.removeAll();
        boolean isReportSelectedAndValid = selectedReport != null && !"Оберіть тип звіту...".equals(selectedReport);
        btnGenerateReport.setEnabled(isReportSelectedAndValid);

        if ("Продажі за маршрутами (період)".equals(selectedReport)) {
            logger.debug("Налаштування параметрів для звіту 'Продажі за маршрутами'.");
            JLabel lblStartDate = new JLabel("З:");

            parametersPanel.add(lblStartDate);

            txtStartDate = new JTextField(10);
            txtStartDate.setName("txtStartDate");
            txtStartDate.setText(LocalDate.now().minusMonths(1).format(DATE_FORMATTER));
            parametersPanel.add(txtStartDate);

            JLabel lblEndDate = new JLabel("По:");

            parametersPanel.add(lblEndDate);

            txtEndDate = new JTextField(10);
            txtEndDate.setName("txtEndDate");
            txtEndDate.setText(LocalDate.now().format(DATE_FORMATTER));
            parametersPanel.add(txtEndDate);
        } else if ("Завантаженість рейсів (дата)".equals(selectedReport)) {
            logger.debug("Налаштування параметрів для звіту 'Завантаженість рейсів'.");
            JLabel lblReportDate = new JLabel("Дата:");

            parametersPanel.add(lblReportDate);

            txtReportDate = new JTextField(10);
            txtReportDate.setName("txtReportDate");
            txtReportDate.setText(LocalDate.now().format(DATE_FORMATTER));
            parametersPanel.add(txtReportDate);
        } else if ("Статистика по статусах квитків".equals(selectedReport)) {

        }

        parametersPanel.revalidate();
        parametersPanel.repaint();
        logger.trace("Панель параметрів оновлено.");
    }

    /**
     * Обробник події натискання кнопки "Сформувати звіт".
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void generateReportAction(ActionEvent e) {
        String selectedReport = (String) cmbReportType.getSelectedItem();
        logger.info("Натиснуто кнопку 'Сформувати звіт'. Обраний тип: '{}'", selectedReport != null ? selectedReport : "null");

        if (selectedReport == null || "Оберіть тип звіту...".equals(selectedReport)) {
            logger.warn("Спроба сформувати звіт без вибору типу.");
            JOptionPane.showMessageDialog(this, "Будь ласка, оберіть тип звіту.", "Увага", JOptionPane.WARNING_MESSAGE);
            return;
        }

        reportTextArea.setText("");

        if (reportTable != null && reportScrollPane.getViewport().getView() == reportTable) {
            logger.debug("Очищення попереднього табличного звіту, встановлення JTextArea.");
            reportScrollPane.setViewportView(reportTextArea);
            reportTable = null;
        } else {
            logger.debug("Очищення попереднього текстового звіту (або таблиця не відображалася).");
        }


        try {
            switch (selectedReport) {
                case "Продажі за маршрутами (період)":
                    logger.debug("Генерація звіту 'Продажі за маршрутами'.");
                    generateSalesByRouteReport();
                    break;
                case "Завантаженість рейсів (дата)":
                    logger.debug("Генерація звіту 'Завантаженість рейсів'.");
                    generateFlightLoadReport();
                    break;
                case "Статистика по статусах квитків":
                    logger.debug("Генерація звіту 'Статистика по статусах квитків'.");
                    generateTicketStatusReport();
                    break;
                default:
                    logger.warn("Обрано непідтримуваний тип звіту: '{}'", selectedReport);
                    reportTextArea.setText("Тип звіту не підтримується.");
            }
        } catch (DateTimeParseException ex) {
            logger.warn("Помилка формату дати при генерації звіту '{}'. Введені дати: Start='{}', End='{}', ReportDate='{}'.",
                    selectedReport,
                    (txtStartDate != null ? txtStartDate.getText() : "N/A"),
                    (txtEndDate != null ? txtEndDate.getText() : "N/A"),
                    (txtReportDate != null ? txtReportDate.getText() : "N/A"),
                    ex);
            JOptionPane.showMessageDialog(this, "Неправильний формат дати: " + ex.getParsedString() + "\nВикористовуйте формат РРРР-ММ-ДД.", "Помилка формату дати", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            handleSqlException("Помилка при генерації звіту '" + selectedReport + "'", ex);
        } catch (Exception ex) {
            handleGenericException("Непередбачена помилка при генерації звіту '" + selectedReport + "'", ex);
        }
    }

    /**
     * Генерує звіт про продажі за маршрутами за вказаний період.
     * @throws SQLException Якщо виникає помилка при доступі до бази даних.
     * @throws DateTimeParseException Якщо введено некоректний формат дати.
     */
    private void generateSalesByRouteReport() throws SQLException, DateTimeParseException {
        if (txtStartDate == null || txtEndDate == null) {
            logger.error("Поля дат для звіту 'Продажі за маршрутами' не ініціалізовані.");
            JOptionPane.showMessageDialog(this, "Помилка: поля для вводу дат не знайдено.", "Внутрішня помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        LocalDate startDate = LocalDate.parse(txtStartDate.getText().trim(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(txtEndDate.getText().trim(), DATE_FORMATTER);
        logger.info("Генерація звіту продажів за маршрутами за період з {} по {}.", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            logger.warn("Помилка дат: початкова дата ({}) пізніше кінцевої ({}).", startDate, endDate);
            JOptionPane.showMessageDialog(this, "Початкова дата не може бути пізніше кінцевої.", "Помилка дати", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Map<String, Map<String, Object>> salesData = ticketDAO.getSalesByRouteForPeriod(startDate, endDate);
        logger.debug("Отримано {} записів для звіту продажів.", salesData.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Звіт: Продажі за маршрутами\n");
        sb.append("Період: з ").append(startDate.format(DATE_FORMATTER)).append(" по ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("-----------------------------------------------------------------\n");
        if (salesData.isEmpty()) {
            sb.append("За вказаний період продажів не знайдено.\n");
            logger.info("Продажів за маршрутами за вказаний період не знайдено.");
        } else {
            sb.append(String.format("%-40s | %15s | %10s\n", "Маршрут", "Сума продажів", "К-ть квитків"));
            sb.append("-----------------------------------------------------------------\n");
            BigDecimal totalSalesOverall = BigDecimal.ZERO;
            int totalTicketsOverall = 0;
            for (Map.Entry<String, Map<String, Object>> entry : salesData.entrySet()) {
                String routeName = entry.getKey();
                BigDecimal totalAmount = (BigDecimal) entry.getValue().get("totalSales");
                int ticketCount = (Integer) entry.getValue().get("ticketCount");
                sb.append(String.format("%-40.40s | %15s | %10d\n", routeName, CURRENCY_FORMATTER.format(totalAmount), ticketCount));
                totalSalesOverall = totalSalesOverall.add(totalAmount);
                totalTicketsOverall += ticketCount;
            }
            sb.append("-----------------------------------------------------------------\n");
            sb.append(String.format("%-40s | %15s | %10d\n", "Всього:", CURRENCY_FORMATTER.format(totalSalesOverall), totalTicketsOverall));
            logger.info("Звіт продажів за маршрутами сформовано. Загальна сума: {}, Загальна к-ть квитків: {}",
                    CURRENCY_FORMATTER.format(totalSalesOverall), totalTicketsOverall);
        }
        sb.append("-----------------------------------------------------------------\n");
        reportTextArea.setText(sb.toString());
        reportScrollPane.setViewportView(reportTextArea);
    }

    /**
     * Генерує звіт про завантаженість рейсів на вказану дату.
     * @throws SQLException Якщо виникає помилка при доступі до бази даних.
     * @throws DateTimeParseException Якщо введено некоректний формат дати.
     */
    private void generateFlightLoadReport() throws SQLException, DateTimeParseException {
        if (txtReportDate == null) {
            logger.error("Поле дати для звіту 'Завантаженість рейсів' не ініціалізоване.");
            JOptionPane.showMessageDialog(this, "Помилка: поле для вводу дати не знайдено.", "Внутрішня помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        LocalDate reportDate = LocalDate.parse(txtReportDate.getText().trim(), DATE_FORMATTER);
        logger.info("Генерація звіту завантаженості рейсів на дату: {}", reportDate);

        List<Flight> flights = flightDAO.getFlightsByDate(reportDate);
        logger.debug("Знайдено {} рейсів на дату {}.", flights.size(), reportDate);

        String[] columnNames = {"ID Рейсу", "Маршрут", "Відправлення", "Місць всього", "Зайнято", "Завантаженість (%)"};

        if (flights.isEmpty()) {
            logger.info("На дату {} рейсів не знайдено. Відображення порожньої таблиці.", reportDate);

            DefaultTableModel emptyModel = new DefaultTableModel(new Object[][]{}, columnNames);
            if (reportTable == null) {
                reportTable = new JTable(emptyModel);
                reportTable.setName("reportTable");
                reportTable.setEnabled(false);
                reportTable.setFillsViewportHeight(true);
            } else {
                reportTable.setModel(emptyModel);
            }
        } else {
            Object[][] data = new Object[flights.size()][columnNames.length];
            for (int i = 0; i < flights.size(); i++) {
                Flight flight = flights.get(i);
                int occupiedSeats = 0;
                try {
                    occupiedSeats = flightDAO.getOccupiedSeatsCount(flight.getId());
                } catch (SQLException sqlEx) {
                    logger.error("Помилка отримання кількості зайнятих місць для рейсу ID: {}", flight.getId(), sqlEx);

                }
                double loadPercentage = (flight.getTotalSeats() > 0) ? ((double) occupiedSeats / flight.getTotalSeats()) * 100 : 0;

                data[i][0] = flight.getId();
                data[i][1] = (flight.getRoute() != null && flight.getRoute().getFullRouteDescription() != null) ? flight.getRoute().getFullRouteDescription() : "Маршрут не вказано";
                data[i][2] = (flight.getDepartureDateTime() != null) ? flight.getDepartureDateTime().format(TABLE_DATE_TIME_FORMATTER) : "Дата не вказана";
                data[i][3] = flight.getTotalSeats();
                data[i][4] = occupiedSeats;
                data[i][5] = String.format(Locale.US, "%.2f %%", loadPercentage);
                logger.trace("Дані для звіту завантаженості: Рейс ID={}, Зайнято={}, Завантаженість={}%", flight.getId(), occupiedSeats, String.format(Locale.US, "%.2f", loadPercentage));
            }

            if (reportTable == null) {
                reportTable = new JTable(data, columnNames);
                reportTable.setName("reportTable");
                reportTable.setEnabled(false);
                reportTable.setFillsViewportHeight(true);
            } else {
                ((DefaultTableModel) reportTable.getModel()).setDataVector(data, columnNames);
            }


            SwingUtilities.invokeLater(() -> {
                if (reportTable.getColumnModel().getColumnCount() > 5) {
                    DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
                    rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
                    try {
                        reportTable.getColumnModel().getColumn(0).setCellRenderer(rightRenderer);
                        reportTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
                        reportTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
                        reportTable.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        logger.warn("Помилка при налаштуванні рендерера для таблиці завантаженості: {}", e.getMessage());
                    }
                } else {
                    logger.warn("Не вдалося налаштувати рендерер для таблиці завантаженості рейсів - недостатньо стовпців (потрібно >5, є {}).", reportTable.getColumnModel().getColumnCount());
                }
            });
        }
        reportScrollPane.setViewportView(reportTable);
        logger.info("Звіт завантаженості рейсів на дату {} сформовано.", reportDate);
    }


    /**
     * Генерує звіт про статистику квитків за їх статусами.
     * @throws SQLException Якщо виникає помилка при доступі до бази даних.
     */
    private void generateTicketStatusReport() throws SQLException {
        logger.info("Генерація звіту статистики по статусах квитків.");
        Map<TicketStatus, Integer> statusCounts = ticketDAO.getTicketCountsByStatus();
        logger.debug("Отримано статистику статусів квитків: {}", statusCounts);

        StringBuilder sb = new StringBuilder();
        sb.append("Звіт: Статистика по статусах квитків\n");
        sb.append("-------------------------------------\n");
        sb.append(String.format("%-20s | %s\n", "Статус", "Кількість"));
        sb.append("-------------------------------------\n");
        int totalTickets = 0;
        if (statusCounts.isEmpty()){
            sb.append("Дані відсутні.\n");
        } else {
            for (Map.Entry<TicketStatus, Integer> entry : statusCounts.entrySet()) {
                String displayName = (entry.getKey() != null && entry.getKey().getDisplayName() != null) ? entry.getKey().getDisplayName() : "Невідомий статус";
                sb.append(String.format("%-20s | %d\n", displayName, entry.getValue()));
                totalTickets += entry.getValue();
            }
        }
        sb.append("-------------------------------------\n");
        sb.append(String.format("%-20s | %d\n", "Всього квитків:", totalTickets));
        sb.append("-------------------------------------\n");
        reportTextArea.setText(sb.toString());
        reportScrollPane.setViewportView(reportTextArea); // Переконуємося, що TextArea видима
        logger.info("Звіт статистики по статусах квитків сформовано. Всього квитків: {}", totalTickets);
    }

    /**
     * Обробляє винятки типу {@link SQLException}.
     * @param userMessage Повідомлення для користувача.
     * @param e Об'єкт винятку.
     */
    private void handleSqlException(String userMessage, SQLException e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        if (this.isShowing()) {
            JOptionPane.showMessageDialog(this,
                    userMessage + ":\n" + e.getMessage(),
                    "Помилка бази даних",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("ReportsPanel не видима, JOptionPane для SQLException не буде показано: {}", userMessage);
        }
    }

    /**
     * Обробляє загальні винятки.
     * @param userMessage Повідомлення для користувача.
     * @param e Об'єкт винятку.
     */
    private void handleGenericException(String userMessage, Exception e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        if (this.isShowing()) {
            JOptionPane.showMessageDialog(this,
                    userMessage + ":\n" + e.getMessage(),
                    "Внутрішня помилка програми",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("ReportsPanel не видима, JOptionPane для GenericException не буде показано: {}", userMessage);
        }
    }
}
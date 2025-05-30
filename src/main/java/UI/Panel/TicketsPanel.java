package UI.Panel;

import DAO.*;
import Models.Flight;
import Models.Enums.FlightStatus;
import Models.Stop;
import UI.Dialog.BookingDialog;
import UI.Model.FlightsTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Панель для пошуку рейсів та бронювання квитків.
 * Надає користувацький інтерфейс для вибору пунктів відправлення та призначення, дати,
 * перегляду списку доступних рейсів, вибору місця та подальшого бронювання квитка.
 *
 */
public class TicketsPanel extends JPanel {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DIALOG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public JComboBox<Stop> cmbDepartureStop;
    public JComboBox<Stop> cmbDestinationStop;
    private JTextField txtDepartureDate;
    private JButton btnSearchFlights;
    private JTable flightsResultTable;
    private FlightsTableModel flightsResultTableModel;
    private JList<String> listAvailableSeats;
    private DefaultListModel<String> availableSeatsModel;
    private JButton btnBookTicket;
    private JLabel lblSelectedFlightInfo;

    private final FlightDAO flightDAO;
    private final StopDAO stopDAO;
    private final TicketDAO ticketDAO;
    private final PassengerDAO passengerDAO;

    private Flight selectedFlightForBooking;

    public TicketsPanel(FlightDAO flightDAO, StopDAO stopDAO, TicketDAO ticketDAO, PassengerDAO passengerDAO) {
        logger.info("Ініціалізація TicketsPanel з наданими DAO.");
        try {
            this.flightDAO = flightDAO;
            this.stopDAO = stopDAO;
            this.ticketDAO = ticketDAO;
            this.passengerDAO = passengerDAO;
            logger.debug("Всі DAO успішно створені для TicketsPanel.");
        } catch (Exception e) {
            logger.fatal("Не вдалося створити один або декілька DAO в TicketsPanel.", e);
            JOptionPane.showMessageDialog(this, "Критична помилка: не вдалося ініціалізувати сервіси даних.", "Помилка ініціалізації", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Не вдалося ініціалізувати DAO", e);
        }

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initComponents();
        logger.info("TicketsPanel успішно ініціалізовано.");
    }

    /**
     * Конструктор панелі пошуку та бронювання квитків.
     * Ініціалізує необхідні DAO, компоненти користувацького інтерфейсу
     * та завантажує початкові дані (список зупинок).
     *
     * @throws RuntimeException якщо не вдалося ініціалізувати один або декілька DAO.
     */
    public TicketsPanel() {
        logger.info("Ініціалізація TicketsPanel.");
        try {
            this.flightDAO = new FlightDAO();
            this.stopDAO = new StopDAO();
            this.ticketDAO = new TicketDAO();
            this.passengerDAO = new PassengerDAO();
            logger.debug("Всі DAO успішно створені для TicketsPanel.");
        } catch (Exception e) {
            logger.fatal("Не вдалося створити один або декілька DAO в TicketsPanel.", e);
            JOptionPane.showMessageDialog(this, "Критична помилка: не вдалося ініціалізувати сервіси даних.", "Помилка ініціалізації", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Не вдалося ініціалізувати DAO", e);
        }

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initComponents();
        logger.info("TicketsPanel успішно ініціалізовано.");
    }

    /**
     * Ініціалізує та розміщує компоненти користувацького інтерфейсу панелі.
     * Створює панель пошуку рейсів (з випадаючими списками зупинок, полем для дати та кнопкою пошуку),
     * таблицю для відображення результатів пошуку, панель для деталей обраного рейсу
     * (включаючи список доступних місць та кнопку бронювання).
     */
    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для TicketsPanel.");
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Пошук рейсів"));

        searchPanel.add(new JLabel("Пункт відправлення:"));
        cmbDepartureStop = new JComboBox<>();
        cmbDepartureStop.setName("cmbDepartureStop");
        cmbDepartureStop.setPrototypeDisplayValue(new Stop(0, "Довга назва міста для прототипу", "Місто X"));
        searchPanel.add(cmbDepartureStop);

        searchPanel.add(new JLabel("Пункт призначення:"));
        cmbDestinationStop = new JComboBox<>();
        cmbDestinationStop.setName("cmbDestinationStop");
        cmbDestinationStop.setPrototypeDisplayValue(new Stop(0, "Довга назва міста для прототипу", "Місто Y"));
        searchPanel.add(cmbDestinationStop);

        searchPanel.add(new JLabel("Дата (РРРР-ММ-ДД):"));
        txtDepartureDate = new JTextField(10);
        txtDepartureDate.setName("txtDepartureDate");
        txtDepartureDate.setText(LocalDate.now().format(DATE_FORMATTER));
        searchPanel.add(txtDepartureDate);

        btnSearchFlights = new JButton("Знайти рейси");
        btnSearchFlights.setName("btnSearchFlights");
        btnSearchFlights.addActionListener(this::searchFlightsAction);
        searchPanel.add(btnSearchFlights);

        add(searchPanel, BorderLayout.NORTH);

        JPanel resultsAndDetailsPanel = new JPanel(new BorderLayout(10,10));

        flightsResultTableModel = new FlightsTableModel(new ArrayList<>());
        flightsResultTable = new JTable(flightsResultTableModel);
        flightsResultTable.setName("flightsResultTable");
        flightsResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        flightsResultTable.setAutoCreateRowSorter(true);
        flightsResultTable.setFillsViewportHeight(true);
        flightsResultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && flightsResultTable.getSelectedRow() != -1) {
                int viewRow = flightsResultTable.getSelectedRow();
                int modelRow = flightsResultTable.convertRowIndexToModel(viewRow);
                logger.debug("Змінено вибір у таблиці результатів пошуку. Вибраний рядок (view): {}, (model): {}.", viewRow, modelRow);
                selectedFlightForBooking = flightsResultTableModel.getFlightAt(modelRow);
                if (selectedFlightForBooking != null) {
                    logger.info("Обрано рейс ID: {} для перегляду деталей.", selectedFlightForBooking.getId());
                    updateFlightDetailsAndSeats(selectedFlightForBooking);
                } else {
                    logger.warn("Вибрано рядок, але не вдалося отримати об'єкт рейсу (модельний індекс: {}).", modelRow);
                }
            } else if (flightsResultTable.getSelectedRow() == -1) {
                logger.debug("Вибір у таблиці результатів пошуку знято. Очищення деталей рейсу.");
                clearFlightDetailsAndSeats();
            }
        });
        JScrollPane flightsTableScrollPane = new JScrollPane(flightsResultTable);
        flightsTableScrollPane.setPreferredSize(new Dimension(700, 200));

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        if (flightsResultTable.getColumnModel().getColumnCount() > 6) {
            flightsResultTable.getColumnModel().getColumn(0).setCellRenderer(rightRenderer);
            flightsResultTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
            flightsResultTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);
        } else {
            logger.warn("Не вдалося налаштувати рендерер для таблиці рейсів - недостатньо стовпців.");
        }

        if (flightsResultTable.getColumnModel().getColumnCount() > 7) {
            flightsResultTable.getColumnModel().getColumn(0).setPreferredWidth(40);
            flightsResultTable.getColumnModel().getColumn(1).setPreferredWidth(250);
            flightsResultTable.getColumnModel().getColumn(2).setPreferredWidth(120);
            flightsResultTable.getColumnModel().getColumn(3).setPreferredWidth(120);
            flightsResultTable.getColumnModel().getColumn(4).setPreferredWidth(60);
            flightsResultTable.getColumnModel().getColumn(5).setPreferredWidth(100);
            flightsResultTable.getColumnModel().getColumn(6).setPreferredWidth(80);
            flightsResultTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        } else {
            logger.warn("Не вдалося налаштувати ширину стовпців для таблиці рейсів - недостатньо стовпців.");
        }

        JPanel flightDetailsPanel = new JPanel(new BorderLayout(5,5));
        flightDetailsPanel.setBorder(BorderFactory.createTitledBorder("Деталі рейсу та доступні місця"));

        lblSelectedFlightInfo = new JLabel("Оберіть рейс зі списку вище для перегляду деталей.");
        lblSelectedFlightInfo.setName("lblSelectedFlightInfo");
        lblSelectedFlightInfo.setHorizontalAlignment(SwingConstants.CENTER);
        flightDetailsPanel.add(lblSelectedFlightInfo, BorderLayout.NORTH);

        availableSeatsModel = new DefaultListModel<>();
        listAvailableSeats = new JList<>(availableSeatsModel);
        listAvailableSeats.setName("listAvailableSeats");
        listAvailableSeats.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listAvailableSeats.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        listAvailableSeats.setVisibleRowCount(-1);
        JScrollPane seatsScrollPane = new JScrollPane(listAvailableSeats);
        seatsScrollPane.setPreferredSize(new Dimension(300, 150));
        flightDetailsPanel.add(seatsScrollPane, BorderLayout.CENTER);

        btnBookTicket = new JButton("Забронювати обране місце");
        btnBookTicket.setEnabled(false);
        btnBookTicket.setName("btnBookTicket");
        btnBookTicket.addActionListener(this::bookTicketAction);
        listAvailableSeats.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean isSeatSelected = !listAvailableSeats.isSelectionEmpty();
                boolean isFlightBookable = selectedFlightForBooking != null &&
                        (selectedFlightForBooking.getStatus() == FlightStatus.PLANNED || selectedFlightForBooking.getStatus() == FlightStatus.DELAYED);
                btnBookTicket.setEnabled(isSeatSelected && isFlightBookable);
                logger.trace("Зміна вибору місця. Місце вибрано: {}, Рейс доступний для бронювання: {}. Кнопка 'Забронювати': {}",
                        isSeatSelected, isFlightBookable, btnBookTicket.isEnabled());
            }
        });
        flightDetailsPanel.add(btnBookTicket, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, flightsTableScrollPane, flightDetailsPanel);
        splitPane.setResizeWeight(0.6);
        resultsAndDetailsPanel.add(splitPane, BorderLayout.CENTER);

        add(resultsAndDetailsPanel, BorderLayout.CENTER);

        loadStopsIntoComboBoxes();
        logger.debug("Компоненти UI для TicketsPanel успішно створені та додані.");
    }

    /**
     * Завантажує список зупинок з бази даних та заповнює випадаючі списки
     * пунктів відправлення та призначення.
     * Додає опцію "Будь-який" для можливості пошуку без конкретизації зупинки.
     */
    private void loadStopsIntoComboBoxes() {
        logger.info("Завантаження списку зупинок для JComboBox.");
        try {
            List<Stop> stops = stopDAO.getAllStops();

            Stop emptyStop = new Stop(0, "Будь-який", "місто");
            cmbDepartureStop.addItem(emptyStop);
            cmbDestinationStop.addItem(emptyStop);
            logger.trace("Додано опцію 'Будь-який' до JComboBox зупинок.");

            DefaultListCellRenderer stopRenderer = new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Stop) {
                        Stop s = (Stop) value;
                        if (s.getId() == 0) setText(s.getName());
                        else setText(s.getName() + " (" + s.getCity() + ")");
                    }
                    return this;
                }
            };
            cmbDepartureStop.setRenderer(stopRenderer);
            cmbDestinationStop.setRenderer(stopRenderer);
            logger.trace("Рендерер для JComboBox зупинок встановлено.");

            for (Stop stop : stops) {
                cmbDepartureStop.addItem(stop);
                cmbDestinationStop.addItem(stop);
            }
            logger.info("Успішно завантажено {} зупинок у JComboBox.", stops.size());
        } catch (SQLException e) {
            handleSqlException("Помилка завантаження списку зупинок", e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при завантаженні списку зупинок", e);
        }
    }

    /**
     * Обробляє дію пошуку рейсів.
     * Отримує параметри пошуку з полів форми, фільтрує список всіх рейсів
     * та оновлює таблицю результатів.
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void searchFlightsAction(ActionEvent e) {
        logger.info("Натиснуто кнопку 'Знайти рейси'.");
        Stop departureFilter = (Stop) cmbDepartureStop.getSelectedItem();
        Stop destinationFilter = (Stop) cmbDestinationStop.getSelectedItem();
        LocalDate dateFilter = null;

        String departureStopLog = (departureFilter != null && departureFilter.getId() != 0) ? departureFilter.getId() + " (" + departureFilter.getName() + ")" : "Будь-який";
        String destinationStopLog = (destinationFilter != null && destinationFilter.getId() != 0) ? destinationFilter.getId() + " (" + destinationFilter.getName() + ")" : "Будь-який";
        String dateLog = "Не вказано";

        try {
            if (!txtDepartureDate.getText().trim().isEmpty()) {
                dateFilter = LocalDate.parse(txtDepartureDate.getText().trim(), DATE_FORMATTER);
                dateLog = dateFilter.format(DATE_FORMATTER);
            }
        } catch (DateTimeParseException ex) {
            logger.warn("Помилка формату дати при пошуку рейсів. Введено: '{}'", txtDepartureDate.getText(), ex);
            JOptionPane.showMessageDialog(this, "Неправильний формат дати. Використовуйте РРРР-ММ-ДД.", "Помилка дати", JOptionPane.ERROR_MESSAGE);
            return;
        }
        logger.debug("Параметри пошуку: Відправлення={}, Призначення={}, Дата={}", departureStopLog, destinationStopLog, dateLog);

        final LocalDate finalDateFilter = dateFilter;

        try {
            List<Flight> allFlights = flightDAO.getAllFlights();
            logger.trace("Отримано {} рейсів з DAO перед фільтрацією.", allFlights.size());
            List<Flight> filteredFlights = allFlights.stream()
                    .filter(flight -> flight.getStatus() == FlightStatus.PLANNED || flight.getStatus() == FlightStatus.DELAYED)
                    .filter(flight -> departureFilter == null || departureFilter.getId() == 0 || (flight.getRoute() != null && flight.getRoute().getDepartureStop().getId() == departureFilter.getId()))
                    .filter(flight -> destinationFilter == null || destinationFilter.getId() == 0 || (flight.getRoute() != null && flight.getRoute().getDestinationStop().getId() == destinationFilter.getId()))
                    .filter(flight -> finalDateFilter == null || flight.getDepartureDateTime().toLocalDate().isEqual(finalDateFilter))
                    .sorted(Comparator.comparing(Flight::getDepartureDateTime))
                    .collect(Collectors.toList());

            logger.info("Знайдено {} рейсів за критеріями пошуку.", filteredFlights.size());
            flightsResultTableModel.setFlights(filteredFlights);
            clearFlightDetailsAndSeats();
            if (filteredFlights.isEmpty()){
                JOptionPane.showMessageDialog(this, "Рейсів за вашим запитом не знайдено.", "Результати пошуку", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException ex) {
            handleSqlException("Помилка отримання списку рейсів під час пошуку", ex);
        } catch (Exception ex) {
            handleGenericException("Непередбачена помилка під час пошуку рейсів", ex);
        }
    }

    /**
     * Очищує інформацію про обраний рейс та список доступних місць.
     * Встановлює текст за замовчуванням для мітки деталей рейсу,
     * очищує модель списку місць та деактивує кнопку бронювання.
     */
    private void clearFlightDetailsAndSeats() {
        logger.debug("Очищення деталей обраного рейсу та списку доступних місць.");
        lblSelectedFlightInfo.setText("Оберіть рейс зі списку вище для перегляду деталей.");
        availableSeatsModel.clear();
        btnBookTicket.setEnabled(false);
        selectedFlightForBooking = null;
    }

    /**
     * Оновлює панель деталей рейсу інформацією про обраний рейс та список доступних місць.
     * Якщо рейс не дозволяє бронювання (наприклад, через його статус),
     * відповідне повідомлення додається до інформації про рейс.
     * @param flight Обраний об'єкт {@link Flight}.
     */
    private void updateFlightDetailsAndSeats(Flight flight) {
        if (flight == null) {
            logger.warn("Спроба оновити деталі для null рейсу. Викликається очищення.");
            clearFlightDetailsAndSeats();
            return;
        }
        logger.info("Оновлення деталей та доступних місць для рейсу ID: {}", flight.getId());

        String departureCity = (flight.getRoute() != null && flight.getRoute().getDepartureStop() != null && flight.getRoute().getDepartureStop().getCity() != null) ? flight.getRoute().getDepartureStop().getCity() : "N/A";
        String destinationCity = (flight.getRoute() != null && flight.getRoute().getDestinationStop() != null && flight.getRoute().getDestinationStop().getCity() != null) ? flight.getRoute().getDestinationStop().getCity() : "N/A";
        String departureTime = (flight.getDepartureDateTime() != null) ? flight.getDepartureDateTime().format(DIALOG_DATE_TIME_FORMATTER) : "N/A";
        String arrivalTime = (flight.getArrivalDateTime() != null) ? flight.getArrivalDateTime().format(DIALOG_DATE_TIME_FORMATTER) : "N/A";
        String price = (flight.getPricePerSeat() != null) ? String.format("%.2f грн", flight.getPricePerSeat()) : "N/A";
        String status = (flight.getStatus() != null && flight.getStatus().getDisplayName() != null) ? flight.getStatus().getDisplayName() : "N/A";

        lblSelectedFlightInfo.setText(String.format("Обрано: %s -> %s, Відпр: %s, Приб: %s, Ціна: %s, Статус: %s",
                departureCity, destinationCity, departureTime, arrivalTime, price, status));
        logger.debug("Інформація про рейс встановлена: {}", lblSelectedFlightInfo.getText());

        availableSeatsModel.clear();
        btnBookTicket.setEnabled(false);

        if (flight.getStatus() != FlightStatus.PLANNED && flight.getStatus() != FlightStatus.DELAYED) {
            String unavailableMsg = " | Бронювання неможливе (рейс не запланований або не відкладений).";
            lblSelectedFlightInfo.setText(lblSelectedFlightInfo.getText() + unavailableMsg);
            logger.info("Бронювання для рейсу ID {} неможливе. Статус: {}.", flight.getId(), flight.getStatus());
            return;
        }

        logger.debug("Завантаження зайнятих місць для рейсу ID: {}", flight.getId());
        try {
            List<String> occupiedSeats = ticketDAO.getOccupiedSeatsForFlight(flight.getId());
            int totalSeats = flight.getTotalSeats();
            logger.trace("Рейс ID {}: Всього місць={}, Зайнято місць={}", flight.getId(), totalSeats, occupiedSeats.size());

            int availableCount = 0;
            for (int i = 1; i <= totalSeats; i++) {
                String seatNumber = String.valueOf(i);
                if (!occupiedSeats.contains(seatNumber)) {
                    availableSeatsModel.addElement(seatNumber);
                    availableCount++;
                }
            }
            logger.info("Знайдено {} доступних місць для рейсу ID {}.", availableCount, flight.getId());
            if (availableSeatsModel.isEmpty()){
                lblSelectedFlightInfo.setText(lblSelectedFlightInfo.getText() + " | Вільних місць немає.");
                logger.info("Вільних місць для рейсу ID {} немає.", flight.getId());
            }
        } catch (SQLException e) {
            handleSqlException("Помилка отримання зайнятих місць для рейсу ID: " + flight.getId(), e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при оновленні деталей місць для рейсу ID: " + flight.getId(), e);
        }
    }

    /**
     * Обробляє дію бронювання квитка.
     * Перевіряє, чи обрано рейс та місце, і чи дозволяє статус рейсу бронювання.
     * Відкриває діалогове вікно {@link BookingDialog} для введення даних пасажира.
     * Якщо бронювання успішне, оновлює список доступних місць.
     * @param e Об'єкт події {@link ActionEvent}.
     */
    public void bookTicketAction(ActionEvent e) {
        logger.info("Натиснуто кнопку 'Забронювати обране місце'.");
        if (selectedFlightForBooking == null || listAvailableSeats.isSelectionEmpty()) {
            logger.warn("Спроба забронювати квиток, але рейс або місце не вибрано.");
            JOptionPane.showMessageDialog(this, "Будь ласка, оберіть рейс та вільне місце для бронювання.", "Помилка", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedFlightForBooking.getStatus() != FlightStatus.PLANNED && selectedFlightForBooking.getStatus() != FlightStatus.DELAYED) {
            logger.warn("Спроба забронювати квиток на рейс ID {}, який не має статусу PLANNED або DELAYED. Поточний статус: {}",
                    selectedFlightForBooking.getId(), selectedFlightForBooking.getStatus());
            JOptionPane.showMessageDialog(this, "Неможливо забронювати квиток на цей рейс. Статус рейсу: " + selectedFlightForBooking.getStatus().getDisplayName(), "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedSeat = listAvailableSeats.getSelectedValue();
        logger.info("Відкриття діалогу бронювання для рейсу ID: {} та місця: {}", selectedFlightForBooking.getId(), selectedSeat);
        BookingDialog bookingDialog = new BookingDialog((Frame) SwingUtilities.getWindowAncestor(this),
                selectedFlightForBooking, selectedSeat, passengerDAO, ticketDAO);
        bookingDialog.setVisible(true);

        if (bookingDialog.isBookingConfirmed()) {
            logger.info("Бронювання для рейсу ID {} та місця {} підтверджено. Оновлення деталей рейсу.",
                    selectedFlightForBooking.getId(), selectedSeat);
            updateFlightDetailsAndSeats(selectedFlightForBooking);
        } else {
            logger.debug("Бронювання для рейсу ID {} та місця {} було скасовано або закрито без підтвердження.",
                    selectedFlightForBooking.getId(), selectedSeat);
        }
    }

    /**
     * Обробляє винятки типу {@link SQLException}, логує їх та показує повідомлення користувачу.
     * @param userMessage Повідомлення для користувача, що описує контекст помилки.
     * @param e Об'єкт винятку {@link SQLException}.
     */
    private void handleSqlException(String userMessage, SQLException e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Обробляє загальні винятки (не {@link SQLException}), логує їх та показує повідомлення користувачу.
     * @param userMessage Повідомлення для користувача, що описує контекст помилки.
     * @param e Об'єкт винятку {@link Exception}.
     */
    private void handleGenericException(String userMessage, Exception e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Внутрішня помилка програми", JOptionPane.ERROR_MESSAGE);
    }
}
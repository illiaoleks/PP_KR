package UI.Dialog;

import DAO.FlightDAO;
import DAO.RouteDAO;
import Models.Flight;
import Models.Enums.FlightStatus;
import Models.Route;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean; // Додано для прапорця

/**
 * Діалогове вікно для створення нового або редагування існуючого рейсу.
 */
public class FlightDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private JTextField txtDepartureDateTime, txtArrivalDateTime, txtTotalSeats, txtBusModel, txtPricePerSeat;
    private JComboBox<Route> cmbRoute;
    private JComboBox<FlightStatus> cmbStatus;
    private JButton btnSave; // Зроблено полем
    private JButton btnCancel; // Зроблено полем

    private Flight currentFlight;
    private final FlightDAO flightDAO;
    private final RouteDAO routeDAO;
    private boolean saved = false;


    private static final AtomicBoolean suppressMessagesForTesting = new AtomicBoolean(false);

    /**
     * Встановлює режим придушення повідомлень JOptionPane для тестування.
     * УВАГА: Використовуйте тільки в тестовому середовищі!
     * @param suppress true, щоб придушити повідомлення, false - щоб показувати.
     */
    public static void setSuppressMessagesForTesting(boolean suppress) {
        suppressMessagesForTesting.set(suppress);
        if (suppress) {
            logger.warn("УВАГА: Повідомлення JOptionPane придушені для тестування в FlightDialog!");
        } else {
            logger.info("Режим придушення повідомлень JOptionPane вимкнено в FlightDialog.");
        }
    }


    private void showDialogMessage(Component parentComponent, Object message, String title, int messageType) {
        if (!suppressMessagesForTesting.get()) {
            JOptionPane.showMessageDialog(parentComponent, message, title, messageType);
        } else {
            String typeStr = "";
            switch (messageType) {
                case JOptionPane.ERROR_MESSAGE: typeStr = "ERROR"; break;
                case JOptionPane.INFORMATION_MESSAGE: typeStr = "INFORMATION"; break;
                case JOptionPane.WARNING_MESSAGE: typeStr = "WARNING"; break;
                case JOptionPane.QUESTION_MESSAGE: typeStr = "QUESTION"; break;
                default: typeStr = "UNKNOWN (" + messageType + ")"; break;
            }
            logger.info("FlightDialog JOptionPane придушено (тестовий режим): Титул='{}', Повідомлення='{}', Тип={}", title, message, typeStr);
        }
    }


    /**
     * Конструктор діалогового вікна.
     * @param owner Батьківське вікно.
     * @param title Заголовок вікна.
     * @param flightDAO DAO для роботи з рейсами.
     * @param routeDAO DAO для роботи з маршрутами (для завантаження списку).
     * @param flightToEdit Рейс для редагування, або {@code null} для створення нового.
     * @throws IllegalArgumentException якщо flightDAO або routeDAO є null.
     */
    public FlightDialog(Frame owner, String title, FlightDAO flightDAO, RouteDAO routeDAO, Flight flightToEdit) {
        super(owner, title, true);
        logger.info("Ініціалізація діалогу FlightDialog. Заголовок: '{}'. Редагування рейсу: {}",
                title, (flightToEdit != null ? "ID " + flightToEdit.getId() : "Новий рейс"));

        if (flightDAO == null) {
            logger.fatal("Критична помилка: FlightDAO є null при ініціалізації FlightDialog.");
            showDialogMessage(null, "Помилка ініціалізації діалогу: відсутній FlightDAO.", "Критична помилка", JOptionPane.ERROR_MESSAGE);
            throw new IllegalArgumentException("FlightDAO не може бути null.");
        }
        if (routeDAO == null) {
            logger.fatal("Критична помилка: RouteDAO є null при ініціалізації FlightDialog.");
            showDialogMessage(null, "Помилка ініціалізації діалогу: відсутній RouteDAO.", "Критична помилка", JOptionPane.ERROR_MESSAGE);
            throw new IllegalArgumentException("RouteDAO не може бути null.");
        }

        this.flightDAO = flightDAO;
        this.routeDAO = routeDAO;
        this.currentFlight = flightToEdit;

        initComponents();
        loadRoutesIntoComboBox();

        if (currentFlight != null) {
            logger.debug("Заповнення полів для редагування рейсу ID: {}", currentFlight.getId());
            populateFields(currentFlight);
        } else {
            logger.debug("Встановлення статусу за замовчуванням 'PLANNED' для нового рейсу.");
            if (cmbStatus.getItemCount() > 0) { // Перевірка, чи є елементи в cmbStatus
                cmbStatus.setSelectedItem(FlightStatus.PLANNED); // Переконайтесь, що PLANNED існує і є дійсним
            } else {
                logger.warn("cmbStatus порожній, неможливо встановити статус за замовчуванням.");
            }
        }

        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        logger.debug("Діалог FlightDialog успішно ініціалізовано.");
    }

    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для FlightDialog.");
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(10,10,10,10)); // Додамо відступи

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Маршрут:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        cmbRoute = new JComboBox<>();
        cmbRoute.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Route) {
                    setText(((Route) value).getFullRouteDescription());
                } else if (value == null && index == -1) {
                    setText("Оберіть маршрут...");
                }
                return this;
            }
        });
        formPanel.add(cmbRoute, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("Відправлення (РРРР-ММ-ДД ГГ:ХХ):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        txtDepartureDateTime = new JTextField(16);
        formPanel.add(txtDepartureDateTime, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(new JLabel("Прибуття (РРРР-ММ-ДД ГГ:ХХ):"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2;
        txtArrivalDateTime = new JTextField(16);
        formPanel.add(txtArrivalDateTime, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(new JLabel("Кількість місць:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        txtTotalSeats = new JTextField(5);
        formPanel.add(txtTotalSeats, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(new JLabel("Модель автобуса:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2;
        txtBusModel = new JTextField(15);
        formPanel.add(txtBusModel, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 5; formPanel.add(new JLabel("Ціна за місце (грн):"), gbc);
        gbc.gridx = 1; gbc.gridy = 5; gbc.gridwidth = 2;
        txtPricePerSeat = new JTextField(8);
        formPanel.add(txtPricePerSeat, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 6; formPanel.add(new JLabel("Статус:"), gbc);
        gbc.gridx = 1; gbc.gridy = 6; gbc.gridwidth = 2;
        cmbStatus = new JComboBox<>(FlightStatus.values());
        cmbStatus.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FlightStatus) {
                    setText(((FlightStatus) value).getDisplayName());
                }
                return this;
            }
        });
        formPanel.add(cmbStatus, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnSave = new JButton("Зберегти");
        btnCancel = new JButton("Скасувати");

        btnSave.addActionListener(this::saveFlightAction);
        btnCancel.addActionListener(e -> {
            logger.debug("Натиснуто кнопку 'Скасувати'. Закриття FlightDialog.");
            dispose();
        });

        buttonPanel.add(btnSave);
        buttonPanel.add(btnCancel);

        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        logger.debug("Компоненти UI для FlightDialog успішно створені та додані.");
    }

    private void loadRoutesIntoComboBox() {
        logger.debug("Завантаження маршрутів у JComboBox.");
        try {
            List<Route> routes = routeDAO.getAllRoutes();
            cmbRoute.removeAllItems();
            if (routes == null || routes.isEmpty()) {
                logger.warn("Список маршрутів порожній або null. JComboBox буде деактивовано.");
                cmbRoute.addItem(null);
                cmbRoute.setSelectedItem(null);
                cmbRoute.setEnabled(false);
                showDialogMessage(this, "Список маршрутів порожній. Додайте маршрути перед створенням рейсів.", "Увага", JOptionPane.WARNING_MESSAGE);
            } else {
                cmbRoute.setEnabled(true);
                for (Route route : routes) {
                    cmbRoute.addItem(route);
                }
                logger.info("Успішно завантажено {} маршрутів у JComboBox.", routes.size());
            }
        } catch (SQLException e) {
            logger.error("Помилка завантаження маршрутів для JComboBox.", e);
            showDialogMessage(this,
                    "Не вдалося завантажити список маршрутів: " + e.getMessage(),
                    "Помилка бази даних",
                    JOptionPane.ERROR_MESSAGE);
            cmbRoute.removeAllItems();
            cmbRoute.addItem(null);
            cmbRoute.setSelectedItem(null);
            cmbRoute.setEnabled(false);
        }
    }

    private void populateFields(Flight flight) {
        logger.debug("Заповнення полів діалогу даними рейсу ID: {}", flight.getId());
        boolean routeFound = false;
        if (cmbRoute.getItemCount() > 0 && flight.getRoute() != null) {
            for (int i = 0; i < cmbRoute.getItemCount(); i++) {
                Route item = cmbRoute.getItemAt(i);
                if (item != null && item.getId() == flight.getRoute().getId()) {
                    cmbRoute.setSelectedIndex(i);
                    routeFound = true;
                    logger.trace("Маршрут '{}' обрано для рейсу ID: {}", item.getFullRouteDescription(), flight.getId());
                    break;
                }
            }
        }
        if (!routeFound && flight.getRoute() != null) {
            logger.warn("Маршрут ID: {} для рейсу ID: {} не знайдено в списку JComboBox. Можливо, маршрут було видалено.",
                    flight.getRoute().getId(), flight.getId());

        } else if (flight.getRoute() == null) {
            cmbRoute.setSelectedItem(null);
        }


        txtDepartureDateTime.setText(flight.getDepartureDateTime().format(INPUT_DATE_TIME_FORMATTER));
        txtArrivalDateTime.setText(flight.getArrivalDateTime().format(INPUT_DATE_TIME_FORMATTER));
        txtTotalSeats.setText(String.valueOf(flight.getTotalSeats()));
        txtBusModel.setText(flight.getBusModel() != null ? flight.getBusModel() : "");
        txtPricePerSeat.setText(flight.getPricePerSeat().toString());
        cmbStatus.setSelectedItem(flight.getStatus());
        logger.debug("Поля діалогу заповнені даними рейсу.");
    }

    private void saveFlightAction(ActionEvent event) {
        logger.info("Спроба зберегти дані рейсу.");
        Route selectedRoute = (Route) cmbRoute.getSelectedItem();

        if (selectedRoute == null && cmbRoute.isEnabled()) {
            logger.warn("Валідація не пройдена: маршрут не обрано, хоча JComboBox активний.");
            showDialogMessage(this, "Будь ласка, оберіть маршрут.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!cmbRoute.isEnabled() && currentFlight == null) {
            logger.warn("Валідація не пройдена: спроба створити новий рейс без доступних маршрутів.");
            showDialogMessage(this, "Неможливо створити рейс: список маршрутів порожній або не завантажений.", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalDateTime departureDateTime, arrivalDateTime;
        try {
            departureDateTime = LocalDateTime.parse(txtDepartureDateTime.getText().trim(), INPUT_DATE_TIME_FORMATTER);
            arrivalDateTime = LocalDateTime.parse(txtArrivalDateTime.getText().trim(), INPUT_DATE_TIME_FORMATTER);
            logger.trace("Дати розпарсені: Відправлення={}, Прибуття={}", departureDateTime, arrivalDateTime);
        } catch (DateTimeParseException e) {
            logger.warn("Помилка валідації: неправильний формат дати/часу. Введено: Відпр='{}', Приб='{}'",
                    txtDepartureDateTime.getText(), txtArrivalDateTime.getText(), e);
            showDialogMessage(this, "Неправильний формат дати/часу. Використовуйте РРРР-ММ-ДД ГГ:ХХ.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (arrivalDateTime.isBefore(departureDateTime) || arrivalDateTime.isEqual(departureDateTime)) {
            logger.warn("Помилка валідації: час прибуття ({}) не пізніше часу відправлення ({}).", arrivalDateTime, departureDateTime);
            showDialogMessage(this, "Час прибуття має бути пізніше часу відправлення.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int totalSeats;
        try {
            totalSeats = Integer.parseInt(txtTotalSeats.getText().trim());
            if (totalSeats <= 0) {
                logger.warn("Помилка валідації: кількість місць ({}) не є позитивним числом.", totalSeats);
                showDialogMessage(this, "Кількість місць має бути позитивним числом.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
                return;
            }
            logger.trace("Кількість місць: {}", totalSeats);
        } catch (NumberFormatException e) {
            return;
        }

        BigDecimal pricePerSeat;
        try {
            pricePerSeat = new BigDecimal(txtPricePerSeat.getText().trim().replace(",", "."));
            if (pricePerSeat.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Помилка валідації: ціна ({}) не може бути від'ємною.", pricePerSeat);
                return;
            }
            logger.trace("Ціна за місце: {}", pricePerSeat);
        } catch (NumberFormatException e) {
            logger.warn("Помилка валідації: неправильний формат ціни. Введено: '{}'", txtPricePerSeat.getText(), e);
            showDialogMessage(this, "Неправильний формат ціни.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String busModel = txtBusModel.getText().trim();
        FlightStatus status = (FlightStatus) cmbStatus.getSelectedItem();
        if (status == null && cmbStatus.getItemCount() > 0) {
            return;
        } else if (status == null) {
            return;
        }

        logger.trace("Модель автобуса: '{}', Статус: {}", busModel, status);

        try {
            if (currentFlight == null) {
                logger.debug("Створення нового рейсу.");

                Flight newFlight = new Flight(0, selectedRoute, departureDateTime, arrivalDateTime, totalSeats, status, busModel, pricePerSeat);
                logger.debug("Створено об'єкт нового рейсу.");
                if (flightDAO.addFlight(newFlight)) {
                    saved = true;
                    logger.info("Новий рейс ID: {} успішно додано.", newFlight.getId());
                    dispose();
                } else {
                    logger.warn("Не вдалося додати новий рейс (flightDAO.addFlight повернув false).");
                    showDialogMessage(this, "Не вдалося додати рейс (DAO повернув false).", "Помилка збереження", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                logger.debug("Оновлення існуючого рейсу ID: {}", currentFlight.getId());
                Route routeToSet = (selectedRoute != null) ? selectedRoute : currentFlight.getRoute();


                currentFlight.setRoute(routeToSet);
                currentFlight.setDepartureDateTime(departureDateTime);
                currentFlight.setArrivalDateTime(arrivalDateTime);
                currentFlight.setTotalSeats(totalSeats);
                currentFlight.setBusModel(busModel);
                currentFlight.setPricePerSeat(pricePerSeat);
                currentFlight.setStatus(status);
                logger.debug("Об'єкт рейсу ID: {} підготовлено до оновлення.", currentFlight.getId());
                if (flightDAO.updateFlight(currentFlight)) {
                    saved = true;
                    logger.info("Рейс ID: {} успішно оновлено.", currentFlight.getId());
                    dispose();
                } else {
                    logger.warn("Не вдалося оновити рейс ID: {} (flightDAO.updateFlight повернув false).", currentFlight.getId());
                    showDialogMessage(this, "Не вдалося оновити рейс (DAO повернув false).", "Помилка збереження", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            logger.error("Помилка бази даних при збереженні рейсу. Режим: {}", (currentFlight == null ? "Створення" : "Оновлення"), ex);
            showDialogMessage(this,
                    "Помилка під час взаємодії з базою даних: " + ex.getMessage(),
                    "Помилка бази даних",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException exArg) {
            logger.error("Помилка даних при збереженні рейсу: {}", exArg.getMessage(), exArg);
            showDialogMessage(this, "Помилка валідації даних: " + exArg.getMessage(), "Помилка даних", JOptionPane.ERROR_MESSAGE);
        }
        catch (Exception exGeneral) {
            logger.error("Непередбачена помилка при збереженні рейсу. Режим: {}", (currentFlight == null ? "Створення" : "Оновлення"), exGeneral);
            showDialogMessage(this,
                    "Сталася непередбачена помилка: " + exGeneral.getMessage(),
                    "Внутрішня помилка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        logger.trace("Перевірка статусу збереження: {}", saved);
        return saved;
    }

    // Геттери для компонентів UI
    public JTextField getTxtDepartureDateTime() { return txtDepartureDateTime; }
    public JTextField getTxtArrivalDateTime() { return txtArrivalDateTime; }
    public JTextField getTxtTotalSeats() { return txtTotalSeats; }
    public JTextField getTxtBusModel() { return txtBusModel; }
    public JTextField getTxtPricePerSeat() { return txtPricePerSeat; }
    public JComboBox<Route> getCmbRoute() { return cmbRoute; }
    public JComboBox<FlightStatus> getCmbStatus() { return cmbStatus; }
    public JButton getBtnSave() { return btnSave; }
    public JButton getBtnCancel() { return btnCancel; }
}
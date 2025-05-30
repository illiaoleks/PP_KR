package UI.Panel;

import DAO.FlightDAO;
import DAO.RouteDAO;
import DAO.StopDAO;
import Models.Flight;
import Models.Enums.FlightStatus;
import Models.Route;
import UI.Dialog.FlightDialog;
import UI.Dialog.RouteCreationDialog;
import UI.Model.FlightsTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Панель для управління рейсами.
 * Надає користувацький інтерфейс для перегляду списку рейсів,
 * додавання нових рейсів, редагування, скасування існуючих,
 * а також ініціює створення нових маршрутів через відповідний діалог.
 */
public class FlightsPanel extends JPanel {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private JTable flightsTable;
    private FlightsTableModel flightsTableModel;
    private JButton btnAddFlight, btnEditFlight, btnCancelFlight, btnRefreshFlights, btnAddNewRoute;

    private final FlightDAO flightDAO;
    private final RouteDAO routeDAO;
    private final StopDAO stopDAO;


    private static final AtomicBoolean suppressMessagesForTesting = new AtomicBoolean(false);

    /**
     * Встановлює режим придушення повідомлень JOptionPane для тестування.
     * УВАГА: Використовуйте тільки в тестовому середовищі!
     * @param suppress true, щоб придушити повідомлення, false - щоб показувати.
     */
    public static void setSuppressMessagesForTesting(boolean suppress) {
        suppressMessagesForTesting.set(suppress);
        if (suppress) {
            logger.warn("УВАГА: Повідомлення JOptionPane придушені для тестування в FlightsPanel!");
        } else {
            logger.info("Режим придушення повідомлень JOptionPane вимкнено в FlightsPanel.");
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
            logger.info("FlightsPanel JOptionPane придушено (тестовий режим): Титул='{}', Повідомлення='{}', Тип={}", title, message, typeStr);
        }
    }



    /**
     * Конструктор панелі управління рейсами для використання в програмі.
     * Ініціалізує DAO через new, компоненти UI та завантажує початкові дані про рейси.
     *
     * @throws RuntimeException якщо не вдалося ініціалізувати один з DAO.
     */
    public FlightsPanel() {
        this(createFlightDAOInternal(), createRouteDAOInternal(), createStopDAOInternal());
        logger.info("FlightsPanel створено з DAO за замовчуванням.");
    }

    private static FlightDAO createFlightDAOInternal() {
        try {
            return new FlightDAO();
        } catch (Exception e) {
            logger.fatal("Критична помилка при створенні FlightDAO в конструкторі за замовчуванням.", e);
            throw new RuntimeException("Не вдалося ініціалізувати FlightDAO", e);
        }
    }

    private static RouteDAO createRouteDAOInternal() {
        try {
            return new RouteDAO();
        } catch (Exception e) {
            logger.fatal("Критична помилка при створенні RouteDAO в конструкторі за замовчуванням.", e);
            throw new RuntimeException("Не вдалося ініціалізувати RouteDAO", e);
        }
    }

    private static StopDAO createStopDAOInternal() {
        try {
            return new StopDAO();
        } catch (Exception e) {
            logger.fatal("Критична помилка при створенні StopDAO в конструкторі за замовчуванням.", e);
            throw new RuntimeException("Не вдалося ініціалізувати StopDAO", e);
        }
    }

    /**
     * Конструктор панелі управління рейсами для тестування та ін'єкції залежностей.
     * Ініціалізує компоненти UI та завантажує початкові дані про рейси, використовуючи надані DAO.
     *
     * @param flightDAO DAO для роботи з рейсами.
     * @param routeDAO DAO для роботи з маршрутами.
     * @param stopDAO DAO для роботи із зупинками.
     * @throws IllegalArgumentException якщо будь-який з наданих DAO є null.
     */
    public FlightsPanel(FlightDAO flightDAO, RouteDAO routeDAO, StopDAO stopDAO) {
        logger.info("Ініціалізація FlightsPanel з наданими DAO.");
        if (flightDAO == null) {
            logger.fatal("Наданий FlightDAO не може бути null.");
            throw new IllegalArgumentException("FlightDAO не може бути null.");
        }
        if (routeDAO == null) {
            logger.fatal("Наданий RouteDAO не може бути null.");
            throw new IllegalArgumentException("RouteDAO не може бути null.");
        }
        if (stopDAO == null) {
            logger.fatal("Наданий StopDAO не може бути null.");
            throw new IllegalArgumentException("StopDAO не може бути null.");
        }
        this.flightDAO = flightDAO;
        this.routeDAO = routeDAO;
        this.stopDAO = stopDAO;
        logger.debug("DAO успішно присвоєні.");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        initComponents();
        loadFlightsData();
        logger.info("FlightsPanel успішно ініціалізовано.");
    }


    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для FlightsPanel.");
        flightsTableModel = new FlightsTableModel(new ArrayList<>());
        flightsTable = new JTable(flightsTableModel);
        flightsTable.setName("flightsTable");
        flightsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        flightsTable.setAutoCreateRowSorter(true);
        flightsTable.setFillsViewportHeight(true);


        SwingUtilities.invokeLater(() -> {
            if (flightsTable.getColumnModel().getColumnCount() > 0) {
                DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
                rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
                try {
                    if (flightsTable.getColumnCount() > 6) {
                        flightsTable.getColumnModel().getColumn(0).setCellRenderer(rightRenderer);
                        flightsTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
                        flightsTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);
                    }
                    if (flightsTable.getColumnCount() > 7) {
                        flightsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
                        flightsTable.getColumnModel().getColumn(1).setPreferredWidth(250);
                        flightsTable.getColumnModel().getColumn(2).setPreferredWidth(120);
                        flightsTable.getColumnModel().getColumn(3).setPreferredWidth(120);
                        flightsTable.getColumnModel().getColumn(4).setPreferredWidth(60);
                        flightsTable.getColumnModel().getColumn(5).setPreferredWidth(100);
                        flightsTable.getColumnModel().getColumn(6).setPreferredWidth(80);
                        flightsTable.getColumnModel().getColumn(7).setPreferredWidth(100);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.warn("Помилка при налаштуванні рендерера/ширини стовпців: індекс поза межами. Кількість стовпців: {}", flightsTable.getColumnModel().getColumnCount(), e);
                }
            } else {
                logger.warn("Модель стовпців для flightsTable ще не ініціалізована або порожня.");
            }
        });


        flightsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent mouseEvent) {
                JTable table = (JTable) mouseEvent.getSource();
                Point point = mouseEvent.getPoint();
                int row = table.rowAtPoint(point);
                if (row != -1 && mouseEvent.getClickCount() == 2) {
                    logger.debug("Подвійний клік на рядку {} таблиці рейсів.", row);
                    int modelRow = table.convertRowIndexToModel(row);
                    Flight flightToEdit = flightsTableModel.getFlightAt(modelRow);
                    if (flightToEdit != null) {
                        logger.info("Відкриття діалогу редагування для рейсу ID: {}", flightToEdit.getId());
                        openEditFlightDialog(flightToEdit);
                    } else {
                        logger.warn("Подвійний клік на рядку {}, але не вдалося отримати рейс для редагування (модельний індекс {}).", row, modelRow);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(flightsTable);
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnAddFlight = new JButton("Додати рейс");
        btnAddFlight.setName("btnAddFlight");
        btnEditFlight = new JButton("Редагувати рейс");
        btnEditFlight.setName("btnEditFlight");
        btnCancelFlight = new JButton("Скасувати рейс");
        btnCancelFlight.setName("btnCancelFlight");
        btnRefreshFlights = new JButton("Оновити список");
        btnRefreshFlights.setName("btnRefreshFlights");
        btnAddNewRoute = new JButton("Створити маршрут");
        btnAddNewRoute.setName("btnAddNewRoute");

        btnAddFlight.addActionListener(this::addFlightAction);
        btnEditFlight.addActionListener(this::editFlightAction);
        btnCancelFlight.addActionListener(this::cancelFlightAction);
        btnRefreshFlights.addActionListener(e -> {
            logger.info("Натиснуто кнопку 'Оновити список' рейсів.");
            loadFlightsData();
        });
        btnAddNewRoute.addActionListener(this::addNewRouteAction);

        buttonPanel.add(btnAddFlight);
        buttonPanel.add(btnEditFlight);
        buttonPanel.add(btnCancelFlight);
        buttonPanel.add(btnRefreshFlights);
        buttonPanel.add(btnAddNewRoute);

        add(buttonPanel, BorderLayout.SOUTH);
        logger.debug("Компоненти UI для FlightsPanel успішно створені та додані.");
    }

    public void loadFlightsData() {
        logger.info("Завантаження даних про рейси.");
        try {
            List<Flight> flights = flightDAO.getAllFlights();
            flightsTableModel.setFlights(flights != null ? flights : new ArrayList<>());
            logger.info("Успішно завантажено {} рейсів.", (flights != null ? flights.size() : 0));
        } catch (SQLException e) {
            handleSqlException("Не вдалося завантажити список рейсів", e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при завантаженні списку рейсів", e);
        }
    }

    private Frame getOwnerFrame() {
        Window topLevelAncestor = SwingUtilities.getWindowAncestor(this);
        if (topLevelAncestor instanceof Frame) {
            return (Frame) topLevelAncestor;
        }
        logger.warn("Батьківське вікно не є JFrame, діалоги можуть не мати коректного власника.");
        return null;
    }

    private void openEditFlightDialog(Flight flightToEdit) {
        if (flightToEdit == null) {
            logger.error("Спроба відкрити діалог редагування для null рейсу.");
            showDialogMessage(this, "Не вдалося отримати дані обраного рейсу для редагування.", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        logger.debug("Відкриття FlightDialog для редагування рейсу ID: {}", flightToEdit.getId());
        FlightDialog dialog = new FlightDialog(getOwnerFrame(), "Редагувати рейс ID: " + flightToEdit.getId(), flightDAO, routeDAO, flightToEdit);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            logger.info("Рейс ID: {} було відредаговано та збережено. Оновлення списку рейсів.", flightToEdit.getId());
            loadFlightsData();
        } else {
            logger.debug("Редагування рейсу ID: {} було скасовано або закрито без збереження.", flightToEdit.getId());
        }
    }

    private void addFlightAction(ActionEvent e) {
        logger.info("Натиснуто кнопку 'Додати рейс'. Відкриття FlightDialog для створення нового рейсу.");
        FlightDialog dialog = new FlightDialog(getOwnerFrame(), "Новий рейс", flightDAO, routeDAO, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            logger.info("Новий рейс було створено та збережено. Оновлення списку рейсів.");
            loadFlightsData();
        } else {
            logger.debug("Створення нового рейсу було скасовано або закрито без збереження.");
        }
    }

    private void editFlightAction(ActionEvent e) {
        logger.debug("Натиснуто кнопку 'Редагувати рейс'.");
        int selectedRowView = flightsTable.getSelectedRow();
        if (selectedRowView == -1) {
            logger.warn("Спроба редагувати рейс, але жоден рядок не вибрано.");
            showDialogMessage(this, "Будь ласка, виберіть рейс для редагування.", "Рейс не вибрано", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = flightsTable.convertRowIndexToModel(selectedRowView);
        Flight flightToEdit = flightsTableModel.getFlightAt(modelRow);
        if (flightToEdit != null) {
            logger.info("Відкриття діалогу редагування для рейсу ID: {} (обраний рядок: {}, модельний індекс: {}).",
                    flightToEdit.getId(), selectedRowView, modelRow);
            openEditFlightDialog(flightToEdit);
        } else {
            logger.error("Не вдалося отримати рейс для редагування. Обраний рядок: {}, модельний індекс: {}.", selectedRowView, modelRow);
            showDialogMessage(this, "Помилка: не вдалося отримати дані вибраного рейсу.", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelFlightAction(ActionEvent e) {
        logger.debug("Натиснуто кнопку 'Скасувати рейс'.");
        int selectedRowView = flightsTable.getSelectedRow();
        if (selectedRowView == -1) {
            logger.warn("Спроба скасувати рейс, але жоден рядок не вибрано.");
            showDialogMessage(this, "Будь ласка, виберіть рейс для скасування.", "Рейс не вибрано", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = flightsTable.convertRowIndexToModel(selectedRowView);
        Flight flightToCancel = flightsTableModel.getFlightAt(modelRow);

        if (flightToCancel != null) {
            logger.info("Спроба скасувати рейс ID: {}. Поточний статус: {}", flightToCancel.getId(), flightToCancel.getStatus());
            if (flightToCancel.getStatus() == FlightStatus.CANCELLED) {
                logger.info("Рейс ID: {} вже скасовано.", flightToCancel.getId());
                showDialogMessage(this, "Цей рейс вже скасовано.", "Інформація", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (flightToCancel.getStatus() == FlightStatus.DEPARTED || flightToCancel.getStatus() == FlightStatus.ARRIVED) {
                logger.warn("Спроба скасувати рейс ID: {}, який вже відправлений/прибув. Статус: {}", flightToCancel.getId(), flightToCancel.getStatus());
                showDialogMessage(this, "Неможливо скасувати рейс, який вже відправлений або прибув.", "Помилка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String routeDescription = (flightToCancel.getRoute() != null && flightToCancel.getRoute().getFullRouteDescription() != null) ? flightToCancel.getRoute().getFullRouteDescription() : "N/A";

            int confirmation;
            if (!suppressMessagesForTesting.get()) {
                confirmation = JOptionPane.showConfirmDialog(this,
                        "Ви впевнені, що хочете скасувати рейс ID " + flightToCancel.getId() + " (" + routeDescription + ")?",
                        "Підтвердження скасування", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            } else {
                logger.info("FlightsPanel JOptionPane.showConfirmDialog придушено (тестовий режим). Автоматично підтверджуємо YES_OPTION.");
                confirmation = JOptionPane.YES_OPTION;
            }


            if (confirmation == JOptionPane.YES_OPTION) {
                logger.debug("Користувач підтвердив скасування рейсу ID: {}", flightToCancel.getId());
                try {
                    if (flightDAO.updateFlightStatus(flightToCancel.getId(), FlightStatus.CANCELLED)) {
                        logger.info("Рейс ID: {} успішно скасовано.", flightToCancel.getId());
                        showDialogMessage(this, "Рейс успішно скасовано.", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                        loadFlightsData();
                    } else {
                        logger.warn("Не вдалося скасувати рейс ID: {} (DAO повернув false).", flightToCancel.getId());
                        showDialogMessage(this, "Не вдалося скасувати рейс (операція DAO не вдалася).", "Помилка", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (SQLException ex) {
                    handleSqlException("Помилка бази даних при скасуванні рейсу ID: " + flightToCancel.getId(), ex);
                } catch (Exception exGeneral) {
                    handleGenericException("Непередбачена помилка при скасуванні рейсу ID: " + flightToCancel.getId(), exGeneral);
                }
            } else {
                logger.debug("Користувач скасував операцію скасування рейсу ID: {}", flightToCancel.getId());
            }
        } else {
            logger.error("Не вдалося отримати рейс для скасування. Обраний рядок: {}, модельний індекс: {}.", selectedRowView, modelRow);
            showDialogMessage(this, "Не вдалося отримати дані обраного рейсу для скасування.", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addNewRouteAction(ActionEvent e) {
        logger.info("Натиснуто кнопку 'Створити маршрут'. Відкриття RouteCreationDialog.");
        RouteCreationDialog routeDialog = new RouteCreationDialog(getOwnerFrame(), stopDAO);
        routeDialog.setVisible(true);

        if (routeDialog.isSaved()) {
            Route newRoute = routeDialog.getCreatedRoute();
            if (newRoute != null) {
                logger.debug("RouteCreationDialog повернув новий маршрут: {}", newRoute.getFullRouteDescription());
                try {
                    if (routeDAO.addRoute(newRoute)) {
                        logger.info("Новий маршрут успішно додано до БД. ID: {}", newRoute.getId());
                        showDialogMessage(this, "Новий маршрут '" + newRoute.getFullRouteDescription() + "' успішно створено та додано.", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        logger.warn("Не вдалося додати новий маршрут до БД (DAO повернув false).");
                        showDialogMessage(this, "Не вдалося зберегти новий маршрут в базі даних.", "Помилка збереження", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (SQLException ex) {
                    handleSqlException("Помилка SQL при збереженні нового маршруту", ex);
                } catch (Exception exGeneric) {
                    handleGenericException("Непередбачена помилка при збереженні нового маршруту", exGeneric);
                }
            } else {
                logger.warn("RouteCreationDialog був збережений, але повернув null маршрут.");
            }
        } else {
            logger.debug("Створення нового маршруту було скасовано або закрито без збереження.");
        }
    }

    private void handleSqlException(String userMessagePrefix, SQLException e) {
        logger.error("{}: {}", userMessagePrefix, e.getMessage(), e);
        if (this.isShowing()) {
            showDialogMessage(this, userMessagePrefix + ":\n" + e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("FlightsPanel не видима, JOptionPane для SQLException не буде показано: {}", userMessagePrefix);
        }
    }

    private void handleGenericException(String userMessagePrefix, Exception e) {
        logger.error("{}: {}", userMessagePrefix, e.getMessage(), e);
        if (this.isShowing()) {
            showDialogMessage(this, userMessagePrefix + ":\n" + e.getMessage(), "Внутрішня помилка програми", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("FlightsPanel не видима, JOptionPane для GenericException не буде показано: {}", userMessagePrefix);
        }
    }


    public JTable getFlightsTable() { return flightsTable; }
    public FlightsTableModel getFlightsTableModel() { return flightsTableModel; }
    public JButton getBtnAddFlight() { return btnAddFlight; }
    public JButton getBtnEditFlight() { return btnEditFlight; }
    public JButton getBtnCancelFlight() { return btnCancelFlight; }
    public JButton getBtnRefreshFlights() { return btnRefreshFlights; }
    public JButton getBtnAddNewRoute() { return btnAddNewRoute; }
}
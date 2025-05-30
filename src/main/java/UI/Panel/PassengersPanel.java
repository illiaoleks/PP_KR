package UI.Panel;

import DAO.PassengerDAO;
import DAO.TicketDAO;
import Models.Passenger;
import Models.Ticket;
import UI.Dialog.PassengerDialog;
import UI.Model.PassengerHistoryTableModel;
import UI.Model.PassengersTableModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;

import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Панель для управління даними пасажирів та перегляду їхньої історії поїздок.
 * Надає користувацький інтерфейс для відображення списку пасажирів,
 * редагування їхніх даних та перегляду історії куплених/заброньованих квитків
 * для обраного пасажира.
 */
public class PassengersPanel extends JPanel {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private JTable passengersTable;
    private PassengersTableModel passengersTableModel;
    private JTable historyTable;
    private PassengerHistoryTableModel historyTableModel;
    private JButton btnEditPassenger, btnRefreshPassengers;

    private final PassengerDAO passengerDAO;
    private final TicketDAO ticketDAO;

    /**
     * Конструктор панелі управління пасажирами для використання в програмі.
     * Ініціалізує DAO через new, компоненти UI та завантажує початкові дані про пасажирів.
     *
     * @throws RuntimeException якщо не вдалося ініціалізувати {@link PassengerDAO} або {@link TicketDAO}.
     */
    public PassengersPanel() {

        this(createPassengerDAOInternal(), createTicketDAOInternal());
        logger.info("PassengersPanel створено з DAO за замовчуванням.");
    }


    private static PassengerDAO createPassengerDAOInternal() {
        try {
            return new PassengerDAO();
        } catch (Exception e) {

            logger.fatal("Критична помилка при створенні PassengerDAO в конструкторі за замовчуванням.", e);

            throw new RuntimeException("Не вдалося ініціалізувати PassengerDAO", e);
        }
    }

    private static TicketDAO createTicketDAOInternal() {
        try {
            return new TicketDAO();
        } catch (Exception e) {
            throw new RuntimeException("Не вдалося ініціалізувати TicketDAO", e);
        }
    }


    /**
     * Конструктор панелі управління пасажирами для тестування та ін'єкції залежностей.
     * Ініціалізує компоненти UI та завантажує початкові дані про пасажирів, використовуючи надані DAO.
     *
     * @param passengerDAO DAO для роботи з пасажирами.
     * @param ticketDAO    DAO для роботи з квитками.
     * @throws RuntimeException якщо надані DAO є null або виникає інша помилка ініціалізації.
     */
    public PassengersPanel(PassengerDAO passengerDAO, TicketDAO ticketDAO) {
        logger.info("Ініціалізація PassengersPanel з наданими DAO.");
        if (passengerDAO == null || ticketDAO == null) {
            logger.fatal("Надані DAO не можуть бути null при створенні PassengersPanel.");

            throw new IllegalArgumentException("PassengerDAO та TicketDAO не можуть бути null.");
        }
        this.passengerDAO = passengerDAO;
        this.ticketDAO = ticketDAO;
        logger.debug("PassengerDAO та TicketDAO успішно присвоєні.");




        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initComponents();
        loadPassengersData();
        logger.info("PassengersPanel успішно ініціалізовано.");
    }

    /**
     * Ініціалізує та розміщує компоненти користувацького інтерфейсу панелі.
     * Створює таблицю для відображення списку пасажирів, таблицю для історії поїздок,
     * та кнопки для операцій "Редагувати пасажира" та "Оновити список".
     * Налаштовує взаємодію між таблицями (вибір пасажира оновлює історію поїздок).
     */
    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для PassengersPanel.");
        JPanel passengerListPanel = new JPanel(new BorderLayout(5, 5));
        passengerListPanel.setBorder(BorderFactory.createTitledBorder("Список пасажирів"));

        passengersTableModel = new PassengersTableModel(new ArrayList<>());
        passengersTable = new JTable(passengersTableModel);
        passengersTable.setName("passengersTable");
        passengersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        passengersTable.setAutoCreateRowSorter(true);
        passengersTable.setFillsViewportHeight(true);

        passengersTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && passengersTable.getSelectedRow() != -1) {
                int viewRow = passengersTable.getSelectedRow();
                int modelRow = passengersTable.convertRowIndexToModel(viewRow);
                logger.debug("Змінено вибір у таблиці пасажирів. Вибраний рядок (view): {}, (model): {}.", viewRow, modelRow);
                Passenger selectedPassenger = passengersTableModel.getPassengerAt(modelRow);
                if (selectedPassenger != null) {
                    logger.info("Завантаження історії поїздок для пасажира ID: {}", selectedPassenger.getId());
                    loadPassengerHistory(selectedPassenger.getId());
                } else {
                    logger.warn("Вибрано рядок, але не вдалося отримати об'єкт пасажира (модельний індекс: {}).", modelRow);
                }
            } else if (passengersTable.getSelectedRow() == -1 && historyTableModel != null) {
                logger.debug("Вибір у таблиці пасажирів знято. Очищення таблиці історії.");
                historyTableModel.setTickets(new ArrayList<>());
            }
        });

        passengersTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent mouseEvent) {
                JTable table = (JTable) mouseEvent.getSource();
                Point point = mouseEvent.getPoint();
                int row = table.rowAtPoint(point);
                if (row != -1 && mouseEvent.getClickCount() == 2) {
                    logger.debug("Подвійний клік на рядку {} таблиці пасажирів.", row);
                    int modelRow = table.convertRowIndexToModel(row);
                    Passenger passengerToEdit = passengersTableModel.getPassengerAt(modelRow);
                    if (passengerToEdit != null) {
                        logger.info("Відкриття діалогу редагування для пасажира ID: {}", passengerToEdit.getId());
                        openEditPassengerDialog(passengerToEdit);
                    } else {
                        logger.warn("Подвійний клік на рядку {}, але не вдалося отримати пасажира для редагування (модельний індекс {}).", row, modelRow);
                    }
                }
            }
        });

        JScrollPane passengersScrollPane = new JScrollPane(passengersTable);
        passengersScrollPane.setName("passengersScrollPane"); // Опціонально
        passengersScrollPane.setPreferredSize(new Dimension(700, 250));
        passengerListPanel.add(passengersScrollPane, BorderLayout.CENTER);

        JPanel passengerButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        btnEditPassenger = new JButton("Редагувати пасажира");
        btnEditPassenger.setName("btnEditPassenger");

        btnRefreshPassengers = new JButton("Оновити список");
        btnRefreshPassengers.setName("btnRefreshPassengers");

        btnEditPassenger.addActionListener(this::editPassengerAction);
        btnRefreshPassengers.addActionListener(e -> {
            logger.info("Натиснуто кнопку 'Оновити список' пасажирів.");
            loadPassengersData();
        });

        passengerButtonsPanel.add(btnEditPassenger);
        passengerButtonsPanel.add(btnRefreshPassengers);
        passengerListPanel.add(passengerButtonsPanel, BorderLayout.SOUTH);

        JPanel historyPanel = new JPanel(new BorderLayout(5, 5));
        historyPanel.setBorder(BorderFactory.createTitledBorder("Історія поїздок обраного пасажира"));


        historyTableModel = new PassengerHistoryTableModel(new ArrayList<>());
        historyTable = new JTable(historyTableModel);
        historyTable.setName("historyTable");
        historyTable.setFillsViewportHeight(true);

        DefaultTableCellRenderer rightRendererHistory = new DefaultTableCellRenderer();
        rightRendererHistory.setHorizontalAlignment(JLabel.RIGHT);

        SwingUtilities.invokeLater(() -> {
            if (historyTable.getColumnModel().getColumnCount() > 5) {
                logger.trace("Налаштування рендерера для таблиці історії.");
                historyTable.getColumnModel().getColumn(0).setCellRenderer(rightRendererHistory);
                historyTable.getColumnModel().getColumn(1).setCellRenderer(rightRendererHistory);
                historyTable.getColumnModel().getColumn(5).setCellRenderer(rightRendererHistory);
            } else {
                logger.warn("Кількість стовпців у таблиці історії ({}) менша за 6, рендерер може бути не встановлено для всіх стовпців.", historyTable.getColumnModel().getColumnCount());
            }
        });


        JScrollPane historyScrollPane = new JScrollPane(historyTable);
        historyScrollPane.setName("historyScrollPane"); // Опціонально
        historyScrollPane.setPreferredSize(new Dimension(700, 200));
        historyPanel.add(historyScrollPane, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, passengerListPanel, historyPanel);

        splitPane.setResizeWeight(0.6);
        add(splitPane, BorderLayout.CENTER);
        logger.debug("Компоненти UI для PassengersPanel успішно створені та додані.");
    }

    /**
     * Відкриває діалогове вікно для редагування даних обраного пасажира.
     * Якщо пасажир не передано ({@code null}), виводить повідомлення про помилку.
     * Після закриття діалогу, якщо дані були збережені, оновлює список пасажирів
     * та намагається відновити попередній вибір у таблиці.
     *
     * @param passengerToEdit Об'єкт {@link Passenger} для редагування.
     */
    private void openEditPassengerDialog(Passenger passengerToEdit) {
        if (passengerToEdit == null) {
            logger.error("Спроба відкрити діалог редагування для null пасажира.");
            JOptionPane.showMessageDialog(this, "Не вдалося отримати дані обраного пасажира для редагування.", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        logger.debug("Відкриття PassengerDialog для редагування пасажира ID: {}", passengerToEdit.getId());

        Window topLevelAncestor = SwingUtilities.getWindowAncestor(this);
        Frame ownerFrame = null;
        if (topLevelAncestor instanceof Frame) {
            ownerFrame = (Frame) topLevelAncestor;
        } else {

            logger.warn("Батьківське вікно не є JFrame, PassengerDialog може не мати коректного власника.");
        }

        PassengerDialog dialog = new PassengerDialog(
                ownerFrame,
                passengerToEdit,
                passengerDAO
        );
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            logger.info("Пасажира ID: {} було відредаговано та збережено. Оновлення списку пасажирів.", passengerToEdit.getId());
            int selectedRowBeforeEdit = passengersTable.getSelectedRow();
            loadPassengersData();
            if (selectedRowBeforeEdit != -1 && selectedRowBeforeEdit < passengersTable.getRowCount()) {
                try {
                    passengersTable.setRowSelectionInterval(selectedRowBeforeEdit, selectedRowBeforeEdit);
                    logger.trace("Відновлено вибір рядка {} після редагування.", selectedRowBeforeEdit);
                } catch (Exception ex) {
                    logger.warn("Не вдалося відновити вибір рядка {} після редагування.", selectedRowBeforeEdit, ex);
                }
            }
        } else {
            logger.debug("Редагування пасажира ID: {} було скасовано або закрито без збереження.", passengerToEdit.getId());
        }
    }

    /**
     * Обробляє дію редагування обраного пасажира.
     * Отримує обраного пасажира з таблиці та викликає метод {@link #openEditPassengerDialog(Passenger)}
     * для відкриття діалогу редагування.
     *
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void editPassengerAction(ActionEvent e) {
        logger.debug("Натиснуто кнопку 'Редагувати пасажира'.");
        int selectedRowView = passengersTable.getSelectedRow();
        if (selectedRowView == -1) {
            logger.warn("Спроба редагувати пасажира, але жоден рядок не вибрано.");
            JOptionPane.showMessageDialog(this, "Будь ласка, виберіть пасажира для редагування.", "Пасажира не вибрано", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = passengersTable.convertRowIndexToModel(selectedRowView);
        Passenger passengerToEdit = passengersTableModel.getPassengerAt(modelRow);
        if (passengerToEdit != null) {
            logger.info("Відкриття діалогу редагування для пасажира ID: {} (обраний рядок: {}, модельний індекс: {}).",
                    passengerToEdit.getId(), selectedRowView, modelRow);
            openEditPassengerDialog(passengerToEdit);
        } else {
            logger.error("Не вдалося отримати пасажира для редагування. Обраний рядок: {}, модельний індекс: {}.", selectedRowView, modelRow);
            JOptionPane.showMessageDialog(this, "Помилка: не вдалося отримати дані вибраного пасажира.", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Завантажує або оновлює список пасажирів у таблиці.
     * Дані отримуються з {@link PassengerDAO}. У випадку помилки,
     * виводиться повідомлення користувачу. Якщо жоден пасажир не обраний після оновлення,
     * таблиця історії поїздок очищується.
     */
    private void loadPassengersData() {
        logger.info("Завантаження даних про пасажирів.");
        try {
            List<Passenger> passengers = passengerDAO.getAllPassengers();
            passengersTableModel.setPassengers(passengers);
            logger.info("Успішно завантажено {} пасажирів.", passengers.size());
            if (passengersTable.getSelectedRow() == -1 && historyTableModel != null) {
                logger.debug("Жоден пасажир не вибраний, очищення таблиці історії.");
                historyTableModel.setTickets(new ArrayList<>());
            }
        } catch (SQLException e) {
            handleSqlException("Помилка завантаження списку пасажирів", e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при завантаженні списку пасажирів", e);
        }
    }

    /**
     * Завантажує історію поїздок для вказаного пасажира та відображає її в таблиці історії.
     * Дані отримуються з {@link TicketDAO}. У випадку помилки,
     * виводиться повідомлення користувачу.
     *
     * @param passengerId Ідентифікатор пасажира, для якого потрібно завантажити історію.
     */
    private void loadPassengerHistory(long passengerId) {
        logger.info("Завантаження історії поїздок для пасажира ID: {}", passengerId);
        try {
            List<Ticket> tickets = ticketDAO.getTicketsByPassengerId(passengerId);
            historyTableModel.setTickets(tickets);
            logger.info("Успішно завантажено {} квитків для історії пасажира ID: {}", tickets.size(), passengerId);
        } catch (SQLException e) {
            handleSqlException("Помилка завантаження історії поїздок для пасажира ID: " + passengerId, e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при завантаженні історії поїздок для пасажира ID: " + passengerId, e);
        }
    }

    /**
     * Обробляє винятки типу {@link SQLException}, логує їх та показує повідомлення користувачу.
     *
     * @param userMessage Повідомлення для користувача, що описує контекст помилки.
     * @param e           Об'єкт винятку {@link SQLException}.
     */
    private void handleSqlException(String userMessage, SQLException e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Обробляє загальні винятки (не {@link SQLException}), логує їх та показує повідомлення користувачу.
     *
     * @param userMessage Повідомлення для користувача, що описує контекст помилки.
     * @param e           Об'єкт винятку {@link Exception}.
     */
    private void handleGenericException(String userMessage, Exception e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Внутрішня помилка програми", JOptionPane.ERROR_MESSAGE);
    }
}
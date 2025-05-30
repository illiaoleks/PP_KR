package UI.Panel;

import DAO.TicketDAO;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;
import Models.Ticket;
import UI.Model.BookingsTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Панель для управління бронюваннями та продажем квитків.
 * Надає користувацький інтерфейс для перегляду списку квитків,
 * їх фільтрації за статусом, а також для виконання операцій продажу
 * та скасування квитків/бронювань.
 */
public class BookingsManagementPanel extends JPanel {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    public JTable bookingsTable;
    public BookingsTableModel bookingsTableModel;
    public JComboBox<TicketStatus> cmbStatusFilter;
    public JButton btnSellTicket;
    public JButton btnCancelBookingTicket;
    public JButton btnRefresh;

    private final TicketDAO ticketDAO;

    /**
     * Конструктор панелі управління бронюваннями.
     * Ініціалізує DAO, компоненти UI та завантажує початкові дані.
     * У разі критичної помилки ініціалізації DAO, робота панелі припиняється.
     */


    /**
     * Конструктор панелі управління бронюваннями для тестування та ін'єкції залежностей.
     * @param ticketDAO DAO для роботи з квитками.
     * @throws IllegalArgumentException якщо наданий ticketDAO є null.
     */
    public BookingsManagementPanel(TicketDAO ticketDAO) {
        logger.info("Ініціалізація BookingsManagementPanel з наданим DAO.");
        if (ticketDAO == null) {
            logger.fatal("Наданий TicketDAO не може бути null при створенні BookingsManagementPanel.");
            JOptionPane.showMessageDialog(this, "Критична помилка: не вдалося ініціалізувати сервіс даних квитків.", "Помилка ініціалізації", JOptionPane.ERROR_MESSAGE);
            throw new IllegalArgumentException("TicketDAO не може бути null.");
        }
        this.ticketDAO = ticketDAO;
        logger.debug("TicketDAO успішно присвоєно.");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        initComponents();
        loadBookingsData(null);
        logger.info("BookingsManagementPanel успішно ініціалізовано.");
    }


    /**
     * Ініціалізує та розміщує компоненти користувацького інтерфейсу панелі.
     */
    private void initComponents() {
        logger.debug("Ініціалізація компонентів UI для BookingsManagementPanel.");
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setName("filterPanel");
        filterPanel.add(new JLabel("Фільтр за статусом:"));

        TicketStatus[] statusesWithAll = new TicketStatus[TicketStatus.values().length + 1];
        statusesWithAll[0] = null;
        System.arraycopy(TicketStatus.values(), 0, statusesWithAll, 1, TicketStatus.values().length);

        cmbStatusFilter = new JComboBox<>(statusesWithAll);
        cmbStatusFilter.setName("cmbStatusFilter");
        cmbStatusFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TicketStatus) {
                    setText(((TicketStatus) value).getDisplayName());
                } else if (value == null) {
                    setText("Всі статуси");
                }
                return this;
            }
        });
        cmbStatusFilter.addActionListener(e -> {
            TicketStatus selectedStatus = (TicketStatus) cmbStatusFilter.getSelectedItem();
            logger.debug("Змінено фільтр статусу на: {}", (selectedStatus != null ? selectedStatus.getDisplayName() : "Всі статуси"));
            loadBookingsData(selectedStatus);
        });
        filterPanel.add(cmbStatusFilter);

        btnRefresh = new JButton("Оновити");
        btnRefresh.setName("btnRefresh");
        btnRefresh.addActionListener(e -> {
            TicketStatus selectedStatus = (TicketStatus) cmbStatusFilter.getSelectedItem();
            logger.info("Натиснуто кнопку 'Оновити'. Поточний фільтр: {}", (selectedStatus != null ? selectedStatus.getDisplayName() : "Всі статуси"));
            loadBookingsData(selectedStatus);
        });
        filterPanel.add(btnRefresh);

        bookingsTableModel = new BookingsTableModel(new ArrayList<>());
        bookingsTable = new JTable(bookingsTableModel);
        bookingsTable.setName("bookingsTable");
        bookingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookingsTable.setAutoCreateRowSorter(true);

        SwingUtilities.invokeLater(() -> {
            if (bookingsTable.getColumnModel().getColumnCount() > 0) {
                DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
                rightRenderer.setHorizontalAlignment(JLabel.RIGHT);

                final int priceColumnIndex = 6;
                if (bookingsTable.getColumnModel().getColumnCount() > priceColumnIndex) {
                    try {
                        bookingsTable.getColumnModel().getColumn(priceColumnIndex).setCellRenderer(rightRenderer);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        logger.warn("Помилка при налаштуванні рендерера для стовпця Ціна (індекс {}): {}", priceColumnIndex, e.getMessage());
                    }
                } else {
                    logger.warn("Не вдалося знайти стовпець 'Ціна' (індекс {}) для налаштування рендерера.", priceColumnIndex);
                }
            }
        });


        JScrollPane scrollPane = new JScrollPane(bookingsTable);
        scrollPane.setName("bookingsScrollPane");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setName("bookingsButtonPanel");
        btnSellTicket = new JButton("Продати квиток");
        btnSellTicket.setName("btnSellTicket");
        btnCancelBookingTicket = new JButton("Скасувати бронювання/квиток");
        btnCancelBookingTicket.setName("btnCancelBookingTicket");

        btnSellTicket.addActionListener(this::sellTicketAction);
        btnCancelBookingTicket.addActionListener(this::cancelTicketAction);

        bookingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        buttonPanel.add(btnSellTicket);
        buttonPanel.add(btnCancelBookingTicket);

        add(filterPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
        logger.debug("Компоненти UI для BookingsManagementPanel успішно створені та додані.");
    }

    /**
     * Оновлює стан активності кнопок "Продати квиток" та "Скасувати бронювання/квиток".
     */
    private void updateButtonStates() {
        int selectedRow = bookingsTable.getSelectedRow();
        logger.trace("Оновлення стану кнопок. Вибраний рядок: {}", selectedRow);

        if (selectedRow == -1) {
            btnSellTicket.setEnabled(false);
            btnCancelBookingTicket.setEnabled(false);
            logger.trace("Жоден рядок не вибрано, кнопки деактивовано.");
            return;
        }
        int modelRow = bookingsTable.convertRowIndexToModel(selectedRow);
        Ticket selectedTicket = bookingsTableModel.getTicketAt(modelRow);

        if (selectedTicket == null) {
            btnSellTicket.setEnabled(false);
            btnCancelBookingTicket.setEnabled(false);
            logger.warn("Не вдалося отримати квиток для вибраного рядка (модельний індекс: {}). Кнопки деактивовано.", modelRow);
            return;
        }

        logger.debug("Оновлення стану кнопок для квитка ID: {}, Статус: {}", selectedTicket.getId(), selectedTicket.getStatus());
        btnSellTicket.setEnabled(selectedTicket.getStatus() == TicketStatus.BOOKED);

        boolean canCancel = selectedTicket.getStatus() == TicketStatus.BOOKED || selectedTicket.getStatus() == TicketStatus.SOLD;
        if (canCancel && selectedTicket.getFlight() != null && selectedTicket.getFlight().getDepartureDateTime() != null) {

            boolean flightNotDepartedOrArrived = selectedTicket.getFlight().getStatus() != FlightStatus.DEPARTED &&
                    selectedTicket.getFlight().getStatus() != FlightStatus.ARRIVED;
            boolean flightIsCancellable = selectedTicket.getFlight().getStatus() == FlightStatus.PLANNED ||
                    selectedTicket.getFlight().getStatus() == FlightStatus.DELAYED;


            canCancel = (selectedTicket.getFlight().getDepartureDateTime().isAfter(LocalDateTime.now()) && flightIsCancellable) ||
                    (selectedTicket.getStatus() == TicketStatus.BOOKED);
        } else if (selectedTicket.getFlight() == null) {
            canCancel = false;
        }


        btnCancelBookingTicket.setEnabled(canCancel);
        logger.trace("Стан кнопок: Продати={}, Скасувати={}", btnSellTicket.isEnabled(), btnCancelBookingTicket.isEnabled());
    }

    /**
     * Завантажує дані про квитки з бази даних, використовуючи вказаний фільтр за статусом,
     * та оновлює таблицю.
     * @param filterStatus Статус для фільтрації квитків. Якщо {@code null}, завантажуються всі квитки.
     */
    public void loadBookingsData(TicketStatus filterStatus) {
        String statusForLog = (filterStatus != null) ? filterStatus.getDisplayName() : "Всі статуси";
        logger.info("Завантаження даних про бронювання/квитки. Фільтр за статусом: {}", statusForLog);
        try {
            List<Ticket> tickets = ticketDAO.getAllTickets(filterStatus);
            bookingsTableModel.setTickets(tickets);
            logger.info("Успішно завантажено {} квитків.", tickets.size());
        } catch (SQLException e) {
            handleSqlException("Помилка завантаження списку квитків. Фільтр: " + statusForLog, e);
        } catch (Exception e) {
            handleGenericException("Непередбачена помилка при завантаженні списку квитків. Фільтр: " + statusForLog, e);
        }
        updateButtonStates();
    }

    /**
     * Обробляє дію продажу обраного квитка.
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void sellTicketAction(ActionEvent e) {
        int selectedRow = bookingsTable.getSelectedRow();
        if (selectedRow == -1) {
            logger.warn("Спроба продати квиток, але жоден рядок не вибрано.");

            return;
        }
        int modelRow = bookingsTable.convertRowIndexToModel(selectedRow);
        Ticket ticketToSell = bookingsTableModel.getTicketAt(modelRow);

        if (ticketToSell == null) {
            handleGenericException("Не вдалося отримати дані вибраного квитка для продажу.", new IllegalStateException("Selected ticket is null at model row " + modelRow));
            return;
        }

        logger.info("Спроба продати квиток ID: {}. Пасажир: {}. Поточний статус: {}",
                ticketToSell.getId(), ticketToSell.getPassenger().getFullName(), ticketToSell.getStatus());

        if (ticketToSell.getStatus() == TicketStatus.BOOKED) {
            int confirmation = JOptionPane.showConfirmDialog(this,
                    "Продати квиток ID " + ticketToSell.getId() + " пасажиру " + ticketToSell.getPassenger().getFullName() + "?",
                    "Підтвердження продажу", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirmation == JOptionPane.YES_OPTION) {
                logger.debug("Користувач підтвердив продаж квитка ID: {}", ticketToSell.getId());
                try {
                    if (ticketDAO.updateTicketStatus(ticketToSell.getId(), TicketStatus.SOLD, LocalDateTime.now())) {
                        logger.info("Квиток ID: {} успішно продано.", ticketToSell.getId());
                        JOptionPane.showMessageDialog(this, "Квиток успішно продано.", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                        loadBookingsData((TicketStatus) cmbStatusFilter.getSelectedItem());
                    } else {
                        handleGenericException("Не вдалося продати квиток (операція DAO не вдалася).", new SQLException("DAO returned false for ticket sale."));
                    }
                } catch (SQLException ex) {
                    handleSqlException("Помилка БД при продажу квитка ID: " + ticketToSell.getId(), ex);
                } catch (Exception exGeneral) {
                    handleGenericException("Непередбачена помилка при продажу квитка ID: " + ticketToSell.getId(), exGeneral);
                }
            } else {
                logger.debug("Користувач скасував продаж квитка ID: {}", ticketToSell.getId());
            }
        } else {
            logger.warn("Спроба продати квиток ID: {}, який не має статусу BOOKED. Поточний статус: {}", ticketToSell.getId(), ticketToSell.getStatus());
            JOptionPane.showMessageDialog(this, "Цей квиток не може бути проданий (поточний статус: " + ticketToSell.getStatus().getDisplayName() + ").", "Помилка", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Обробляє дію скасування обраного квитка або бронювання.
     * @param e Об'єкт події {@link ActionEvent}.
     */
    private void cancelTicketAction(ActionEvent e) {
        int selectedRow = bookingsTable.getSelectedRow();
        if (selectedRow == -1) {
            logger.warn("Спроба скасувати квиток, але жоден рядок не вибрано.");
            return;
        }
        int modelRow = bookingsTable.convertRowIndexToModel(selectedRow);
        Ticket ticketToCancel = bookingsTableModel.getTicketAt(modelRow);

        if (ticketToCancel == null) {
            handleGenericException("Не вдалося отримати дані вибраного квитка для скасування.", new IllegalStateException("Selected ticket for cancellation is null at model row " + modelRow));
            return;
        }

        String actionType = ticketToCancel.getStatus() == TicketStatus.BOOKED ? "бронювання" : "квиток";
        logger.info("Спроба скасувати {} ID: {}. Поточний статус: {}",
                actionType, ticketToCancel.getId(), ticketToCancel.getStatus());

        if (ticketToCancel.getStatus() == TicketStatus.BOOKED || ticketToCancel.getStatus() == TicketStatus.SOLD) {
            if (ticketToCancel.getFlight() == null || ticketToCancel.getFlight().getDepartureDateTime() == null) {
                handleGenericException("Помилка даних рейсу. Неможливо перевірити час відправлення.", new IllegalStateException("Flight data incomplete for ticket ID: " + ticketToCancel.getId()));
                return;
            }

            boolean flightDepartedOrArrived = ticketToCancel.getFlight().getStatus() == FlightStatus.DEPARTED ||
                    ticketToCancel.getFlight().getStatus() == FlightStatus.ARRIVED;

            if (flightDepartedOrArrived) {
                logger.warn("Спроба скасувати {} ID: {} на рейс, який вже відбувся/відправлений. Статус рейсу: {}",
                        actionType, ticketToCancel.getId(), ticketToCancel.getFlight().getStatus());
                JOptionPane.showMessageDialog(this, "Неможливо скасувати квиток на рейс, який вже відправлений або прибув.", "Помилка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (ticketToCancel.getFlight().getDepartureDateTime().isBefore(LocalDateTime.now()) &&
                    ticketToCancel.getStatus() == TicketStatus.SOLD) {

                logger.warn("Спроба скасувати проданий квиток ID: {} на рейс, час відправлення якого минув.",
                        ticketToCancel.getId());
                JOptionPane.showMessageDialog(this, "Неможливо скасувати проданий квиток на рейс, час відправлення якого вже минув.", "Помилка", JOptionPane.ERROR_MESSAGE);
                return;
            }


            int confirmation = JOptionPane.showConfirmDialog(this,
                    "Скасувати " + actionType + " ID " + ticketToCancel.getId() + "?",
                    "Підтвердження скасування", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (confirmation == JOptionPane.YES_OPTION) {
                logger.debug("Користувач підтвердив скасування {} ID: {}", actionType, ticketToCancel.getId());
                try {
                    if (ticketDAO.updateTicketStatus(ticketToCancel.getId(), TicketStatus.CANCELLED, null)) {
                        logger.info("{} ID: {} успішно скасовано.", actionType.substring(0, 1).toUpperCase() + actionType.substring(1), ticketToCancel.getId());
                        JOptionPane.showMessageDialog(this, actionType.substring(0, 1).toUpperCase() + actionType.substring(1) + " успішно скасовано.", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                        loadBookingsData((TicketStatus) cmbStatusFilter.getSelectedItem());
                    } else {
                        handleGenericException("Не вдалося скасувати " + actionType + " (операція DAO не вдалася).", new SQLException("DAO returned false for ticket cancellation."));
                    }
                } catch (SQLException ex) {
                    handleSqlException("Помилка БД при скасуванні " + actionType + " ID: " + ticketToCancel.getId(), ex);
                } catch (Exception exGeneral) {
                    handleGenericException("Непередбачена помилка при скасуванні " + actionType + " ID: " + ticketToCancel.getId(), exGeneral);
                }
            } else {
                logger.debug("Користувач скасував операцію скасування {} ID: {}", actionType, ticketToCancel.getId());
            }
        } else {
            logger.warn("Спроба скасувати квиток ID: {}, який не має статусу BOOKED або SOLD. Поточний статус: {}", ticketToCancel.getId(), ticketToCancel.getStatus());
            if (ticketToCancel.getStatus() != null) {
                JOptionPane.showMessageDialog(this, "Цей квиток/бронювання не може бути скасовано (поточний статус: " + ticketToCancel.getStatus().getDisplayName() + ").", "Помилка", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Цей квиток/бронювання не може бути скасовано (статус невідомий).", "Помилка", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void handleSqlException(String userMessage, SQLException e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        if (this.isShowing()) {
            JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("BookingsManagementPanel не видима, JOptionPane для SQLException не буде показано: {}", userMessage);
        }
    }

    private void handleGenericException(String userMessage, Exception e) {
        logger.error("{}: {}", userMessage, e.getMessage(), e);
        if (this.isShowing()) {
            JOptionPane.showMessageDialog(this, userMessage + ":\n" + e.getMessage(), "Внутрішня помилка програми", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.warn("BookingsManagementPanel не видима, JOptionPane для GenericException не буде показано: {}", userMessage);
        }
    }
}
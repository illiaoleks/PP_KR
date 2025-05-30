package UI.Model;

import Models.Flight;
import Models.Route;
import Models.Ticket;
import Models.Enums.TicketStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель таблиці для відображення історії поїздок конкретного пасажира.
 * Цей клас розширює {@link AbstractTableModel} і надає дані для {@link javax.swing.JTable},
 * відображаючи деталі кожного квитка пасажира, такі як ID квитка, інформація про рейс,
 * маршрут, дата відправлення, місце, сплачена ціна та статус квитка.
 *
 * @author [Ваше ім'я або назва команди] // Додайте автора, якщо потрібно
 * @version 1.1 // Версія оновлена для відображення змін
 */
public class PassengerHistoryTableModel extends AbstractTableModel {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private static final DateTimeFormatter HISTORY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Список об'єктів {@link Ticket}, що представляють історію поїздок пасажира.
     */
    private List<Ticket> tickets;
    /**
     * Масив назв стовпців таблиці історії поїздок.
     */
    private final String[] columnNames = {"ID Квитка", "Рейс (ID)", "Маршрут", "Дата відпр.", "Місце", "Ціна", "Статус квитка"};


    /**
     * Конструктор для створення моделі таблиці історії поїздок.
     * Ініціалізує модель наданим списком квитків. Якщо список {@code null},
     * створюється порожній список.
     *
     * @param tickets список об'єктів {@link Ticket}, що становлять історію поїздок.
     */
    public PassengerHistoryTableModel(List<Ticket> tickets) {
        if (tickets == null) {
            logger.debug("Ініціалізація PassengerHistoryTableModel з null списком квитків. Створюється порожній список.");
            this.tickets = new ArrayList<>();
        } else {
            this.tickets = new ArrayList<>(tickets);
            logger.debug("Ініціалізація PassengerHistoryTableModel з {} квитками.", this.tickets.size());
        }
    }

    /**
     * Встановлює новий список квитків для моделі історії поїздок.
     * Оновлює внутрішній список квитків та сповіщає таблицю про зміну даних,
     * що призводить до перемальовування таблиці.
     *
     * @param tickets новий список об'єктів {@link Ticket}.
     */
    public void setTickets(List<Ticket> tickets) {
        if (tickets == null) {
            logger.warn("Спроба встановити null список квитків в PassengerHistoryTableModel. Список буде очищено.");
            this.tickets = new ArrayList<>();
        } else {
            this.tickets = new ArrayList<>(tickets);
            logger.info("Встановлено новий список з {} квитків для історії пасажира.", this.tickets.size());
        }
        logger.debug("Дані таблиці історії пасажира оновлено.");
        fireTableDataChanged();
    }

    /**
     * Повертає кількість рядків у моделі таблиці.
     * Кількість рядків відповідає кількості квитків у списку історії.
     *
     * @return кількість рядків.
     */
    @Override
    public int getRowCount() {
        int count = tickets.size();

        return count;
    }

    /**
     * Повертає кількість стовпців у моделі таблиці.
     * Кількість стовпців визначається масивом {@code columnNames}.
     *
     * @return кількість стовпців.
     */
    @Override
    public int getColumnCount() {

        return columnNames.length;
    }

    /**
     * Повертає назву стовпця за його індексом.
     *
     * @param column індекс стовпця.
     * @return назва стовпця.
     */
    @Override
    public String getColumnName(int column) {
        if (column >= 0 && column < columnNames.length) {
            return columnNames[column];
        }
        logger.warn("Запит назви стовпця для історії пасажира за недійсним індексом: {}", column);
        return "";
    }

    /**
     * Повертає значення для комірки таблиці за вказаними індексами рядка та стовпця.
     * Визначає, які дані з об'єкта {@link Ticket} та пов'язаного з ним {@link Flight}
     * відображати в кожному стовпці. Дата відправлення форматується за допомогою
     * {@link #HISTORY_DATE_FORMATTER}.
     *
     * @param rowIndex індекс рядка.
     * @param columnIndex індекс стовпця.
     * @return об'єкт, що представляє значення комірки.
     *         Повертає {@code "N/A"} або інший плейсхолдер у випадку помилки або відсутності даних.
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

        if (rowIndex < 0 || rowIndex >= tickets.size()) {
            logger.error("Недійсний індекс рядка {} при запиті значення для таблиці історії пасажира. Кількість рядків: {}", rowIndex, tickets.size());
            return "ПОМИЛКА ІНДЕКСУ РЯДКА";
        }
        Ticket ticket = tickets.get(rowIndex);
        if (ticket == null) {
            logger.error("Об'єкт Ticket є null для рядка {} при запиті значення для таблиці історії пасажира.", rowIndex);
            return "ПОМИЛКА: NULL КВИТОК";
        }

        Flight flight = ticket.getFlight();


        if (flight == null && (columnIndex == 1 || columnIndex == 2 || columnIndex == 3)) {
            logger.warn("Об'єкт Flight є null для квитка ID {} (рядок {}). Стовпець: {}. Дані рейсу будуть недоступні.",
                    ticket.getId(), rowIndex, columnIndex);
            return "Рейс N/A";
        }

        try {
            switch (columnIndex) {
                case 0:
                    return ticket.getId();
                case 1:
                    return flight != null ? flight.getId() : "ID Рейсу N/A";
                case 2:
                    if (flight != null) {
                        Route route = flight.getRoute();
                        return (route != null && route.getFullRouteDescription() != null) ? route.getFullRouteDescription() : "Маршрут не вказано";
                    }
                    return "Маршрут N/A";
                case 3:
                    if (flight != null) {
                        LocalDateTime departureDateTime = flight.getDepartureDateTime();
                        return (departureDateTime != null) ? departureDateTime.format(HISTORY_DATE_FORMATTER) : "Дата не вказана";
                    }
                    return "Дата N/A";
                case 4:
                    return ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "Місце не вказано";
                case 5:
                    return ticket.getPricePaid();
                case 6:
                    TicketStatus status = ticket.getStatus();
                    return (status != null && status.getDisplayName() != null) ? status.getDisplayName() : "Статус невідомий";
                default:
                    logger.warn("Запит значення для невідомого індексу стовпця для історії пасажира: {} (рядок {})", columnIndex, rowIndex);
                    return "НЕВІДОМИЙ СТОВПЕЦЬ";
            }
        } catch (Exception e) {
            logger.error("Помилка при отриманні значення для комірки історії пасажира [{}, {}], квиток ID {}", rowIndex, columnIndex, ticket.getId(), e);
            return "ПОМИЛКА ДАНИХ";
        }
    }
}
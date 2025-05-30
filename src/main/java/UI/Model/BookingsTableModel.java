package UI.Model;

import Models.Flight;
import Models.Passenger;
import Models.Route;
import Models.Ticket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Модель таблиці для відображення інформації про квитки (бронювання).
 * Цей клас розширює {@link AbstractTableModel} і надає дані для {@link javax.swing.JTable},
 * відображаючи деталі кожного квитка, такі як ID, рейс, маршрут, пасажир, місце,
 * дати бронювання та продажу, ціну та статус.
 *
 */
public class BookingsTableModel extends AbstractTableModel {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private static final DateTimeFormatter TABLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    /**
     * Список об'єктів {@link Ticket}, що відображаються в таблиці.
     */
    private List<Ticket> tickets;
    /**
     * Масив назв стовпців таблиці.
     */
    private final String[] columnNames = {"ID Квитка", "Рейс (ID)", "Маршрут", "Пасажир", "Місце", "Дата бронюв.", "Дата продажу", "Ціна", "Статус"};


    /**
     * Конструктор для створення моделі таблиці бронювань.
     * Ініціалізує модель наданим списком квитків. Якщо список {@code null},
     * створюється порожній список.
     *
     * @param tickets список об'єктів {@link Ticket} для відображення.
     */
    public BookingsTableModel(List<Ticket> tickets) {
        if (tickets == null) {
            logger.debug("Ініціалізація BookingsTableModel з null списком квитків. Створюється порожній список.");
            this.tickets = new ArrayList<>();
        } else {
            this.tickets = new ArrayList<>(tickets);
            logger.debug("Ініціалізація BookingsTableModel з {} квитками.", this.tickets.size());
        }
        sortTickets();
    }

    /**
     * Встановлює новий список квитків для моделі.
     * Оновлює внутрішній список квитків, сортує їх за датою бронювання
     * (новіші квитки відображаються першими) та сповіщає таблицю про зміну даних.
     *
     * @param tickets новий список об'єктів {@link Ticket}.
     */
    public void setTickets(List<Ticket> tickets) {
        if (tickets == null) {
            logger.warn("Спроба встановити null список квитків в BookingsTableModel. Список буде очищено.");
            this.tickets = new ArrayList<>();
        } else {
            this.tickets = new ArrayList<>(tickets);
            logger.info("Встановлено новий список з {} квитків в BookingsTableModel.", this.tickets.size());
        }
        sortTickets();
        logger.debug("Дані таблиці оновлено та відсортовано.");
        fireTableDataChanged();
    }

    private void sortTickets() {
        if (this.tickets != null) {
            this.tickets.sort(Comparator.comparing(Ticket::getBookingDateTime, Comparator.nullsLast(Comparator.reverseOrder())));
            logger.trace("Квитки відсортовано за датою бронювання (новіші вгорі).");
        }
    }

    /**
     * Повертає об'єкт {@link Ticket} за вказаним індексом рядка.
     *
     * @param rowIndex індекс рядка в таблиці.
     * @return об'єкт {@link Ticket} з відповідного рядка, або {@code null}, якщо індекс виходить за межі.
     */
    public Ticket getTicketAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < tickets.size()) {
            Ticket ticket = tickets.get(rowIndex);
            logger.trace("Отримання квитка за індексом {}: ID {}", rowIndex, ticket.getId());
            return ticket;
        }
        logger.warn("Спроба отримати квиток за недійсним індексом рядка: {}. Розмір списку: {}", rowIndex, tickets.size());
        return null;
    }

    /**
     * Повертає кількість рядків у моделі таблиці.
     * Кількість рядків відповідає кількості квитків у списку.
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
        logger.warn("Запит назви стовпця за недійсним індексом: {}", column);
        return "";
    }

    /**
     * Повертає значення для комірки таблиці за вказаними індексами рядка та стовпця.
     * Визначає, які дані з об'єкта {@link Ticket} (а також пов'язаних об'єктів
     * {@link Flight}, {@link Passenger}, {@link Route}) відображати в кожному стовпці.
     * Дати форматуються за допомогою {@link #TABLE_DATE_FORMATTER}.
     *
     * @param rowIndex індекс рядка.
     * @param columnIndex індекс стовпця.
     * @return об'єкт, що представляє значення комірки.
     *         Повертає {@code "N/A"} або інший плейсхолдер у випадку помилки або відсутності даних.
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= tickets.size()) {
            logger.error("Недійсний індекс рядка {} при запиті значення. Кількість рядків: {}", rowIndex, tickets.size());
            return "ПОМИЛКА ІНДЕКСУ РЯДКА";
        }
        Ticket ticket = tickets.get(rowIndex);
        if (ticket == null) {
            logger.error("Об'єкт Ticket є null для рядка {} при запиті значення.", rowIndex);
            return "ПОМИЛКА: NULL КВИТОК";
        }

        Flight flight = ticket.getFlight();
        Passenger passenger = ticket.getPassenger();

        if (flight == null) {
            logger.warn("Об'єкт Flight є null для квитка ID {} (рядок {}). Стовпець: {}", ticket.getId(), rowIndex, columnIndex);
            if (columnIndex == 0) return ticket.getId();
            if (columnIndex == 4) return ticket.getSeatNumber();
            if (columnIndex == 8 && ticket.getStatus() != null) return ticket.getStatus().getDisplayName();
            return "Рейс N/A";
        }
        if (passenger == null) {
            logger.warn("Об'єкт Passenger є null для квитка ID {} (рядок {}). Стовпець: {}", ticket.getId(), rowIndex, columnIndex);
            if (columnIndex == 0) return ticket.getId();
            if (columnIndex == 4) return ticket.getSeatNumber();
            if (columnIndex == 8 && ticket.getStatus() != null) return ticket.getStatus().getDisplayName();
            return "Пасажир N/A";
        }

        Route route = flight.getRoute();

        try {
            switch (columnIndex) {
                case 0:
                    return ticket.getId();
                case 1:
                    return flight.getId();
                case 2:
                    return route != null && route.getFullRouteDescription() != null ? route.getFullRouteDescription() : "Маршрут не вказано";
                case 3:
                    return passenger.getFullName() != null ? passenger.getFullName() : "Ім'я не вказано";
                case 4:
                    return ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "Місце не вказано";
                case 5:
                    return ticket.getBookingDateTime() != null ? ticket.getBookingDateTime().format(TABLE_DATE_FORMATTER) : "-";
                case 6:
                    return ticket.getPurchaseDateTime() != null ? ticket.getPurchaseDateTime().format(TABLE_DATE_FORMATTER) : "-";
                case 7:
                    return ticket.getPricePaid() != null ? ticket.getPricePaid() : "Ціна не вказана";
                case 8:
                    return ticket.getStatus() != null && ticket.getStatus().getDisplayName() != null ? ticket.getStatus().getDisplayName() : "Статус невідомий";
                default:
                    logger.warn("Запит значення для невідомого індексу стовпця: {} (рядок {})", columnIndex, rowIndex);
                    return "НЕВІДОМИЙ СТОВПЕЦЬ";
            }
        } catch (Exception e) {
            logger.error("Помилка при отриманні значення для комірки [{}, {}], квиток ID {}", rowIndex, columnIndex, ticket.getId(), e);
            return "ПОМИЛКА ДАНИХ";
        }
    }
}
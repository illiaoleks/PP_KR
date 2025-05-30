package UI.Model;

import Models.Flight;
import Models.Route;
import Models.Enums.FlightStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель даних для таблиці рейсів (`JTable`).
 * Відповідає за надання даних таблиці та інформації про її структуру.
 */
public class FlightsTableModel extends AbstractTableModel {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private static final DateTimeFormatter TABLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private List<Flight> flights;
    private final String[] columnNames = {"ID", "Маршрут", "Відправлення", "Прибуття", "Місць", "Автобус", "Ціна", "Статус"};


    public FlightsTableModel(List<Flight> flights) {
        if (flights == null) {
            logger.debug("Ініціалізація FlightsTableModel з null списком рейсів. Створюється порожній список.");
            this.flights = new ArrayList<>();
        } else {
            this.flights = new ArrayList<>(flights);
            logger.debug("Ініціалізація FlightsTableModel з {} рейсами.", this.flights.size());
        }
    }

    /**
     * Оновлює дані в моделі таблиці.
     *
     * @param flights Новий список рейсів.
     */
    public void setFlights(List<Flight> flights) {
        if (flights == null) {
            logger.warn("Спроба встановити null список рейсів в FlightsTableModel. Список буде очищено.");
            this.flights = new ArrayList<>();
        } else {
            this.flights = new ArrayList<>(flights);
            logger.info("Встановлено новий список з {} рейсів в FlightsTableModel.", this.flights.size());
        }
        logger.debug("Дані таблиці рейсів оновлено.");
        fireTableDataChanged();
    }

    /**
     * Повертає об'єкт рейсу за індексом рядка.
     *
     * @param rowIndex Індекс рядка.
     * @return Об'єкт {@link Flight} або {@code null}, якщо індекс недійсний.
     */
    public Flight getFlightAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < flights.size()) {
            Flight flight = flights.get(rowIndex);
            logger.trace("Отримання рейсу за індексом {}: ID {}", rowIndex, flight.getId());
            return flight;
        }
        logger.warn("Спроба отримати рейс за недійсним індексом рядка: {}. Розмір списку: {}", rowIndex, flights.size());
        return null;
    }

    @Override
    public int getRowCount() {
        int count = flights.size();
        return count;
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        if (column >= 0 && column < columnNames.length) {
            return columnNames[column];
        }
        logger.warn("Запит назви стовпця для рейсів за недійсним індексом: {}", column);
        return "";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: // ID
                return Long.class;
            case 4: // Місць
                return Integer.class;
            case 6: // Ціна
                return BigDecimal.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= flights.size()) {
            logger.error("Недійсний індекс рядка {} при запиті значення для таблиці рейсів. Кількість рядків: {}", rowIndex, flights.size());
            return "ПОМИЛКА ІНДЕКСУ РЯДКА";
        }
        Flight flight = flights.get(rowIndex);
        if (flight == null) {
            logger.error("Об'єкт Flight є null для рядка {} при запиті значення для таблиці рейсів.", rowIndex);
            return "ПОМИЛКА: NULL РЕЙС";
        }

        try {
            switch (columnIndex) {
                case 0: // ID
                    return flight.getId();
                case 1: // Маршрут
                    Route route = flight.getRoute();
                    return (route != null && route.getFullRouteDescription() != null) ? route.getFullRouteDescription() : "Маршрут не вказано";
                case 2: // Відправлення
                    return (flight.getDepartureDateTime() != null) ? flight.getDepartureDateTime().format(TABLE_DATE_TIME_FORMATTER) : "Дата не вказана";
                case 3: // Прибуття
                    return (flight.getArrivalDateTime() != null) ? flight.getArrivalDateTime().format(TABLE_DATE_TIME_FORMATTER) : "Дата не вказана";
                case 4: // Місць
                    return flight.getTotalSeats();
                case 5: // Автобус
                    return (flight.getBusModel() != null && !flight.getBusModel().isEmpty()) ? flight.getBusModel() : "-";
                case 6: // Ціна
                    return flight.getPricePerSeat();
                case 7: // Статус
                    FlightStatus status = flight.getStatus();
                    return (status != null && status.getDisplayName() != null) ? status.getDisplayName() : "Статус невідомий";
                default:
                    logger.warn("Запит значення для невідомого індексу стовпця для рейсів: {} (рядок {})", columnIndex, rowIndex);
                    return "НЕВІДОМИЙ СТОВПЕЦЬ";
            }
        } catch (Exception e) {
            logger.error("Помилка при отриманні значення для комірки рейсів [{}, {}], рейс ID {}", rowIndex, columnIndex, flight.getId(), e);
            return "ПОМИЛКА ДАНИХ";
        }
    }
}
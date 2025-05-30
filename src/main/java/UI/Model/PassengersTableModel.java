package UI.Model;

import Models.Passenger;
import Models.Enums.BenefitType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель таблиці для відображення інформації про пасажирів.
 * Цей клас розширює {@link AbstractTableModel} і надає дані для {@link javax.swing.JTable},
 * відображаючи деталі кожного пасажира, такі як ID, ПІБ, інформація про документ,
 * контактні дані та тип пільги.
 *
 * @author [Ваше ім'я або назва команди] // Додайте автора, якщо потрібно
 * @version 1.1 // Версія оновлена для відображення змін
 */
public class PassengersTableModel extends AbstractTableModel {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    /**
     * Список об'єктів {@link Passenger}, що відображаються в таблиці.
     */
    private List<Passenger> passengers;
    /**
     * Масив назв стовпців таблиці пасажирів.
     */
    private final String[] columnNames = {"ID", "ПІБ", "Документ", "Номер документа", "Телефон", "Email", "Пільга"};

    /**
     * Конструктор для створення моделі таблиці пасажирів.
     * Ініціалізує модель наданим списком пасажирів. Якщо список {@code null},
     * створюється порожній список.
     *
     * @param passengers список об'єктів {@link Passenger} для відображення.
     */
    public PassengersTableModel(List<Passenger> passengers) {
        if (passengers == null) {
            logger.debug("Ініціалізація PassengersTableModel з null списком пасажирів. Створюється порожній список.");
            this.passengers = new ArrayList<>();
        } else {
            this.passengers = new ArrayList<>(passengers);
            logger.debug("Ініціалізація PassengersTableModel з {} пасажирами.", this.passengers.size());
        }
    }

    /**
     * Встановлює новий список пасажирів для моделі.
     * Оновлює внутрішній список пасажирів та сповіщає таблицю про зміну даних,
     * що призводить до перемальовування таблиці.
     *
     * @param passengers новий список об'єктів {@link Passenger}.
     */
    public void setPassengers(List<Passenger> passengers) {
        if (passengers == null) {
            logger.warn("Спроба встановити null список пасажирів в PassengersTableModel. Список буде очищено.");
            this.passengers = new ArrayList<>();
        } else {
            this.passengers = new ArrayList<>(passengers);
            logger.info("Встановлено новий список з {} пасажирами.", this.passengers.size());
        }
        logger.debug("Дані таблиці пасажирів оновлено.");
        fireTableDataChanged();
    }

    /**
     * Повертає об'єкт {@link Passenger} за вказаним індексом рядка.
     *
     * @param rowIndex індекс рядка в таблиці.
     * @return об'єкт {@link Passenger} з відповідного рядка, або {@code null}, якщо індекс виходить за межі.
     */
    public Passenger getPassengerAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < passengers.size()) {
            Passenger passenger = passengers.get(rowIndex);
            logger.trace("Отримання пасажира за індексом {}: ID {}", rowIndex, (passenger != null ? passenger.getId() : "null"));
            return passenger;
        }
        logger.warn("Спроба отримати пасажира за недійсним індексом рядка: {}. Розмір списку: {}", rowIndex, passengers.size());
        return null;
    }

    /**
     * Повертає кількість рядків у моделі таблиці.
     * Кількість рядків відповідає кількості пасажирів у списку.
     *
     * @return кількість рядків.
     */
    @Override
    public int getRowCount() {
        int count = passengers.size();

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
        logger.warn("Запит назви стовпця для таблиці пасажирів за недійсним індексом: {}", column);
        return "";
    }

    /**
     * Повертає значення для комірки таблиці за вказаними індексами рядка та стовпця.
     * Визначає, які дані з об'єкта {@link Passenger} відображати в кожному стовпці.
     *
     * @param rowIndex індекс рядка.
     * @param columnIndex індекс стовпця.
     * @return об'єкт, що представляє значення комірки.
     *         Повертає {@code "N/A"} або інший плейсхолдер у випадку помилки або відсутності даних.
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

        if (rowIndex < 0 || rowIndex >= passengers.size()) {
            logger.error("Недійсний індекс рядка {} при запиті значення для таблиці пасажирів. Кількість рядків: {}", rowIndex, passengers.size());
            return "ПОМИЛКА ІНДЕКСУ РЯДКА";
        }
        Passenger p = passengers.get(rowIndex);
        if (p == null) {
            logger.error("Об'єкт Passenger є null для рядка {} при запиті значення для таблиці пасажирів.", rowIndex);
            return "ПОМИЛКА: NULL ПАСАЖИР";
        }

        try {
            switch (columnIndex) {
                case 0: // ID
                    return p.getId();
                case 1: // ПІБ
                    return p.getFullName() != null ? p.getFullName() : "ПІБ не вказано";
                case 2: // Документ (тип)
                    return p.getDocumentType() != null ? p.getDocumentType() : "Тип не вказано";
                case 3: // Номер документа
                    return p.getDocumentNumber() != null ? p.getDocumentNumber() : "Номер не вказано";
                case 4: // Телефон
                    return p.getPhoneNumber() != null ? p.getPhoneNumber() : "Телефон не вказано";
                case 5: // Email
                    return p.getEmail() != null ? p.getEmail() : "-"; // Відображаємо "-" якщо email відсутній
                case 6: // Пільга
                    BenefitType benefitType = p.getBenefitType();
                    return (benefitType != null && benefitType.getDisplayName() != null) ? benefitType.getDisplayName() : "Без пільг";
                default:
                    logger.warn("Запит значення для невідомого індексу стовпця для пасажирів: {} (рядок {})", columnIndex, rowIndex);
                    return "НЕВІДОМИЙ СТОВПЕЦЬ";
            }
        } catch (Exception e) {
            logger.error("Помилка при отриманні значення для комірки пасажирів [{}, {}], пасажир ID {}", rowIndex, columnIndex, p.getId(), e);
            return "ПОМИЛКА ДАНИХ";
        }
    }
}
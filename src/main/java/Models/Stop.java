package Models;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Клас, що представляє зупинку в маршруті.
 * Кожна зупинка характеризується унікальним ідентифікатором, назвою (наприклад, назва автостанції або конкретного місця зупинки)
 * та містом, в якому вона розташована.
 *
 */
public class Stop {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private long id;
    private String name;
    private String city;

    /**
     * Конструктор для створення об'єкта Зупинка.
     *
     * @param id унікальний ідентифікатор зупинки.
     * @param name назва зупинки.
     * @param city місто, в якому розташована зупинка.
     */
    public Stop(long id, String name, String city) {
        logger.debug("Спроба створити новий об'єкт Stop з ID: {}", id);

        if (name == null || name.trim().isEmpty()) {
            logger.error("Помилка створення Stop: Назва зупинки (name) не може бути порожньою для ID: {}", id);
            throw new IllegalArgumentException("Назва зупинки не може бути порожньою.");
        }
        if (city == null || city.trim().isEmpty()) {
            logger.error("Помилка створення Stop: Місто (city) не може бути порожнім для ID: {}", id);
            throw new IllegalArgumentException("Місто не може бути порожнім.");
        }

        this.id = id;
        this.name = name;
        this.city = city;
        logger.info("Об'єкт Stop успішно створено: ID={}, Назва={}, Місто={}", this.id, this.name, this.city);
    }

    /**
     * Повертає унікальний ідентифікатор зупинки.
     * @return {@code long} значення ідентифікатора.
     */
    public long getId() {
        return id;
    }

    /**
     * Встановлює унікальний ідентифікатор зупинки.
     * @param id новий ідентифікатор зупинки.
     */
    public void setId(long id) {
        logger.trace("Встановлення ID зупинки {} на: {}", this.id, id);
        this.id = id;
    }

    /**
     * Повертає назву зупинки.
     * @return {@code String} назва зупинки.
     */
    public String getName() {
        return name;
    }



    /**
     * Повертає місто, в якому знаходиться зупинка.
     * @return {@code String} назва міста.
     */
    public String getCity() {
        return city;
    }

    /**
     * Встановлює місто, в якому знаходиться зупинка.
     * @param city нова назва міста.
     */
    public void setCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            logger.warn("Спроба встановити порожнє місто для зупинки ID: {}", this.id);
        }
        logger.trace("Зміна міста зупинки ID {}.", this.id);
        this.city = city;
    }

    /**
     * Повертає рядкове представлення об'єкта {@code Stop}.
     * Формат: "ID: [id], Назва: [НазваЗупинки], Місто: [Місто]".
     * @return {@code String} рядкове представлення зупинки.
     */
    @Override
    public String toString() {
        return String.format("ID: %d, Назва: %s, Місто: %s",
                id,
                name != null ? name : "н/д",
                city != null ? city : "н/д");
    }

    /**
     * Порівнює поточний об'єкт {@code Stop} з іншим об'єктом.
     * Дві зупинки вважаються рівними, якщо їхні ідентифікатори ({@code id}) однакові.
     *
     * @param o об'єкт для порівняння.
     * @return {@code true}, якщо об'єкти рівні (мають однаковий {@code id}),
     *         {@code false} в іншому випадку.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stop stop = (Stop) o;
        return id == stop.id;
    }

    /**
     * Повертає хеш-код для об'єкта {@code Stop}.
     * Хеш-код базується на ідентифікаторі ({@code id}) зупинки.
     *
     * @return {@code int} хеш-код об'єкта.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
package Models;

import Models.Enums.FlightStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Клас, що представляє один конкретний рейс (поїздку) автобуса.
 * <p>
 * Ця модель містить детальну інформацію про рейс, включаючи його унікальний ідентифікатор,
 * маршрут, дати та час відправлення й прибуття, загальну кількість місць в автобусі,
 * поточний статус рейсу, модель автобуса та базову ціну за місце.
 * </p>
 * <p>
 * Важливо зазначити, що ця модель представляє <strong>окремий екземпляр рейсу</strong>
 * на конкретну дату та час. Періодичність або регулярність рейсів (наприклад, щоденні рейси
 * за певним маршрутом) реалізується на рівні сервісів, які генерують
 * екземпляри цього класу {@code Flight} для кожної конкретної поїздки.
 * Кількість доступних місць для бронювання зазвичай розраховується динамічно
 * або управляється відповідними сервісами, а не зберігається безпосередньо в цій моделі
 * як окреме поле.
 * </p>
 *
 * @see Route
 * @see Models.Enums.FlightStatus
 */
public class Flight {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private long id;
    private Route route;
    private LocalDateTime departureDateTime;
    private LocalDateTime arrivalDateTime;
    private int totalSeats;
    private FlightStatus status;
    private String busModel;
    private BigDecimal pricePerSeat;

    /**
     * Конструктор для створення нового екземпляра рейсу.
     *
     * @param id Унікальний ідентифікатор рейсу.
     * @param route Об'єкт {@link Route}, що представляє маршрут рейсу.
     * @param departureDateTime Дата та час відправлення.
     * @param arrivalDateTime Дата та час прибуття.
     * @param totalSeats Загальна кількість місць в автобусі.
     * @param status Поточний статус рейсу (об'єкт {@link Models.Enums.FlightStatus}).
     * @param busModel Модель автобуса (може бути null або порожнім рядком, якщо невідома).
     * @param pricePerSeat Базова ціна за одне місце.
     */
    public Flight(long id, Route route, LocalDateTime departureDateTime, LocalDateTime arrivalDateTime,
                  int totalSeats, FlightStatus status, String busModel, BigDecimal pricePerSeat) {
        logger.debug("Спроба створити новий об'єкт Flight з ID: {}", id);

        if (route == null) {
            logger.error("Помилка створення Flight: Маршрут (route) не може бути null для ID: {}", id);
            throw new IllegalArgumentException("Маршрут (route) не може бути null");
        }
        if (departureDateTime == null) {
            logger.error("Помилка створення Flight: Дата та час відправлення (departureDateTime) не можуть бути null для ID: {}", id);
            throw new IllegalArgumentException("Дата та час відправлення (departureDateTime) не можуть бути null");
        }
        if (arrivalDateTime == null) {
            logger.error("Помилка створення Flight: Дата та час прибуття (arrivalDateTime) не можуть бути null для ID: {}", id);
            throw new IllegalArgumentException("Дата та час прибуття (arrivalDateTime) не можуть бути null");
        }
        if (status == null) {
            logger.error("Помилка створення Flight: Статус рейсу (status) не може бути null для ID: {}", id);
            throw new IllegalArgumentException("Статус рейсу (status) не може бути null");
        }
        if (pricePerSeat == null || pricePerSeat.compareTo(BigDecimal.ZERO) < 0) {
            logger.error("Помилка створення Flight: Ціна за місце (pricePerSeat) не може бути null або від'ємною ({}) для ID: {}", pricePerSeat, id);
            throw new IllegalArgumentException("Ціна за місце (pricePerSeat) не може бути null або від'ємною");
        }
        if (totalSeats <= 0) {
            logger.error("Помилка створення Flight: Загальна кількість місць (totalSeats) має бути позитивним числом ({}) для ID: {}", totalSeats, id);
            throw new IllegalArgumentException("Загальна кількість місць (totalSeats) має бути позитивним числом");
        }
        if (departureDateTime.isAfter(arrivalDateTime)) {
            logger.warn("Увага при створенні Flight (ID: {}): Дата відправлення ({}) пізніше за дату прибуття ({}).", id, departureDateTime, arrivalDateTime);
        }

        this.id = id;
        this.route = route;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.totalSeats = totalSeats;
        this.status = status;
        this.busModel = busModel;
        this.pricePerSeat = pricePerSeat;
        logger.info("Об'єкт Flight успішно створено: ID={}", this.id);
    }

    /**
     * Повертає унікальний ідентифікатор рейсу.
     * @return ID рейсу.
     */
    public long getId() {
        return id;
    }

    /**
     * Встановлює унікальний ідентифікатор рейсу.
     * @param id Новий ID рейсу.
     */
    public void setId(long id) {
        logger.trace("Встановлення ID рейсу {} на: {}", this.id, id);
        this.id = id;
    }

    /**
     * Повертає маршрут рейсу.
     * @return Об'єкт {@link Route}, що описує маршрут.
     */
    public Route getRoute() {
        return this.route;
    }

    /**
     * Встановлює маршрут для рейсу.
     * @param route Новий об'єкт {@link Route}. Не може бути {@code null}.
     * @throws IllegalArgumentException якщо {@code route} є {@code null}.
     */
    public void setRoute(Route route) {
        if (route == null) {
            logger.error("Спроба встановити null маршрут для рейсу ID: {}", this.id);
            throw new IllegalArgumentException("Маршрут (route) не може бути null");
        }
        logger.trace("Зміна маршруту для рейсу ID {}.", this.id);
        this.route = route;
    }

    /**
     * Повертає дату та час відправлення рейсу.
     * @return Дата та час відправлення.
     */
    public LocalDateTime getDepartureDateTime() {
        return departureDateTime;
    }

    /**
     * Встановлює дату та час відправлення рейсу.
     * @param departureDateTime Нова дата та час відправлення. Не може бути {@code null}.
     * @throws IllegalArgumentException якщо {@code departureDateTime} є {@code null}.
     */
    public void setDepartureDateTime(LocalDateTime departureDateTime) {
        if (departureDateTime == null) {
            logger.error("Спроба встановити null дату відправлення для рейсу ID: {}", this.id);
            throw new IllegalArgumentException("Дата та час відправлення (departureDateTime) не можуть бути null");
        }
        if (this.arrivalDateTime != null && departureDateTime.isAfter(this.arrivalDateTime)) {
            logger.warn("Увага при зміні дати відправлення рейсу ID {}: Нова дата відправлення ({}) пізніше за поточну дату прибуття ({}).", this.id, departureDateTime, this.arrivalDateTime);
        }
        logger.trace("Зміна дати відправлення для рейсу ID {}.", this.id);
        this.departureDateTime = departureDateTime;
    }

    /**
     * Повертає очікувану дату та час прибуття рейсу.
     * @return Дата та час прибуття.
     */
    public LocalDateTime getArrivalDateTime() {
        return arrivalDateTime;
    }

    /**
     * Встановлює очікувану дату та час прибуття рейсу.
     * @param arrivalDateTime Нова дата та час прибуття. Не може бути {@code null}.
     * @throws IllegalArgumentException якщо {@code arrivalDateTime} є {@code null}.
     */
    public void setArrivalDateTime(LocalDateTime arrivalDateTime) {
        if (arrivalDateTime == null) {
            logger.error("Спроба встановити null дату прибуття для рейсу ID: {}", this.id);
            throw new IllegalArgumentException("Дата та час прибуття (arrivalDateTime) не можуть бути null");
        }
        if (this.departureDateTime != null && this.departureDateTime.isAfter(arrivalDateTime)) {
            logger.warn("Увага при зміні дати прибуття рейсу ID {}: Нова дата прибуття ({}) раніше за поточну дату відправлення ({}).", this.id, arrivalDateTime, this.departureDateTime);
        }
        logger.trace("Зміна дати прибуття для рейсу ID {}.", this.id);
        this.arrivalDateTime = arrivalDateTime;
    }

    /**
     * Повертає загальну кількість місць в автобусі.
     * @return Загальна кількість місць.
     */
    public int getTotalSeats() {
        return totalSeats;
    }

    /**
     * Встановлює загальну кількість місць в автобусі.
     * @param totalSeats Нова загальна кількість місць. Має бути позитивним числом.
     * @throws IllegalArgumentException якщо {@code totalSeats} не є позитивним числом.
     */
    public void setTotalSeats(int totalSeats) {
        if (totalSeats <= 0) {
            logger.error("Спроба встановити непозитивну кількість місць ({}) для рейсу ID: {}", totalSeats, this.id);
            throw new IllegalArgumentException("Загальна кількість місць (totalSeats) має бути позитивним числом");
        }
        logger.trace("Зміна кількості місць для рейсу ID {}.", this.id);
        this.totalSeats = totalSeats;
    }

    /**
     * Повертає поточний статус рейсу.
     * @return Статус рейсу як об'єкт {@link Models.Enums.FlightStatus}.
     */
    public FlightStatus getStatus() {
        return status;
    }

    /**
     * Встановлює поточний статус рейсу.
     * @param status Новий статус рейсу (об'єкт {@link Models.Enums.FlightStatus}). Не може бути {@code null}.
     * @throws IllegalArgumentException якщо {@code status} є {@code null}.
     */
    public void setStatus(FlightStatus status) {
        if (status == null) {
            logger.error("Спроба встановити null статус для рейсу ID: {}", this.id);
            throw new IllegalArgumentException("Статус рейсу (status) не може бути null");
        }
        logger.info("Зміна статусу для рейсу ID {}: з {} на {}", this.id, this.status, status);
        this.status = status;
    }

    /**
     * Повертає модель автобуса, що обслуговує рейс.
     * @return Рядок з назвою моделі автобуса, або {@code null}, якщо не вказано.
     */
    public String getBusModel() {
        return busModel;
    }

    /**
     * Встановлює модель автобуса для рейсу.
     * @param busModel Назва моделі автобуса. Може бути {@code null} або порожнім рядком.
     */
    public void setBusModel(String busModel) {
        logger.trace("Зміна моделі автобуса для рейсу ID {}.", this.id);
        this.busModel = busModel;
    }

    /**
     * Повертає базову ціну за одне місце на рейсі.
     * @return Ціна за місце типу {@link BigDecimal}.
     */
    public BigDecimal getPricePerSeat() {
        return pricePerSeat;
    }

    /**
     * Встановлює базову ціну за одне місце на рейсі.
     * @param pricePerSeat Нова ціна за місце типу {@link BigDecimal}. Не може бути {@code null} або від'ємною.
     * @throws IllegalArgumentException якщо {@code pricePerSeat} є {@code null} або від'ємною.
     */
    public void setPricePerSeat(BigDecimal pricePerSeat) {
        if (pricePerSeat == null || pricePerSeat.compareTo(BigDecimal.ZERO) < 0) {
            logger.error("Спроба встановити некоректну ціну ({}) для рейсу ID: {}", pricePerSeat, this.id);
            throw new IllegalArgumentException("Ціна за місце (pricePerSeat) не може бути null або від'ємною");
        }
        logger.trace("Зміна ціни за місце для рейсу ID {}.", this.id);
        this.pricePerSeat = pricePerSeat;
    }

    /**
     * Повертає рядкове представлення об'єкта {@code Flight}.
     * Формат: "Рейс [id]: [опис маршруту], Відправлення: [дата/час відправлення], Статус: [відображуване ім'я статусу]".
     *
     * @return Рядок, що описує рейс.
     */
    @Override
    public String toString() {
        String routeDescription = (route != null && route.getFullRouteDescription() != null) ? route.getFullRouteDescription() : "Маршрут не вказано";
        String statusDisplay = (status != null && status.getDisplayName() != null) ? status.getDisplayName() : "Статус невідомий";
        String departureDisplay = (departureDateTime != null) ? departureDateTime.toString() : "Час відправлення не вказано";
        String arrivalDisplay = (arrivalDateTime != null) ? arrivalDateTime.toString() : "Час прибуття не вказано";
        String priceDisplay = (pricePerSeat != null) ? pricePerSeat.toString() : "Ціна не вказана";

        return "Рейс " + id + ": " + routeDescription +
                ", Відправлення: " + departureDisplay +
                ", Прибуття: " + arrivalDisplay +
                ", Місць: " + totalSeats +
                (busModel != null && !busModel.isEmpty() ? ", Автобус: " + busModel : "") +
                ", Ціна: " + priceDisplay +
                ", Статус: " + statusDisplay;
    }

    /**
     * Порівнює цей об'єкт {@code Flight} з іншим об'єктом на рівність.
     * Два рейси вважаються рівними, якщо їхні ідентифікатори ({@code id}) однакові.
     * Інші поля не враховуються для визначення рівності, оскільки {@code id}
     * передбачається унікальним для кожного екземпляра рейсу.
     *
     * @param o Об'єкт для порівняння.
     * @return {@code true}, якщо об'єкти рівні (мають однаковий ID), {@code false} в іншому випадку.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Flight flight = (Flight) o;
        return id == flight.id;
    }

    /**
     * Повертає хеш-код для об'єкта {@code Flight}.
     * Хеш-код генерується на основі ідентифікатора рейсу ({@code id}).
     *
     * @return Хеш-код об'єкта.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
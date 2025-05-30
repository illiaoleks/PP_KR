package Models;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Клас, що представляє маршрут рейсу.
 * Маршрут складається з пункту відправлення, пункту призначення та списку проміжних зупинок.
 *
 */
public class Route {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private long id;
    private Stop departureStop;
    private Stop destinationStop;
    private List<Stop> intermediateStops;

    /**
     * Конструктор для створення об'єкта Маршрут.
     *
     * @param id унікальний ідентифікатор маршруту.
     * @param departureStop зупинка відправлення.
     * @param destinationStop зупинка призначення.
     * @param intermediateStops список проміжних зупинок (може бути {@code null} або порожнім, буде ініціалізовано порожнім списком).
     */
    public Route(long id, Stop departureStop, Stop destinationStop, List<Stop> intermediateStops) {
        logger.debug("Спроба створити новий об'єкт Route з ID: {}", id);

        if (departureStop == null) {
            logger.error("Помилка створення Route: Зупинка відправлення (departureStop) не може бути null для ID: {}", id);
            throw new IllegalArgumentException("Зупинка відправлення не може бути null.");
        }
        if (destinationStop == null) {
            logger.error("Помилка створення Route: Зупинка призначення (destinationStop) не може бути null для ID: {}", id);
            throw new IllegalArgumentException("Зупинка призначення не може бути null.");
        }
        if (departureStop.equals(destinationStop)) {
            logger.warn("Увага при створенні Route (ID: {}): Зупинка відправлення та зупинка призначення однакові.", id);
        }

        this.id = id;
        this.departureStop = departureStop;
        this.destinationStop = destinationStop;
        if (intermediateStops == null) {
            logger.trace("Для Route ID: {} список проміжних зупинок був null, ініціалізовано порожнім списком.", id);
            this.intermediateStops = new ArrayList<>();
        } else {
            this.intermediateStops = new ArrayList<>(intermediateStops);
            logger.trace("Для Route ID: {} встановлено {} проміжних зупинок.", id, this.intermediateStops.size());
        }
        logger.info("Об'єкт Route успішно створено: ID={}", this.id);
    }

    /**
     * Повертає унікальний ідентифікатор маршруту.
     * @return {@code long} значення ідентифікатора.
     */
    public long getId() {
        return id;
    }

    /**
     * Встановлює унікальний ідентифікатор маршруту.
     * @param id новий ідентифікатор маршруту.
     */
    public void setId(long id) {
        logger.trace("Встановлення ID маршруту {} на: {}", this.id, id);
        this.id = id;
    }

    /**
     * Повертає зупинку відправлення маршруту.
     * @return {@link Stop} об'єкт зупинки відправлення.
     */
    public Stop getDepartureStop() {
        return departureStop;
    }

    /**
     * Встановлює зупинку відправлення маршруту.
     * @param departureStop нова зупинка відправлення.
     */
    public void setDepartureStop(Stop departureStop) {
        if (departureStop == null) {
            logger.error("Спроба встановити null зупинку відправлення для маршруту ID: {}", this.id);
            throw new IllegalArgumentException("Зупинка відправлення не може бути null.");
        }
        if (this.destinationStop != null && departureStop.equals(this.destinationStop)) {
            logger.warn("Увага при зміні зупинки відправлення маршруту ID {}: Нова зупинка відправлення збігається з поточною зупинкою призначення.", this.id);
        }
        logger.trace("Зміна зупинки відправлення для маршруту ID {}.", this.id);
        this.departureStop = departureStop;
    }

    /**
     * Повертає зупинку призначення маршруту.
     * @return {@link Stop} об'єкт зупинки призначення.
     */
    public Stop getDestinationStop() {
        return destinationStop;
    }

    /**
     * Встановлює зупинку призначення маршруту.
     * @param destinationStop нова зупинка призначення.
     */
    public void setDestinationStop(Stop destinationStop) {
        if (destinationStop == null) {
            logger.error("Спроба встановити null зупинку призначення для маршруту ID: {}", this.id);
            throw new IllegalArgumentException("Зупинка призначення не може бути null.");
        }
        if (this.departureStop != null && destinationStop.equals(this.departureStop)) {
            logger.warn("Увага при зміні зупинки призначення маршруту ID {}: Нова зупинка призначення збігається з поточною зупинкою відправлення.", this.id);
        }
        logger.trace("Зміна зупинки призначення для маршруту ID {}.", this.id);
        this.destinationStop = destinationStop;
    }

    /**
     * Повертає список проміжних зупинок на маршруті.
     * Повертає копію списку, щоб запобігти зовнішній модифікації.
     * @return {@code List<Stop>} список проміжних зупинок. Може бути порожнім.
     */
    public List<Stop> getIntermediateStops() {
        return intermediateStops != null ? new ArrayList<>(intermediateStops) : new ArrayList<>();
    }

    /**
     * Встановлює список проміжних зупинок на маршруті.
     * @param intermediateStops новий список проміжних зупинок (може бути {@code null} або порожнім, буде ініціалізовано порожнім списком або копією).
     */
    public void setIntermediateStops(List<Stop> intermediateStops) {
        int oldSize = (this.intermediateStops != null) ? this.intermediateStops.size() : 0;
        if (intermediateStops == null) {
            this.intermediateStops = new ArrayList<>();
            logger.trace("Список проміжних зупинок для маршруту ID {} очищено (був null).", this.id);
        } else {
            this.intermediateStops = new ArrayList<>(intermediateStops);
            logger.trace("Зміна списку проміжних зупинок для маршруту ID {}. Старий розмір: {}, Новий розмір: {}.",
                    this.id, oldSize, this.intermediateStops.size());
        }
    }

    /**
     * Повертає повний опис маршруту у вигляді рядка.
     * Формат: "МістоВідправлення -> МістоПроміжноїЗупинки1 -> ... -> МістоПризначення".
     * Обробляє випадки, коли зупинки або їх міста можуть бути null.
     *
     * @return {@code String} рядок, що описує маршрут.
     */
    public String getFullRouteDescription() {
        StringBuilder sb = new StringBuilder();
        String depCity = (departureStop != null && departureStop.getCity() != null) ? departureStop.getCity() : "Невідомо";
        sb.append(depCity);

        if (intermediateStops != null && !intermediateStops.isEmpty()) {
            for (Stop stop : intermediateStops) {
                String interCity = (stop != null && stop.getCity() != null) ? stop.getCity() : "Невідомо";
                sb.append(" -> ").append(interCity);
            }
        }

        String destCity = (destinationStop != null && destinationStop.getCity() != null) ? destinationStop.getCity() : "Невідомо";
        sb.append(" -> ").append(destCity);
        return sb.toString();
    }

    /**
     * Повертає рядкове представлення об'єкта {@code Route}.
     * Формат: "Маршрут ID [id]: [Повний опис маршруту]".
     * @return {@code String} рядкове представлення маршруту.
     */
    @Override
    public String toString() {
        return "Маршрут ID " + id + ": " + getFullRouteDescription();
    }

    /**
     * Порівнює поточний об'єкт {@code Route} з іншим об'єктом.
     * Два маршрути вважаються рівними, якщо їхні ідентифікатори ({@code id}) однакові.
     *
     * @param o об'єкт для порівняння.
     * @return {@code true}, якщо об'єкти рівні (мають однаковий {@code id}),
     *         {@code false} в іншому випадку.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Route route = (Route) o;
        return id == route.id;
    }

    /**
     * Повертає хеш-код для об'єкта {@code Route}.
     * Хеш-код базується на ідентифікаторі ({@code id}) маршруту.
     *
     * @return {@code int} хеш-код об'єкта.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
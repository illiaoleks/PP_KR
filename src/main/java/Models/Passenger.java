package Models;

import Models.Enums.BenefitType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Клас, що представляє пасажира.
 * Містить персональні дані пасажира та інформацію про пільги.
 * Історія поїздок буде реалізована через запити до квитків.
 *
 * @version 1.1
 */
public class Passenger {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private long id;
    private String fullName;
    private String documentNumber;
    private String documentType;
    private String phoneNumber;
    private String email;
    private BenefitType benefitType;

    /**
     * Конструктор для створення об'єкта Пасажир з усіма необхідними даними.
     *
     * @param id унікальний ідентифікатор пасажира.
     * @param fullName повне ім'я та прізвище пасажира.
     * @param documentNumber номер документа, що посвідчує особу.
     * @param documentType тип документа, що посвідчує особу.
     * @param phoneNumber номер телефону пасажира.
     * @param email адреса електронної пошти пасажира (може бути {@code null}).
     * @param benefitType тип пільги, якою користується пасажир.
     */
    public Passenger(long id, String fullName, String documentNumber, String documentType,
                     String phoneNumber, String email, BenefitType benefitType) {
        logger.debug("Спроба створити новий об'єкт Passenger з ID: {}", id);

        if (fullName == null || fullName.trim().isEmpty()) {
            logger.error("Помилка створення Passenger: Повне ім'я (fullName) не може бути порожнім для ID: {}", id);
            throw new IllegalArgumentException("Повне ім'я не може бути порожнім.");
        }
        if (documentNumber == null || documentNumber.trim().isEmpty()) {
            logger.error("Помилка створення Passenger: Номер документа (documentNumber) не може бути порожнім для ID: {}", id);
            throw new IllegalArgumentException("Номер документа не може бути порожнім.");
        }
        if (documentType == null || documentType.trim().isEmpty()) {
            logger.error("Помилка створення Passenger: Тип документа (documentType) не може бути порожнім для ID: {}", id);
            throw new IllegalArgumentException("Тип документа не може бути порожнім.");
        }
        if (benefitType == null) {
            logger.warn("Увага при створенні Passenger (ID: {}): Тип пільги (benefitType) є null. Буде встановлено NONE.", id);
            this.benefitType = BenefitType.NONE;
        } else {
            this.benefitType = benefitType;
        }

        this.id = id;
        this.fullName = fullName;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.phoneNumber = phoneNumber;
        this.email = email;

        logger.info("Об'єкт Passenger успішно створено: ID={}", this.id);
    }

    /**
     * Повертає унікальний ідентифікатор пасажира.
     * @return {@code long} значення ідентифікатора.
     */
    public long getId() {
        return id;
    }

    /**
     * Встановлює унікальний ідентифікатор пасажира.
     * @param id новий ідентифікатор пасажира.
     */
    public void setId(long id) {
        logger.trace("Встановлення ID пасажира {} на: {}", this.id, id);
        this.id = id;
    }

    /**
     * Повертає повне ім'я та прізвище пасажира.
     * @return {@code String} ПІБ пасажира.
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Встановлює повне ім'я та прізвище пасажира.
     * @param fullName нове ПІБ пасажира.
     */
    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            logger.warn("Спроба встановити порожнє повне ім'я для пасажира ID: {}", this.id);
        }
        logger.trace("Зміна повного імені для пасажира ID {}.", this.id);
        this.fullName = fullName;
    }

    /**
     * Повертає номер документа пасажира.
     * @return {@code String} номер документа.
     */
    public String getDocumentNumber() {
        return documentNumber;
    }

    /**
     * Встановлює номер документа пасажира.
     * @param documentNumber новий номер документа.
     */
    public void setDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.trim().isEmpty()) {
            logger.warn("Спроба встановити порожній номер документа для пасажира ID: {}", this.id);
        }
        logger.trace("Зміна номера документа для пасажира ID {}.", this.id);
        this.documentNumber = documentNumber;
    }

    /**
     * Повертає тип документа пасажира.
     * @return {@code String} тип документа.
     */
    public String getDocumentType() {
        return documentType;
    }

    /**
     * Встановлює тип документа пасажира.
     * @param documentType новий тип документа.
     */
    public void setDocumentType(String documentType) {
        if (documentType == null || documentType.trim().isEmpty()) {
            logger.warn("Спроба встановити порожній тип документа для пасажира ID: {}", this.id);
        }
        logger.trace("Зміна типу документа для пасажира ID {}.", this.id);
        this.documentType = documentType;
    }

    /**
     * Повертає номер телефону пасажира.
     * @return {@code String} номер телефону.
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Встановлює номер телефону пасажира.
     * @param phoneNumber новий номер телефону.
     */
    public void setPhoneNumber(String phoneNumber) {
        logger.trace("Зміна номера телефону для пасажира ID {}.", this.id);
        this.phoneNumber = phoneNumber;
    }

    /**
     * Повертає адресу електронної пошти пасажира.
     * @return {@code String} адреса електронної пошти, або {@code null}, якщо не вказана.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Встановлює адресу електронної пошти пасажира.
     * @param email нова адреса електронної пошти (може бути {@code null}).
     */
    public void setEmail(String email) {
        logger.trace("Зміна email для пасажира ID {}.", this.id);
        this.email = email;
    }

    /**
     * Повертає тип пільги пасажира.
     * @return {@link BenefitType} тип пільги.
     */
    public BenefitType getBenefitType() {
        return benefitType;
    }

    /**
     * Встановлює тип пільги пасажира.
     * @param benefitType новий тип пільги.
     */
    public void setBenefitType(BenefitType benefitType) {
        BenefitType oldBenefitType = this.benefitType;
        if (benefitType == null) {
            logger.warn("Спроба встановити null тип пільги для пасажира ID: {}. Буде встановлено NONE.", this.id);
            this.benefitType = BenefitType.NONE;
        } else {
            this.benefitType = benefitType;
        }
        logger.info("Зміна типу пільги для пасажира ID {}: з {} на {}", this.id, oldBenefitType, this.benefitType);
    }

    /**
     * Повертає рядкове представлення об'єкта {@code Passenger}.
     * Формат: "ПІБ (ID: id, Док.: ТипДокумента НомерДокумента, Тел: Телефон, Email: Email, Пільга: ТипПільги)".
     * @return {@code String} рядкове представлення пасажира.
     */
    @Override
    public String toString() {
        String benefitDisplay = (benefitType != null && benefitType.getDisplayName() != null) ? benefitType.getDisplayName() : "не вказано";
        return String.format("%s (ID: %d, Док.: %s %s, Тел: %s, Email: %s, Пільга: %s)",
                fullName != null ? fullName : "Ім'я не вказано",
                id,
                documentType != null ? documentType : "н/д",
                documentNumber != null ? documentNumber : "н/д",
                phoneNumber != null ? phoneNumber : "н/д",
                email != null ? email : "н/д",
                benefitDisplay
        );
    }

    /**
     * Порівнює поточний об'єкт {@code Passenger} з іншим об'єктом.
     * Два пасажири вважаються рівними, якщо їхні ідентифікатори ({@code id}) однакові.
     *
     * @param o об'єкт для порівняння.
     * @return {@code true}, якщо об'єкти рівні (мають однаковий {@code id}),
     *         {@code false} в іншому випадку.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passenger passenger = (Passenger) o;
        return id == passenger.id;
    }

    /**
     * Повертає хеш-код для об'єкта {@code Passenger}.
     * Хеш-код базується на ідентифікаторі ({@code id}) пасажира.
     *
     * @return {@code int} хеш-код об'єкта.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
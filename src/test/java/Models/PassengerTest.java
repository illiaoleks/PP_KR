package Models;

import Models.Enums.BenefitType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerTest {

    @Plugin(name = "TestListAppenderPassenger", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
    public static class TestListAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        protected TestListAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
            super(name, filter, layout, ignoreExceptions, properties);
        }

        @PluginFactory
        public static TestListAppender createAppender(
                @PluginAttribute("name") String name,
                @PluginElement("Layout") Layout<? extends Serializable> layout,
                @PluginElement("Filter") final Filter filter) {
            if (name == null) {
                LOGGER.error("No name provided for TestListAppenderPassenger");
                return null;
            }
            return new TestListAppender(name, filter, layout, true, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        public List<LogEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public void clear() {
            events.clear();
        }
    }

    private TestListAppender listAppender;
    private Passenger validPassenger;

    private final long DEFAULT_ID = 1L;
    private static final String DEFAULT_FULL_NAME = "Іван Іванович Іванов";
    private static final String DEFAULT_DOC_NUMBER = "АА123456";
    private static final String DEFAULT_DOC_TYPE = "Паспорт";
    private final String DEFAULT_PHONE = "0991234567";
    private final String DEFAULT_EMAIL = "ivan@example.com";
    private final BenefitType DEFAULT_BENEFIT = BenefitType.NONE;

    @Mock
    private BenefitType mockBenefitType;


    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        listAppender = TestListAppender.createAppender("TestListAppenderPassenger", null, null);
        listAppender.start();
        config.addAppender(listAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        if (!loggerConfig.getName().equals("insurance.log")) {
            loggerConfig = new LoggerConfig("insurance.log", Level.ALL, false);
            config.addLogger("insurance.log", loggerConfig);
        }
        loggerConfig.addAppender(listAppender, Level.ALL, null);
        ctx.updateLoggers();

        validPassenger = new Passenger(DEFAULT_ID, DEFAULT_FULL_NAME, DEFAULT_DOC_NUMBER, DEFAULT_DOC_TYPE,
                DEFAULT_PHONE, DEFAULT_EMAIL, DEFAULT_BENEFIT);
        listAppender.clear();
    }

    @AfterEach
    void tearDown() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("insurance.log");
        loggerConfig.removeAppender("TestListAppenderPassenger");
        listAppender.stop();
        listAppender.clear();
        ctx.updateLoggers();
    }

    private boolean findLogMessage(Level level, String partialMessage) {
        return listAppender.getEvents().stream().anyMatch(event ->
                event.getLevel() == level &&
                        event.getMessage().getFormattedMessage().contains(partialMessage)
        );
    }

    private boolean findExactLogMessage(Level level, String exactMessage) {
        return listAppender.getEvents().stream().anyMatch(event ->
                event.getLevel() == level &&
                        event.getMessage().getFormattedMessage().equals(exactMessage)
        );
    }


    @Test
    void constructor_validArguments_createsInstanceAndLogs() {
        listAppender.clear();
        Passenger passenger = new Passenger(2L, "Петро Петров", "ВВ654321", "ID-картка",
                "0507654321", "petro@mail.com", BenefitType.STUDENT);
        assertNotNull(passenger);
        assertEquals(2L, passenger.getId());
        assertEquals("Петро Петров", passenger.getFullName());
        assertEquals(BenefitType.STUDENT, passenger.getBenefitType());

        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Passenger з ID: 2"));
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Passenger успішно створено: ID=2"));
    }

    @Test
    void constructor_nullBenefitType_setsToNoneAndLogsWarn() {
        listAppender.clear();
        Passenger passenger = new Passenger(3L, "Анна Антоненко", "СС112233", "Закордонний паспорт",
                "0671122334", null, null);

        assertNotNull(passenger);
        assertEquals(BenefitType.NONE, passenger.getBenefitType());
        assertTrue(findLogMessage(Level.WARN, "Увага при створенні Passenger (ID: 3): Тип пільги (benefitType) є null. Буде встановлено NONE."));
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Passenger успішно створено: ID=3"));
    }

    @Test
    void constructor_nullPhoneNumberAndEmail_areAllowed() {
        listAppender.clear();
        Passenger passenger = new Passenger(4L, "Олег Олійник", "DD556677", "Посвідчення водія",
                null, null, BenefitType.PENSIONER);
        assertNotNull(passenger);
        assertNull(passenger.getPhoneNumber());
        assertNull(passenger.getEmail());
        assertTrue(findLogMessage(Level.INFO, "Об'єкт Passenger успішно створено: ID=4"));
    }


    private static Stream<Arguments> invalidConstructorArguments() {
        return Stream.of(
                Arguments.of(null, DEFAULT_DOC_NUMBER, DEFAULT_DOC_TYPE, "Повне ім'я не може бути порожнім."),
                Arguments.of("  ", DEFAULT_DOC_NUMBER, DEFAULT_DOC_TYPE, "Повне ім'я не може бути порожнім."),
                Arguments.of(DEFAULT_FULL_NAME, null, DEFAULT_DOC_TYPE, "Номер документа не може бути порожнім."),
                Arguments.of(DEFAULT_FULL_NAME, "  ", DEFAULT_DOC_TYPE, "Номер документа не може бути порожнім."),
                Arguments.of(DEFAULT_FULL_NAME, DEFAULT_DOC_NUMBER, null, "Тип документа не може бути порожнім."),
                Arguments.of(DEFAULT_FULL_NAME, DEFAULT_DOC_NUMBER, "  ", "Тип документа не може бути порожнім.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void constructor_invalidRequiredFields_throwsIllegalArgumentExceptionAndLogsError(
            String fullName, String docNumber, String docType, String expectedMessage) {

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new Passenger(5L, fullName, docNumber, docType, DEFAULT_PHONE, DEFAULT_EMAIL, DEFAULT_BENEFIT)
        );
        assertEquals(expectedMessage, exception.getMessage());

        assertFalse(findLogMessage(Level.DEBUG, "Спроба створити новий об'єкт Passenger з ID: 5"));
        if (fullName == null || fullName.trim().isEmpty()) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Passenger: Повне ім'я (fullName) не може бути порожнім для ID: 5"));
        }
        if (docNumber == null || docNumber.trim().isEmpty()) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Passenger: Номер документа (documentNumber) не може бути порожнім для ID: 5"));
        }
        if (docType == null || docType.trim().isEmpty()) {
            assertTrue(findLogMessage(Level.ERROR, "Помилка створення Passenger: Тип документа (documentType) не може бути порожнім для ID: 5"));
        }
    }

    @Test
    void getId_returnsCorrectId() {
        assertEquals(DEFAULT_ID, validPassenger.getId());
    }

    @Test
    void setId_changesIdAndLogsTrace() {
        validPassenger.setId(99L);
        assertEquals(99L, validPassenger.getId());
        assertFalse(findExactLogMessage(Level.TRACE, "Встановлення ID пасажира " + DEFAULT_ID + " на: 99"));
    }

    @Test
    void getFullName_returnsCorrectFullName() {
        assertEquals(DEFAULT_FULL_NAME, validPassenger.getFullName());
    }

    @Test
    void setFullName_validName_changesNameAndLogsTrace() {
        String newName = "Петро Петренко";
        validPassenger.setFullName(newName);
        assertEquals(newName, validPassenger.getFullName());
        assertFalse(findLogMessage(Level.TRACE, "Зміна повного імені для пасажира ID " + DEFAULT_ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void setFullName_nullOrEmptyOrBlank_setsItAndLogsWarnAndTrace(String invalidName) {
        validPassenger.setFullName(invalidName);
        assertEquals(invalidName, validPassenger.getFullName());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожнє повне ім'я для пасажира ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна повного імені для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void getDocumentNumber_returnsCorrectDocumentNumber() {
        assertEquals(DEFAULT_DOC_NUMBER, validPassenger.getDocumentNumber());
    }

    @Test
    void setDocumentNumber_validNumber_changesNumberAndLogsTrace() {
        String newDocNum = "СС987654";
        validPassenger.setDocumentNumber(newDocNum);
        assertEquals(newDocNum, validPassenger.getDocumentNumber());
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера документа для пасажира ID " + DEFAULT_ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void setDocumentNumber_nullOrEmptyOrBlank_setsItAndLogsWarnAndTrace(String invalidDocNum) {
        validPassenger.setDocumentNumber(invalidDocNum);
        assertEquals(invalidDocNum, validPassenger.getDocumentNumber());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожній номер документа для пасажира ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера документа для пасажира ID " + DEFAULT_ID));
    }


    @Test
    void getDocumentType_returnsCorrectDocumentType() {
        assertEquals(DEFAULT_DOC_TYPE, validPassenger.getDocumentType());
    }

    @Test
    void setDocumentType_validType_changesTypeAndLogsTrace() {
        String newDocType = "Водійське посвідчення";
        validPassenger.setDocumentType(newDocType);
        assertEquals(newDocType, validPassenger.getDocumentType());
        assertFalse(findLogMessage(Level.TRACE, "Зміна типу документа для пасажира ID " + DEFAULT_ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void setDocumentType_nullOrEmptyOrBlank_setsItAndLogsWarnAndTrace(String invalidDocType) {
        validPassenger.setDocumentType(invalidDocType);
        assertEquals(invalidDocType, validPassenger.getDocumentType());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити порожній тип документа для пасажира ID: " + DEFAULT_ID));
        assertFalse(findLogMessage(Level.TRACE, "Зміна типу документа для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void getPhoneNumber_returnsCorrectPhoneNumber() {
        assertEquals(DEFAULT_PHONE, validPassenger.getPhoneNumber());
    }

    @Test
    void setPhoneNumber_validNumber_changesNumberAndLogsTrace() {
        String newPhone = "0667778899";
        validPassenger.setPhoneNumber(newPhone);
        assertEquals(newPhone, validPassenger.getPhoneNumber());
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера телефону для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void setPhoneNumber_nullNumber_setsNullAndLogsTrace() {
        validPassenger.setPhoneNumber(null);
        assertNull(validPassenger.getPhoneNumber());
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера телефону для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void setPhoneNumber_emptyNumber_setsEmptyAndLogsTrace() {
        validPassenger.setPhoneNumber("");
        assertEquals("", validPassenger.getPhoneNumber());
        assertFalse(findLogMessage(Level.TRACE, "Зміна номера телефону для пасажира ID " + DEFAULT_ID));
    }


    @Test
    void getEmail_returnsCorrectEmail() {
        assertEquals(DEFAULT_EMAIL, validPassenger.getEmail());
    }

    @Test
    void setEmail_validEmail_changesEmailAndLogsTrace() {
        String newEmail = "new.email@example.com";
        validPassenger.setEmail(newEmail);
        assertEquals(newEmail, validPassenger.getEmail());
        assertFalse(findLogMessage(Level.TRACE, "Зміна email для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void setEmail_nullEmail_setsNullAndLogsTrace() {
        validPassenger.setEmail(null);
        assertNull(validPassenger.getEmail());
        assertFalse(findLogMessage(Level.TRACE, "Зміна email для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void setEmail_emptyEmail_setsEmptyAndLogsTrace() {
        validPassenger.setEmail("");
        assertEquals("", validPassenger.getEmail());
        assertFalse(findLogMessage(Level.TRACE, "Зміна email для пасажира ID " + DEFAULT_ID));
    }

    @Test
    void getBenefitType_returnsCorrectBenefitType() {
        assertEquals(DEFAULT_BENEFIT, validPassenger.getBenefitType());
    }

    @Test
    void setBenefitType_validType_changesTypeAndLogsInfo() {
        BenefitType oldType = validPassenger.getBenefitType();
        BenefitType newType = BenefitType.STUDENT;
        validPassenger.setBenefitType(newType);
        assertEquals(newType, validPassenger.getBenefitType());
        assertTrue(findLogMessage(Level.INFO, "Зміна типу пільги для пасажира ID " + DEFAULT_ID + ": з " + oldType.getDisplayName() + " на " + newType.getDisplayName()));
    }

    @Test
    void setBenefitType_nullType_setsToNoneAndLogsWarnAndInfo() {
        BenefitType oldType = validPassenger.getBenefitType();
        validPassenger.setBenefitType(null);
        assertEquals(BenefitType.NONE, validPassenger.getBenefitType());
        assertTrue(findLogMessage(Level.WARN, "Спроба встановити null тип пільги для пасажира ID: " + DEFAULT_ID + ". Буде встановлено NONE."));
        assertTrue(findLogMessage(Level.INFO, "Зміна типу пільги для пасажира ID " + DEFAULT_ID + ": з " + oldType.getDisplayName() + " на " + BenefitType.NONE.getDisplayName()));
    }

    @Test
    void toString_withValidData_returnsCorrectFormat() {
        String expected = String.format("%s (ID: %d, Док.: %s %s, Тел: %s, Email: %s, Пільга: %s)",
                DEFAULT_FULL_NAME, DEFAULT_ID, DEFAULT_DOC_TYPE, DEFAULT_DOC_NUMBER,
                DEFAULT_PHONE, DEFAULT_EMAIL, DEFAULT_BENEFIT.getDisplayName());
        assertEquals(expected, validPassenger.toString());
    }

    @Test
    void toString_withNullFullNameInObject_returnsDefault() {

        assertTrue(validPassenger.toString().contains(DEFAULT_FULL_NAME));
    }

    @Test
    void toString_withNullDocumentTypeInObject_returnsND() {
        validPassenger.setDocumentType(null); listAppender.clear();
        assertTrue(validPassenger.toString().contains("Док.: н/д " + DEFAULT_DOC_NUMBER));
    }

    @Test
    void toString_withNullDocumentNumberInObject_returnsND() {
        validPassenger.setDocumentNumber(null); listAppender.clear();
        assertTrue(validPassenger.toString().contains("Док.: " + DEFAULT_DOC_TYPE + " н/д"));
    }

    @Test
    void toString_withNullPhoneNumberInObject_returnsND() {
        validPassenger.setPhoneNumber(null); listAppender.clear();
        assertTrue(validPassenger.toString().contains("Тел: н/д"));
    }

    @Test
    void toString_withNullEmailInObject_returnsND() {
        validPassenger.setEmail(null); listAppender.clear();
        assertTrue(validPassenger.toString().contains("Email: н/д"));
    }

    @Test
    void toString_withBenefitTypeHavingNullDisplayName_returnsDefault() {

        when(mockBenefitType.getDisplayName()).thenReturn(null);
        validPassenger.setBenefitType(mockBenefitType);
        listAppender.clear();

        String str = validPassenger.toString();
        assertTrue(str.contains("Пільга: не вказано"));
    }

    @Test
    void toString_withNullBenefitTypeInObject_returnsDefault() {

        Passenger passengerWithNullBenefit = new Passenger(6L, "Test", "TestDoc", "TestType", "123", "e@mail", null);
        listAppender.clear();

        assertTrue(passengerWithNullBenefit.toString().contains("Пільга: " + BenefitType.NONE.getDisplayName()));
    }


    @Test
    void equals_sameObject_returnsTrue() {
        assertTrue(validPassenger.equals(validPassenger));
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertFalse(validPassenger.equals(null));
    }

    @Test
    void equals_differentClass_returnsFalse() {
        assertFalse(validPassenger.equals(new Object()));
    }

    @Test
    void equals_sameId_returnsTrue() {
        Passenger anotherPassenger = new Passenger(DEFAULT_ID, "Інше Ім'я", "ІншийДок", "ІншийТип",
                "ІншийТел", "other@mail.com", BenefitType.STUDENT);
        assertTrue(validPassenger.equals(anotherPassenger));
    }

    @Test
    void equals_differentId_returnsFalse() {
        Passenger anotherPassenger = new Passenger(DEFAULT_ID + 1, DEFAULT_FULL_NAME, DEFAULT_DOC_NUMBER, DEFAULT_DOC_TYPE,
                DEFAULT_PHONE, DEFAULT_EMAIL, DEFAULT_BENEFIT);
        assertFalse(validPassenger.equals(anotherPassenger));
    }

    @Test
    void hashCode_basedOnId() {
        Passenger anotherPassengerWithSameId = new Passenger(DEFAULT_ID, "Інше Ім'я", "ІншийДок", "ІншийТип",
                null, null, null);
        assertEquals(validPassenger.hashCode(), anotherPassengerWithSameId.hashCode());

        Passenger anotherPassengerWithDifferentId = new Passenger(DEFAULT_ID + 5, DEFAULT_FULL_NAME, DEFAULT_DOC_NUMBER, DEFAULT_DOC_TYPE,
                DEFAULT_PHONE, DEFAULT_EMAIL, DEFAULT_BENEFIT);
        assertNotEquals(validPassenger.hashCode(), anotherPassengerWithDifferentId.hashCode());
    }
}
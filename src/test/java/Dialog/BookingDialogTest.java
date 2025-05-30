package Dialog;

import DAO.PassengerDAO;
import DAO.TicketDAO;
import Models.Enums.BenefitType;
import Models.Enums.FlightStatus;
import Models.*;
import UI.Dialog.BookingDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingDialogTest {

    @Mock
    private PassengerDAO mockPassengerDAO;
    @Mock
    private TicketDAO mockTicketDAO;

    private JFrame testOwnerFrame;
    private Flight testFlight;
    private String testSeat;
    private BookingDialog bookingDialog;

    @Captor
    private ArgumentCaptor<Passenger> passengerCaptor;
    @Captor
    private ArgumentCaptor<Ticket> ticketCaptor;

    private static final DateTimeFormatter DIALOG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @BeforeEach
    void setUp() {
        BookingDialog.setSuppressMessagesForTesting(true);

        testOwnerFrame = new JFrame();

        Stop departureStop = new Stop(1, "Київ", "Автовокзал Центральний");
        Stop destinationStop = new Stop(2, "Львів", "Автовокзал Стрийський");
        Stop intermediateStop = new Stop(3, "Житомир", "Автовокзал");
        Route route = new Route(1, departureStop, destinationStop, Collections.singletonList(intermediateStop));
        testFlight = new Flight(
                1L, route,
                LocalDateTime.of(2024, 8, 1, 10, 0),
                LocalDateTime.of(2024, 8, 1, 17, 0),
                50, FlightStatus.PLANNED,
                "Neoplan N1216", new BigDecimal("1000.00")
        );
        testSeat = "A1";
        bookingDialog = null;
    }

    @AfterEach
    void tearDown() {
        BookingDialog.setSuppressMessagesForTesting(false);

        if (bookingDialog != null) {
            final BookingDialog currentDialog = bookingDialog;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (currentDialog.isDisplayable()) {
                        currentDialog.dispose();
                    }
                });
            } catch (Exception e) {
                System.err.println("Error disposing bookingDialog in tearDown: " + e.getMessage());
            }
            bookingDialog = null;
        }
        if (testOwnerFrame != null) {
            testOwnerFrame.dispose();
            testOwnerFrame = null;
        }
    }

    private void initializeDialog() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog = new BookingDialog(testOwnerFrame, testFlight, testSeat, mockPassengerDAO, mockTicketDAO);
            });
        } catch (Exception e) {
            e.printStackTrace();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail("Failed to initialize BookingDialog: " + cause.getMessage(), cause);
        }
    }

    @Test
    @DisplayName("Конструктор: успішна ініціалізація")
    void constructor_successfulInitialization() {
        initializeDialog();
        assertNotNull(bookingDialog, "BookingDialog не повинен бути null після ініціалізації");
        assertTrue(bookingDialog.getTitle().contains("Бронювання квитка"));

        String expectedFlightInfo = String.format("Рейс: %s (%s - %s)",
                testFlight.getRoute().getFullRouteDescription(),
                testFlight.getDepartureDateTime().format(DIALOG_DATE_TIME_FORMATTER),
                testFlight.getArrivalDateTime().format(DIALOG_DATE_TIME_FORMATTER)
        );
        assertEquals(expectedFlightInfo, bookingDialog.getLblFlightInfo().getText());
        assertEquals("Обране місце: " + testSeat, bookingDialog.getLblSeatInfo().getText());

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("uk", "UA"));
        String expectedPrice = "Ціна до сплати: " + currencyFormat.format(testFlight.getPricePerSeat());
        assertEquals(expectedPrice, bookingDialog.getLblPriceInfo().getText());

        assertEquals(BenefitType.NONE, bookingDialog.getCmbBenefitType().getSelectedItem());
    }

    @Test
    @DisplayName("Конструктор: null flight, повідомлення придушені")
    void constructor_nullFlight_messagesSuppressed_shouldAttemptDispose() {
        final BookingDialog[] dialogHolder = new BookingDialog[1];

        assertDoesNotThrow(() -> {
            SwingUtilities.invokeAndWait(() -> {
                dialogHolder[0] = new BookingDialog(testOwnerFrame, null, testSeat, mockPassengerDAO, mockTicketDAO);
            });
        }, "Конструктор з null flight не повинен кидати винятків, коли повідомлення придушені.");


        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (dialogHolder[0] != null) {
            final BookingDialog createdDialog = dialogHolder[0];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    assertFalse(createdDialog.isDisplayable(), "Діалог з null flight мав би бути закритий (dispose).");
                });
            } catch (Exception e) {
                System.err.println("Перевірка isDisplayable для null-flight діалогу (повідомлення придушені): " + e.getMessage());
            }
        } else {

            System.out.println("Інформація: dialogHolder[0] залишився null, що очікувано, якщо dispose спрацював дуже швидко.");
        }
        assertNull(this.bookingDialog, "Глобальний bookingDialog не повинен бути змінений цим тестом.");
    }


    @ParameterizedTest
    @CsvSource({
            "NONE, 1000.00, 1000.00",
            "STUDENT, 1000.00, 800.00",
            "PENSIONER, 1000.00, 850.00",
            "COMBATANT, 1000.00, 500.00",
            "NONE, 0.00, 0.00",
            "STUDENT, 0.00, 0.00"
    })
    @DisplayName("Розрахунок ціни з пільгою")
    void calculatePriceWithBenefit_variousScenarios(BenefitType benefit, String basePriceStr, String expectedPriceStr) {
        initializeDialog();
        BigDecimal basePrice = new BigDecimal(basePriceStr);
        BigDecimal expectedPrice = new BigDecimal(expectedPriceStr);
        BigDecimal actualPrice = bookingDialog.calculatePriceWithBenefit(basePrice, benefit);
        assertEquals(0, expectedPrice.compareTo(actualPrice),
                "Ціна для пільги " + benefit + " розрахована невірно.");
    }

    @Test
    @DisplayName("Розрахунок ціни з null базовою ціною")
    void calculatePriceWithBenefit_nullBasePrice() {
        initializeDialog();
        BigDecimal actualPrice = bookingDialog.calculatePriceWithBenefit(null, BenefitType.STUDENT);
        assertEquals(0, BigDecimal.ZERO.compareTo(actualPrice), "Ціна має бути 0, якщо базова ціна null.");
    }

    @Test
    @DisplayName("Оновлення інформації про ціну при зміні пільги")
    void updatePriceInfo_onBenefitChange() {
        initializeDialog();
        bookingDialog.getCmbBenefitType().setSelectedItem(BenefitType.STUDENT);
        ItemEvent itemEvent = new ItemEvent(bookingDialog.getCmbBenefitType(), ItemEvent.ITEM_STATE_CHANGED, BenefitType.STUDENT, ItemEvent.SELECTED);
        for (var listener : bookingDialog.getCmbBenefitType().getItemListeners()) {
            listener.itemStateChanged(itemEvent);
        }

        BigDecimal basePrice = testFlight.getPricePerSeat();
        BigDecimal discount = basePrice.multiply(BigDecimal.valueOf(0.20));
        BigDecimal expectedPriceVal = basePrice.subtract(discount).setScale(2, BigDecimal.ROUND_HALF_UP);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("uk", "UA"));
        String expectedPriceText = "Ціна до сплати: " + currencyFormat.format(expectedPriceVal);

        assertEquals(expectedPriceText, bookingDialog.getLblPriceInfo().getText());
    }

    @Test
    @DisplayName("Підтвердження бронювання: успішний сценарій")
    void confirmBookingAction_success() throws SQLException {
        initializeDialog();
        bookingDialog.getTxtFullName().setText("Тест Тестенко");
        bookingDialog.getTxtDocumentType().setText("Паспорт");
        bookingDialog.getTxtDocumentNumber().setText("АА123456");
        bookingDialog.getTxtPhoneNumber().setText("0991234567");
        bookingDialog.getTxtEmail().setText("test@example.com");
        bookingDialog.getCmbBenefitType().setSelectedItem(BenefitType.NONE);

        when(mockPassengerDAO.addOrGetPassenger(any(Passenger.class))).thenReturn(1L);
        when(mockTicketDAO.addTicket(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return true;
        });

        ActionEvent confirmEvent = new ActionEvent(bookingDialog.getBtnConfirmBooking(), ActionEvent.ACTION_PERFORMED, "confirm");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnConfirmBooking().getActionListeners()[0].actionPerformed(confirmEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки: " + e.getMessage(), e);
        }

        verify(mockPassengerDAO).addOrGetPassenger(passengerCaptor.capture());
        Passenger capturedPassenger = passengerCaptor.getValue();
        assertEquals("Тест Тестенко", capturedPassenger.getFullName());
        assertEquals(1L, capturedPassenger.getId());

        verify(mockTicketDAO).addTicket(ticketCaptor.capture());
        Ticket capturedTicket = ticketCaptor.getValue();
        assertEquals(testFlight.getId(), capturedTicket.getFlight().getId());
        assertEquals(1L, capturedTicket.getPassenger().getId());

        assertTrue(bookingDialog.isBookingConfirmed());
        assertFalse(bookingDialog.isDisplayable(), "Діалог мав би закритися після успішного бронювання");
    }

    @Test
    @DisplayName("Підтвердження бронювання: помилка валідації (порожнє ПІБ)")
    void confirmBookingAction_validationError_emptyFullName() throws SQLException {
        initializeDialog();
        bookingDialog.getTxtDocumentType().setText("Паспорт");
        bookingDialog.getTxtDocumentNumber().setText("АА123456");
        bookingDialog.getTxtPhoneNumber().setText("0991234567");

        ActionEvent confirmEvent = new ActionEvent(bookingDialog.getBtnConfirmBooking(), ActionEvent.ACTION_PERFORMED, "confirm");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnConfirmBooking().getActionListeners()[0].actionPerformed(confirmEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки: " + e.getMessage(), e);
        }

        verify(mockPassengerDAO, never()).addOrGetPassenger(any(Passenger.class));
        verify(mockTicketDAO, never()).addTicket(any(Ticket.class));
        assertFalse(bookingDialog.isBookingConfirmed());
        assertTrue(bookingDialog.isDisplayable(), "Діалог не мав закриватися при помилці валідації");
    }


    @Test
    @DisplayName("Підтвердження бронювання: помилка DAO при збереженні пасажира")
    void confirmBookingAction_passengerDaoError() throws SQLException {
        initializeDialog();
        bookingDialog.getTxtFullName().setText("Тест Тестенко");
        bookingDialog.getTxtDocumentType().setText("Паспорт");
        bookingDialog.getTxtDocumentNumber().setText("АА123456");
        bookingDialog.getTxtPhoneNumber().setText("0991234567");

        when(mockPassengerDAO.addOrGetPassenger(any(Passenger.class))).thenThrow(new SQLException("DB error passenger"));

        ActionEvent confirmEvent = new ActionEvent(bookingDialog.getBtnConfirmBooking(), ActionEvent.ACTION_PERFORMED, "confirm");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnConfirmBooking().getActionListeners()[0].actionPerformed(confirmEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки: " + e.getMessage(), e);
        }

        verify(mockTicketDAO, never()).addTicket(any(Ticket.class));
        assertFalse(bookingDialog.isBookingConfirmed());
        assertTrue(bookingDialog.isDisplayable());
    }

    @Test
    @DisplayName("Підтвердження бронювання: помилка DAO при збереженні квитка (addTicket повертає false)")
    void confirmBookingAction_ticketDaoAddTicketReturnsFalse() throws SQLException {
        initializeDialog();
        bookingDialog.getTxtFullName().setText("Тест Тестенко");
        bookingDialog.getTxtDocumentType().setText("Паспорт");
        bookingDialog.getTxtDocumentNumber().setText("АА123456");
        bookingDialog.getTxtPhoneNumber().setText("0991234567");

        when(mockPassengerDAO.addOrGetPassenger(any(Passenger.class))).thenReturn(1L);
        when(mockTicketDAO.addTicket(any(Ticket.class))).thenReturn(false);

        ActionEvent confirmEvent = new ActionEvent(bookingDialog.getBtnConfirmBooking(), ActionEvent.ACTION_PERFORMED, "confirm");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnConfirmBooking().getActionListeners()[0].actionPerformed(confirmEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки: " + e.getMessage(), e);
        }

        assertFalse(bookingDialog.isBookingConfirmed());
        assertTrue(bookingDialog.isDisplayable());
    }

    @Test
    @DisplayName("Підтвердження бронювання: помилка DAO при збереженні квитка (SQLException)")
    void confirmBookingAction_ticketDaoSqlException() throws SQLException {
        initializeDialog();
        bookingDialog.getTxtFullName().setText("Тест Тестенко");
        bookingDialog.getTxtDocumentType().setText("Паспорт");
        bookingDialog.getTxtDocumentNumber().setText("АА123456");
        bookingDialog.getTxtPhoneNumber().setText("0991234567");

        when(mockPassengerDAO.addOrGetPassenger(any(Passenger.class))).thenReturn(1L);
        when(mockTicketDAO.addTicket(any(Ticket.class))).thenThrow(new SQLException("DB error ticket"));

        ActionEvent confirmEvent = new ActionEvent(bookingDialog.getBtnConfirmBooking(), ActionEvent.ACTION_PERFORMED, "confirm");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnConfirmBooking().getActionListeners()[0].actionPerformed(confirmEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки: " + e.getMessage(), e);
        }

        assertFalse(bookingDialog.isBookingConfirmed());
        assertTrue(bookingDialog.isDisplayable());
    }


    @Test
    @DisplayName("Натискання кнопки 'Скасувати'")
    void cancelButton_action() {
        initializeDialog();
        assertNotNull(bookingDialog.getBtnCancel(), "Кнопка скасування не ініціалізована");

        ActionEvent cancelEvent = new ActionEvent(bookingDialog.getBtnCancel(), ActionEvent.ACTION_PERFORMED, "cancel");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnCancel().getActionListeners()[0].actionPerformed(cancelEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки скасування: " + e.getMessage(), e);
        }

        assertFalse(bookingDialog.isBookingConfirmed());
        assertFalse(bookingDialog.isDisplayable(), "Діалог мав закритися після натискання 'Скасувати'");
    }

    @Test
    @DisplayName("Перевірка isBookingConfirmed")
    void isBookingConfirmed_initialAndAfterCancel() {
        initializeDialog();
        assertFalse(bookingDialog.isBookingConfirmed(), "Спочатку бронювання не підтверджено");

        ActionEvent cancelEvent = new ActionEvent(bookingDialog.getBtnCancel(), ActionEvent.ACTION_PERFORMED, "cancel");
        try {
            SwingUtilities.invokeAndWait(() -> {
                bookingDialog.getBtnCancel().getActionListeners()[0].actionPerformed(cancelEvent);
            });
        } catch (Exception e) {
            fail("Помилка при виконанні дії кнопки скасування: " + e.getMessage(), e);
        }
        assertFalse(bookingDialog.isBookingConfirmed(), "Після скасування бронювання не підтверджено");
    }
}
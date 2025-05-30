package Dialog;

import DAO.PassengerDAO;
import Models.Enums.BenefitType;
import Models.Passenger;
import UI.Dialog.PassengerDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerDialogTest {

    @Mock
    private PassengerDAO mockPassengerDAO;

    private JFrame testOwnerFrame;
    private PassengerDialog passengerDialog;
    private Passenger testPassenger;

    @Captor
    private ArgumentCaptor<Passenger> passengerCaptor;

    @BeforeEach
    void setUp() {
        PassengerDialog.setSuppressMessagesForTesting(true);

        testOwnerFrame = new JFrame();

        testPassenger = new Passenger(
                1L, "Тест Тестенко Тестович", "AA123456",
                "Паспорт", "0991234567", "test@example.com", BenefitType.NONE
        );
        passengerDialog = null;
    }

    @AfterEach
    void tearDown() {
        PassengerDialog.setSuppressMessagesForTesting(false);

        if (passengerDialog != null) {
            final PassengerDialog currentDialog = passengerDialog;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (currentDialog.isDisplayable()) {
                        currentDialog.dispose();
                    }
                });
            } catch (Exception e) {
                System.err.println("Error disposing passengerDialog in tearDown: " + e.getMessage());
            }
            passengerDialog = null;
        }
        if (testOwnerFrame != null) {
            testOwnerFrame.dispose();
            testOwnerFrame = null;
        }
    }

    private void initializeDialog(Passenger passenger) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                passengerDialog = new PassengerDialog(testOwnerFrame, passenger, mockPassengerDAO);
            });
        } catch (Exception e) {
            e.printStackTrace();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail("Failed to initialize PassengerDialog: " + cause.getMessage(), cause);
        }
    }

    @Test
    @DisplayName("Конструктор: успішна ініціалізація та заповнення полів")
    void constructor_successfulInitializationAndPopulation() {
        initializeDialog(testPassenger);
        assertNotNull(passengerDialog);
        assertEquals("Редагувати дані пасажира", passengerDialog.getTitle());

        assertEquals(testPassenger.getFullName(), passengerDialog.getTxtFullName().getText());
        assertEquals(testPassenger.getDocumentType(), passengerDialog.getTxtDocumentType().getText());
        assertEquals(testPassenger.getDocumentNumber(), passengerDialog.getTxtDocumentNumber().getText());
        assertEquals(testPassenger.getPhoneNumber(), passengerDialog.getTxtPhoneNumber().getText());
        assertEquals(testPassenger.getEmail(), passengerDialog.getTxtEmail().getText());
        assertEquals(testPassenger.getBenefitType(), passengerDialog.getCmbBenefitType().getSelectedItem());
    }

    @Test
    @DisplayName("Конструктор: помилка, якщо Passenger є null")
    void constructor_nullPassenger_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            new PassengerDialog(testOwnerFrame, null, mockPassengerDAO);
        });
        assertEquals("Пасажир не може бути null.", exception.getMessage());
    }

    @Test
    @DisplayName("Конструктор: помилка, якщо PassengerDAO є null")
    void constructor_nullPassengerDAO_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new PassengerDialog(testOwnerFrame, testPassenger, null);
        });
        assertEquals("PassengerDAO не може бути null.", exception.getMessage());
    }

    private void clickSaveButton() {
        assertNotNull(passengerDialog.getBtnSave(), "Кнопка 'Зберегти' не ініціалізована");
        try {
            SwingUtilities.invokeAndWait(() -> {
                passengerDialog.getBtnSave().getActionListeners()[0].actionPerformed(
                        new ActionEvent(passengerDialog.getBtnSave(), ActionEvent.ACTION_PERFORMED, "save")
                );
            });
        } catch (Exception e) {
            fail("Помилка при симуляції натискання кнопки 'Зберегти': " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Збереження: успішне оновлення даних пасажира")
    void savePassengerAction_success() throws SQLException {
        initializeDialog(testPassenger);


        String newFullName = "Нове ПІБ";
        String newPhone = "0509876543";
        String newEmail = "new.email@example.com";
        BenefitType newBenefit = BenefitType.STUDENT;

        passengerDialog.getTxtFullName().setText(newFullName);
        passengerDialog.getTxtPhoneNumber().setText(newPhone);
        passengerDialog.getTxtEmail().setText(newEmail);
        passengerDialog.getCmbBenefitType().setSelectedItem(newBenefit);

        when(mockPassengerDAO.updatePassenger(any(Passenger.class))).thenReturn(true);

        clickSaveButton();

        assertTrue(passengerDialog.isSaved());
        assertFalse(passengerDialog.isDisplayable());

        verify(mockPassengerDAO).updatePassenger(passengerCaptor.capture());
        Passenger updatedPassenger = passengerCaptor.getValue();

        assertEquals(testPassenger.getId(), updatedPassenger.getId());
        assertEquals(newFullName, updatedPassenger.getFullName());
        assertEquals(newPhone, updatedPassenger.getPhoneNumber());
        assertEquals(newEmail, updatedPassenger.getEmail());
        assertEquals(newBenefit, updatedPassenger.getBenefitType());

        assertEquals(testPassenger.getDocumentType(), updatedPassenger.getDocumentType());
        assertEquals(testPassenger.getDocumentNumber(), updatedPassenger.getDocumentNumber());
    }

    @Test
    @DisplayName("Збереження: помилка валідації - порожнє ПІБ")
    void savePassengerAction_validationError_emptyFullName() throws SQLException {
        initializeDialog(testPassenger);
        passengerDialog.getTxtFullName().setText("");

        clickSaveButton();

        assertFalse(passengerDialog.isSaved());
        assertTrue(passengerDialog.isDisplayable());
        verify(mockPassengerDAO, never()).updatePassenger(any(Passenger.class));
    }

    @Test
    @DisplayName("Збереження: помилка валідації - некоректний Email")
    void savePassengerAction_validationError_invalidEmail() throws SQLException {
        initializeDialog(testPassenger);
        passengerDialog.getTxtEmail().setText("некоректний_email");

        clickSaveButton();

        assertFalse(passengerDialog.isSaved());
        assertTrue(passengerDialog.isDisplayable());
        verify(mockPassengerDAO, never()).updatePassenger(any(Passenger.class));
    }

    @Test
    @DisplayName("Збереження: порожній Email є валідним (має стати null)")
    void savePassengerAction_emptyEmailIsValid() throws SQLException {
        initializeDialog(testPassenger);
        passengerDialog.getTxtEmail().setText("");

        when(mockPassengerDAO.updatePassenger(any(Passenger.class))).thenReturn(true);
        clickSaveButton();

        assertTrue(passengerDialog.isSaved());
        verify(mockPassengerDAO).updatePassenger(passengerCaptor.capture());
        assertNull(passengerCaptor.getValue().getEmail());
    }

    @Test
    @DisplayName("Збереження: помилка DAO (updatePassenger повертає false)")
    void savePassengerAction_daoUpdateFails() throws SQLException {
        initializeDialog(testPassenger);

        passengerDialog.getTxtFullName().setText("Валідне ПІБ");
        passengerDialog.getTxtDocumentType().setText("Паспорт");
        passengerDialog.getTxtDocumentNumber().setText("ББ543210");
        passengerDialog.getTxtPhoneNumber().setText("0661122333");

        when(mockPassengerDAO.updatePassenger(any(Passenger.class))).thenReturn(false);

        clickSaveButton();

        assertFalse(passengerDialog.isSaved());
        assertTrue(passengerDialog.isDisplayable());
        verify(mockPassengerDAO).updatePassenger(any(Passenger.class));
    }

    @Test
    @DisplayName("Збереження: помилка SQLException від DAO")
    void savePassengerAction_sqlExceptionOnUpdate() throws SQLException {
        initializeDialog(testPassenger);
        passengerDialog.getTxtFullName().setText("Валідне ПІБ SQLException");


        when(mockPassengerDAO.updatePassenger(any(Passenger.class))).thenThrow(new SQLException("DB update error"));

        clickSaveButton();

        assertFalse(passengerDialog.isSaved());
        assertTrue(passengerDialog.isDisplayable());
        verify(mockPassengerDAO).updatePassenger(any(Passenger.class));
    }

    @Test
    @DisplayName("Натискання кнопки 'Скасувати'")
    void cancelButton_action() {
        initializeDialog(testPassenger);
        assertNotNull(passengerDialog.getBtnCancel(), "Кнопка 'Скасувати' не ініціалізована");

        try {
            SwingUtilities.invokeAndWait(() -> {
                passengerDialog.getBtnCancel().getActionListeners()[0].actionPerformed(
                        new ActionEvent(passengerDialog.getBtnCancel(), ActionEvent.ACTION_PERFORMED, "cancel")
                );
            });
        } catch (Exception e) {
            fail("Помилка при симуляції натискання кнопки 'Скасувати': " + e.getMessage(), e);
        }

        assertFalse(passengerDialog.isSaved());
        assertFalse(passengerDialog.isDisplayable());
    }
}
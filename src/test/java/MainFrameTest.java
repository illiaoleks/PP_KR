import DB.DatabaseConnectionManager;
import UI.MainFrame;
import UI.Panel.FlightsPanel;
import UI.Panel.PassengersPanel;
import UI.Panel.ReportsPanel;
import UI.Panel.TicketsPanel;

import com.formdev.flatlaf.FlatIntelliJLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;


import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainFrameTest {

    private MainFrame mainFrameInstanceForTest;

    @Mock
    private Connection mockConnection;


    @BeforeEach
    void setUp() {
        MainFrame.setSuppressMessagesForTesting(true);

    }

    @AfterEach
    void tearDown() {
        MainFrame.setSuppressMessagesForTesting(false);

        if (mainFrameInstanceForTest != null && mainFrameInstanceForTest.isDisplayable()) {
            try {
                SwingUtilities.invokeAndWait(() -> mainFrameInstanceForTest.dispose());
            } catch (Exception e) {  }
        }
        mainFrameInstanceForTest = null;

    }

    @SuppressWarnings("unused")
    private void initializeMainFrameWithMockedPanels() {
        try (MockedConstruction<FlightsPanel> flightsMock = Mockito.mockConstruction(FlightsPanel.class);
             MockedConstruction<TicketsPanel> ticketsMock = Mockito.mockConstruction(TicketsPanel.class);
             MockedConstruction<PassengersPanel> passengersMock = Mockito.mockConstruction(PassengersPanel.class);
             MockedConstruction<ReportsPanel> reportsMock = Mockito.mockConstruction(ReportsPanel.class)) {


            if (SwingUtilities.isEventDispatchThread()) {
                mainFrameInstanceForTest = new MainFrame();
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    mainFrameInstanceForTest = new MainFrame();
                });
            }

        } catch (Exception e) {
            fail("Failed to initialize MainFrame with mocked panels: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Конструктор: успішна ініціалізація, заголовок та вкладки")
    void constructor_successfulInitialization() {
        initializeMainFrameWithMockedPanels();
        assertNotNull(mainFrameInstanceForTest);
        assertEquals("Автоматизована система управління автовокзалом", mainFrameInstanceForTest.getTitle());
        assertEquals(JFrame.EXIT_ON_CLOSE, mainFrameInstanceForTest.getDefaultCloseOperation());

        Component[] components = mainFrameInstanceForTest.getContentPane().getComponents();
        assertTrue(components.length > 0 && components[0] instanceof JTabbedPane, "MainFrame має містити JTabbedPane");

        JTabbedPane tabbedPane = (JTabbedPane) components[0];
        assertEquals(4, tabbedPane.getTabCount(), "Має бути 4 вкладки");

        boolean flightsTabFound = false;
        boolean ticketsTabFound = false;
        boolean passengersTabFound = false;
        boolean reportsTabFound = false;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            String title = tabbedPane.getTitleAt(i);
            if ("Управління рейсами".equals(title)) flightsTabFound = true;
            if ("Квитки".equals(title)) ticketsTabFound = true;
            if ("Пасажири".equals(title)) passengersTabFound = true;
            if ("Звітність".equals(title)) reportsTabFound = true;
        }
        assertTrue(flightsTabFound, "Вкладка 'Управління рейсами' не знайдена");
        assertTrue(ticketsTabFound, "Вкладка 'Квитки' не знайдена");
        assertTrue(passengersTabFound, "Вкладка 'Пасажири' не знайдена");
        assertTrue(reportsTabFound, "Вкладка 'Звітність' не знайдена");
    }
    @Test
    @DisplayName("createIcon: іконку не знайдено")
    void createIcon_notFound() {
        initializeMainFrameWithMockedPanels();
        ImageIcon icon = mainFrameInstanceForTest.createIcon("/non/existent/icon.png");
        assertNull(icon, "Іконка має бути null, якщо файл не існує");
    }

    @Test
    @DisplayName("setupLookAndFeel: успішне встановлення FlatLaf")
    void setupLookAndFeel_flatLafSuccess() {
        try (MockedStatic<UIManager> uiManagerMock = Mockito.mockStatic(UIManager.class)) {
            uiManagerMock.when(() -> UIManager.setLookAndFeel(any(FlatIntelliJLaf.class))).thenAnswer(invocation -> null);

            MainFrame.setupLookAndFeel();

            uiManagerMock.verify(() -> UIManager.setLookAndFeel(any(FlatIntelliJLaf.class)));
            uiManagerMock.verify(() -> UIManager.setLookAndFeel(anyString()), never());
        }
    }

    @Test
    @DisplayName("setupLookAndFeel: помилка FlatLaf, успішне встановлення Nimbus")
    void setupLookAndFeel_flatLafFails_nimbusSuccess() {
        try (MockedStatic<UIManager> uiManagerMock = Mockito.mockStatic(UIManager.class)) {
            uiManagerMock.when(() -> UIManager.setLookAndFeel(any(FlatIntelliJLaf.class)))
                    .thenThrow(new UnsupportedLookAndFeelException("FlatLaf error"));

            UIManager.LookAndFeelInfo nimbusInfo = new UIManager.LookAndFeelInfo("Nimbus", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
            uiManagerMock.when(UIManager::getInstalledLookAndFeels).thenReturn(new UIManager.LookAndFeelInfo[]{nimbusInfo});
            uiManagerMock.when(() -> UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel")).thenAnswer(invocation -> null);

            MainFrame.setupLookAndFeel();

            uiManagerMock.verify(() -> UIManager.setLookAndFeel(any(FlatIntelliJLaf.class)));
            uiManagerMock.verify(() -> UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"));
        }
    }

    @Test
    @DisplayName("checkDatabaseConnection: успішне підключення")
    void checkDatabaseConnection_success() throws SQLException {
        try (MockedStatic<DatabaseConnectionManager> dbManagerMock = Mockito.mockStatic(DatabaseConnectionManager.class)) {
            dbManagerMock.when(DatabaseConnectionManager::getConnection).thenReturn(mockConnection);
            when(mockConnection.isClosed()).thenReturn(false);

            assertTrue(MainFrame.checkDatabaseConnection());
            dbManagerMock.verify(DatabaseConnectionManager::getConnection);
        }
    }

    @Test
    @DisplayName("checkDatabaseConnection: помилка підключення (SQLException)")
    void checkDatabaseConnection_sqlException() throws SQLException {

        try (MockedStatic<DatabaseConnectionManager> dbManagerMock = Mockito.mockStatic(DatabaseConnectionManager.class);
             MockedStatic<JOptionPane> optionPaneMock = Mockito.mockStatic(JOptionPane.class) ) {

            dbManagerMock.when(DatabaseConnectionManager::getConnection).thenThrow(new SQLException("Test DB Error"));
            optionPaneMock.when(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt())).thenAnswer(invocation -> null);

            assertFalse(MainFrame.checkDatabaseConnection());
            dbManagerMock.verify(DatabaseConnectionManager::getConnection);
            optionPaneMock.verify(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt()), never());
        }
    }

    @Test
    @DisplayName("checkDatabaseConnection: з'єднання null")
    void checkDatabaseConnection_connectionNull() throws SQLException {

        try (MockedStatic<DatabaseConnectionManager> dbManagerMock = Mockito.mockStatic(DatabaseConnectionManager.class);
             MockedStatic<JOptionPane> optionPaneMock = Mockito.mockStatic(JOptionPane.class) ) {

            dbManagerMock.when(DatabaseConnectionManager::getConnection).thenReturn(null);
            optionPaneMock.when(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt())).thenAnswer(invocation -> null);

            assertFalse(MainFrame.checkDatabaseConnection());
            optionPaneMock.verify(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt()), never());
        }
    }

    @SuppressWarnings("unused")
    @Test
    @DisplayName("main (після рефакторингу): успішний запуск")
    void main_successfulRun_afterRefactor() throws Exception {
        try (MockedStatic<MainFrame> mainFrameStaticSpy = Mockito.mockStatic(MainFrame.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<SwingUtilities> swingUtilitiesMock = Mockito.mockStatic(SwingUtilities.class);
             MockedConstruction<FlightsPanel> flightsMock = Mockito.mockConstruction(FlightsPanel.class);
             MockedConstruction<TicketsPanel> ticketsMock = Mockito.mockConstruction(TicketsPanel.class);
             MockedConstruction<PassengersPanel> passengersMock = Mockito.mockConstruction(PassengersPanel.class);
             MockedConstruction<ReportsPanel> reportsMock = Mockito.mockConstruction(ReportsPanel.class);
             MockedConstruction<MainFrame> mainFrameConstructionMock = Mockito.mockConstruction(MainFrame.class,
                     (mock, context) -> {

                         mainFrameInstanceForTest = mock;
                         doNothing().when(mock).setVisible(anyBoolean());

                     })) {

            mainFrameStaticSpy.when(MainFrame::setupLookAndFeel).thenAnswer(invocation -> null);
            mainFrameStaticSpy.when(MainFrame::checkDatabaseConnection).thenReturn(true);

            swingUtilitiesMock.when(() -> SwingUtilities.invokeLater(any(Runnable.class)))
                    .thenAnswer((Answer<Void>) invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return null;
                    });

            MainFrame.main(new String[]{});

            mainFrameStaticSpy.verify(MainFrame::setupLookAndFeel);
            mainFrameStaticSpy.verify(MainFrame::checkDatabaseConnection);

            assertEquals(1, mainFrameConstructionMock.constructed().size(), "Має бути створений один екземпляр MainFrame");
            MainFrame constructed = mainFrameConstructionMock.constructed().get(0);
            verify(constructed).setVisible(true);
        }
    }



    @Test
    @DisplayName("showDialogMessage: відображає JOptionPane, коли повідомлення НЕ придушені")
    void showDialogMessage_displaysDialog_whenNotSuppressed() {
        initializeMainFrameWithMockedPanels();
        MainFrame.setSuppressMessagesForTesting(false);

        try (MockedStatic<JOptionPane> optionPaneMock = Mockito.mockStatic(JOptionPane.class)) {
            optionPaneMock.when(() -> JOptionPane.showMessageDialog(
                    any(Component.class), any(Object.class), anyString(), anyInt()
            )).thenAnswer(invocation -> null);

            mainFrameInstanceForTest.showDialogMessage(mainFrameInstanceForTest, "Test Message", "Test Title", JOptionPane.INFORMATION_MESSAGE);

            optionPaneMock.verify(() -> JOptionPane.showMessageDialog(
                    mainFrameInstanceForTest, "Test Message", "Test Title", JOptionPane.INFORMATION_MESSAGE
            ), times(1));
        }
    }

    @Test
    @DisplayName("showDialogMessage: НЕ відображає JOptionPane (а логує), коли повідомлення придушені")
    void showDialogMessage_logsMessage_whenSuppressed() {
        initializeMainFrameWithMockedPanels();


        try (MockedStatic<JOptionPane> optionPaneMock = Mockito.mockStatic(JOptionPane.class)) {
            optionPaneMock.when(() -> JOptionPane.showMessageDialog(
                    any(Component.class), any(Object.class), anyString(), anyInt()
            )).thenAnswer(invocation -> null);

            mainFrameInstanceForTest.showDialogMessage(mainFrameInstanceForTest, "Suppressed Message", "Suppressed Title", JOptionPane.ERROR_MESSAGE);

            optionPaneMock.verify(() -> JOptionPane.showMessageDialog(
                    any(Component.class), any(Object.class), anyString(), anyInt()
            ), never());

        }
    }

    @Test
    @DisplayName("showDialogMessage: коректно обробляє різні типи повідомлень при логуванні (придушено)")
    void showDialogMessage_handlesDifferentMessageTypes_whenSuppressed() {
        initializeMainFrameWithMockedPanels();


        try (MockedStatic<JOptionPane> optionPaneMock = Mockito.mockStatic(JOptionPane.class)) {

            mainFrameInstanceForTest.showDialogMessage(null, "Error msg", "Title1", JOptionPane.ERROR_MESSAGE);
            mainFrameInstanceForTest.showDialogMessage(null, "Info msg", "Title2", JOptionPane.INFORMATION_MESSAGE);
            mainFrameInstanceForTest.showDialogMessage(null, "Warning msg", "Title3", JOptionPane.WARNING_MESSAGE);
            mainFrameInstanceForTest.showDialogMessage(null, "Question msg", "Title4", JOptionPane.QUESTION_MESSAGE);
            mainFrameInstanceForTest.showDialogMessage(null, "Default msg", "Title5", JOptionPane.PLAIN_MESSAGE); // Example of a default/unknown

            optionPaneMock.verify(() -> JOptionPane.showMessageDialog(any(), any(), anyString(), anyInt()), never());

        }
    }
}
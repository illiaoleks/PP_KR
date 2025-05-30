package Dialog;

import DAO.StopDAO;
import Models.Route;
import Models.Stop;
import UI.Dialog.RouteCreationDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteCreationDialogTest {

    @Mock
    private StopDAO mockStopDAO;

    private JFrame testOwnerFrame;
    private RouteCreationDialog routeCreationDialog;
    private List<Stop> allStopsList;

    private Stop stopA, stopB, stopC, stopD, stopE;

    @BeforeEach
    void setUp() throws SQLException {
        RouteCreationDialog.setSuppressMessagesForTesting(true);

        testOwnerFrame = new JFrame();


        stopA = new Stop(1, "Місто A", "Вокзал A");
        stopB = new Stop(2, "Місто B", "Вокзал B");
        stopC = new Stop(3, "Місто C", "Вокзал C");
        stopD = new Stop(4, "Місто D", "Вокзал D");
        stopE = new Stop(5, "Місто E", "Вокзал E");
        allStopsList = new ArrayList<>(Arrays.asList(stopA, stopB, stopC, stopD, stopE));


        when(mockStopDAO.getAllStops()).thenReturn(allStopsList);

        routeCreationDialog = null;
    }

    @AfterEach
    void tearDown() {
        RouteCreationDialog.setSuppressMessagesForTesting(false);

        if (routeCreationDialog != null) {
            final RouteCreationDialog currentDialog = routeCreationDialog;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (currentDialog.isDisplayable()) {
                        currentDialog.dispose();
                    }
                });
            } catch (Exception e) {
                System.err.println("Error disposing routeCreationDialog in tearDown: " + e.getMessage());
            }
            routeCreationDialog = null;
        }
        if (testOwnerFrame != null) {
            testOwnerFrame.dispose();
            testOwnerFrame = null;
        }
    }

    private void initializeDialog() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                routeCreationDialog = new RouteCreationDialog(testOwnerFrame, mockStopDAO);
            });
        } catch (Exception e) {
            e.printStackTrace();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail("Failed to initialize RouteCreationDialog: " + cause.getMessage(), cause);
        }
    }

    @Test
    @DisplayName("Конструктор: успішна ініціалізація та завантаження зупинок")
    void constructor_successfulInitializationAndLoadStops() {
        initializeDialog();
        assertNotNull(routeCreationDialog);
        assertEquals("Створення нового маршруту", routeCreationDialog.getTitle());


        assertEquals(allStopsList.size() + 1, routeCreationDialog.getCmbDepartureStop().getItemCount());
        assertEquals(allStopsList.size() + 1, routeCreationDialog.getCmbDestinationStop().getItemCount());

        assertEquals(allStopsList.size(), routeCreationDialog.getAvailableStopsModel().getSize());
        assertTrue(routeCreationDialog.getBtnSave().isEnabled());
    }

    @Test
    @DisplayName("Завантаження зупинок: порожній список з DAO")
    void loadStops_emptyListFromDAO() throws SQLException {
        when(mockStopDAO.getAllStops()).thenReturn(new ArrayList<>());
        initializeDialog();

        assertFalse(routeCreationDialog.getBtnSave().isEnabled());
        assertFalse(routeCreationDialog.getCmbDepartureStop().isEnabled());
        assertFalse(routeCreationDialog.getCmbDestinationStop().isEnabled());
        assertEquals(0, routeCreationDialog.getAvailableStopsModel().getSize());
    }

    @Test
    @DisplayName("Завантаження зупинок: SQLException від DAO")
    void loadStops_sqlExceptionFromDAO() throws SQLException {
        when(mockStopDAO.getAllStops()).thenThrow(new SQLException("DB error loading stops"));
        initializeDialog();
        assertFalse(routeCreationDialog.getBtnSave().isEnabled());
    }

    @Test
    @DisplayName("Оновлення доступних зупинок: при виборі відправлення та призначення")
    void updateAvailableIntermediateStops_onSelection() {
        initializeDialog();

        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);


        DefaultListModel<Stop> availableModel = routeCreationDialog.getAvailableStopsModel();
        assertEquals(allStopsList.size() - 2, availableModel.getSize());
        assertFalse(availableModel.contains(stopA));
        assertFalse(availableModel.contains(stopE));
        assertTrue(availableModel.contains(stopB));
        assertTrue(availableModel.contains(stopC));
        assertTrue(availableModel.contains(stopD));
    }

    @Test
    @DisplayName("Додавання проміжної зупинки")
    void addIntermediateStopAction_addSelected() {
        initializeDialog();
        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);


        DefaultListModel<Stop> availableModel = routeCreationDialog.getAvailableStopsModel();
        JList<Stop> lstAvailable = routeCreationDialog.getLstAvailableStops();
        int indexOfStopB = -1;
        for(int i = 0; i < availableModel.getSize(); i++){
            if(availableModel.getElementAt(i).equals(stopB)){
                indexOfStopB = i;
                break;
            }
        }
        assertTrue(indexOfStopB != -1, "stopB не знайдено в доступних після вибору A та E");
        lstAvailable.setSelectedIndex(indexOfStopB);



        routeCreationDialog.getBtnAddStop().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnAddStop(), ActionEvent.ACTION_PERFORMED, "add")
        );

        DefaultListModel<Stop> selectedModel = routeCreationDialog.getSelectedStopsModel();
        assertEquals(1, selectedModel.getSize());
        assertEquals(stopB, selectedModel.getElementAt(0));
        assertFalse(availableModel.contains(stopB));
    }

    @Test
    @DisplayName("Видалення проміжної зупинки")
    void removeIntermediateStopAction_removeSelected() {
        initializeDialog();
        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);


        DefaultListModel<Stop> selectedModel = routeCreationDialog.getSelectedStopsModel();
        DefaultListModel<Stop> availableModel = routeCreationDialog.getAvailableStopsModel();
        selectedModel.addElement(stopB); availableModel.removeElement(stopB);
        selectedModel.addElement(stopC); availableModel.removeElement(stopC);
        assertEquals(2, selectedModel.getSize());


        routeCreationDialog.getLstSelectedIntermediateStops().setSelectedValue(stopB, true);


        routeCreationDialog.getBtnRemoveStop().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnRemoveStop(), ActionEvent.ACTION_PERFORMED, "remove")
        );

        assertEquals(1, selectedModel.getSize());
        assertEquals(stopC, selectedModel.getElementAt(0));
        assertTrue(availableModel.contains(stopB));
    }

    @Test
    @DisplayName("Переміщення проміжної зупинки вгору")
    void moveStopUpAction_moveSelected() {
        initializeDialog();
        DefaultListModel<Stop> selectedModel = routeCreationDialog.getSelectedStopsModel();
        selectedModel.addElement(stopB);
        selectedModel.addElement(stopC);
        routeCreationDialog.getLstSelectedIntermediateStops().setSelectedValue(stopC, true);

        routeCreationDialog.getBtnMoveUp().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnMoveUp(), ActionEvent.ACTION_PERFORMED, "moveUp")
        );

        assertEquals(stopC, selectedModel.getElementAt(0));
        assertEquals(stopB, selectedModel.getElementAt(1));
        assertEquals(0, routeCreationDialog.getLstSelectedIntermediateStops().getSelectedIndex());
    }

    @Test
    @DisplayName("Переміщення проміжної зупинки вниз")
    void moveStopDownAction_moveSelected() {
        initializeDialog();
        DefaultListModel<Stop> selectedModel = routeCreationDialog.getSelectedStopsModel();
        selectedModel.addElement(stopB);
        selectedModel.addElement(stopC);
        routeCreationDialog.getLstSelectedIntermediateStops().setSelectedValue(stopB, true);

        routeCreationDialog.getBtnMoveDown().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnMoveDown(), ActionEvent.ACTION_PERFORMED, "moveDown")
        );

        assertEquals(stopC, selectedModel.getElementAt(0));
        assertEquals(stopB, selectedModel.getElementAt(1));
        assertEquals(1, routeCreationDialog.getLstSelectedIntermediateStops().getSelectedIndex());
    }


    @Test
    @DisplayName("Збереження: успішне створення маршруту")
    void saveRouteAction_success() {
        initializeDialog();
        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);

        DefaultListModel<Stop> selectedModel = routeCreationDialog.getSelectedStopsModel();
        selectedModel.addElement(stopB);
        selectedModel.addElement(stopC);

        routeCreationDialog.getBtnSave().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnSave(), ActionEvent.ACTION_PERFORMED, "save")
        );

        assertTrue(routeCreationDialog.isSaved());
        assertFalse(routeCreationDialog.isDisplayable());
        Route createdRoute = routeCreationDialog.getCreatedRoute();
        assertNotNull(createdRoute);
        assertEquals(stopA, createdRoute.getDepartureStop());
        assertEquals(stopE, createdRoute.getDestinationStop());
        assertEquals(2, createdRoute.getIntermediateStops().size());
        assertEquals(stopB, createdRoute.getIntermediateStops().get(0));
        assertEquals(stopC, createdRoute.getIntermediateStops().get(1));
    }

    @Test
    @DisplayName("Збереження: помилка валідації - не обрано відправлення")
    void saveRouteAction_validationError_noDeparture() {
        initializeDialog();

        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);

        routeCreationDialog.getBtnSave().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnSave(), ActionEvent.ACTION_PERFORMED, "save")
        );

        assertFalse(routeCreationDialog.isSaved());
        assertTrue(routeCreationDialog.isDisplayable());
        assertNull(routeCreationDialog.getCreatedRoute());
    }

    @Test
    @DisplayName("Збереження: помилка валідації - відправлення та призначення однакові")
    void saveRouteAction_validationError_departureEqualsDestination() {
        initializeDialog();
        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopA);

        routeCreationDialog.getBtnSave().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnSave(), ActionEvent.ACTION_PERFORMED, "save")
        );

        assertFalse(routeCreationDialog.isSaved());
        assertTrue(routeCreationDialog.isDisplayable());
    }

    @Test
    @DisplayName("Збереження: помилка валідації - проміжна збігається з відправленням")
    void saveRouteAction_validationError_intermediateEqualsDeparture() {
        initializeDialog();
        routeCreationDialog.getCmbDepartureStop().setSelectedItem(stopA);
        routeCreationDialog.getCmbDestinationStop().setSelectedItem(stopE);
        routeCreationDialog.getSelectedStopsModel().addElement(stopA);

        routeCreationDialog.getBtnSave().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnSave(), ActionEvent.ACTION_PERFORMED, "save")
        );

        assertFalse(routeCreationDialog.isSaved());
        assertTrue(routeCreationDialog.isDisplayable());
    }

    @Test
    @DisplayName("Натискання кнопки 'Скасувати'")
    void cancelButton_action() {
        initializeDialog();
        routeCreationDialog.getBtnCancel().getActionListeners()[0].actionPerformed(
                new ActionEvent(routeCreationDialog.getBtnCancel(), ActionEvent.ACTION_PERFORMED, "cancel")
        );

        assertFalse(routeCreationDialog.isSaved());
        assertFalse(routeCreationDialog.isDisplayable());
        assertNull(routeCreationDialog.getCreatedRoute());
    }
}
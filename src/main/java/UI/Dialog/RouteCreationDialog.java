package UI.Dialog;

import DAO.StopDAO;
import Models.Route;
import Models.Stop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Діалогове вікно для створення нового автобусного маршруту.
 * Дозволяє користувачеві вибрати зупинку відправлення, зупинку призначення
 * та додати проміжні зупинки.
 */
public class RouteCreationDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private JComboBox<Stop> cmbDepartureStop;
    private JComboBox<Stop> cmbDestinationStop;
    private JList<Stop> lstAvailableStops;
    private JList<Stop> lstSelectedIntermediateStops;
    private DefaultListModel<Stop> availableStopsModel;
    private DefaultListModel<Stop> selectedStopsModel;

    private JButton btnAddStop;
    private JButton btnRemoveStop;
    private JButton btnMoveUp;
    private JButton btnMoveDown;
    private JButton btnSave;
    private JButton btnCancel;

    private StopDAO stopDAO;
    private Route createdRoute = null;
    private boolean saved = false;

    private static final AtomicBoolean suppressMessagesForTesting = new AtomicBoolean(false);

    /**
     * Встановлює режим придушення повідомлень JOptionPane для тестування.
     * УВАГА: Використовуйте тільки в тестовому середовищі!
     * @param suppress true, щоб придушити повідомлення, false - щоб показувати.
     */
    public static void setSuppressMessagesForTesting(boolean suppress) {
        suppressMessagesForTesting.set(suppress);
        if (suppress) {
            logger.warn("УВАГА: Повідомлення JOptionPane придушені для тестування в RouteCreationDialog!");
        } else {
            logger.info("Режим придушення повідомлень JOptionPane вимкнено в RouteCreationDialog.");
        }
    }


    private void showDialogMessage(Component parentComponent, Object message, String title, int messageType) {
        if (!suppressMessagesForTesting.get()) {
            JOptionPane.showMessageDialog(parentComponent, message, title, messageType);
        } else {
            String typeStr = "";
            switch (messageType) {
                case JOptionPane.ERROR_MESSAGE: typeStr = "ERROR"; break;
                case JOptionPane.INFORMATION_MESSAGE: typeStr = "INFORMATION"; break;
                case JOptionPane.WARNING_MESSAGE: typeStr = "WARNING"; break;
                case JOptionPane.QUESTION_MESSAGE: typeStr = "QUESTION"; break;
                default: typeStr = "UNKNOWN (" + messageType + ")"; break;
            }
            logger.info("RouteCreationDialog JOptionPane придушено (тестовий режим): Титул='{}', Повідомлення='{}', Тип={}", title, message, typeStr);
        }
    }


    /**
     * Конструктор для створення діалогового вікна створення маршруту.
     *
     * @param owner Батьківське вікно (Frame), якому належить цей діалог.
     * @param stopDAO Об'єкт DAO для доступу до даних про зупинки.
     */
    public RouteCreationDialog(Frame owner, StopDAO stopDAO) {
        super(owner, "Створення нового маршруту", true);
        if (stopDAO == null) {
            logger.fatal("Критична помилка: StopDAO є null при ініціалізації RouteCreationDialog.");

            showDialogMessage(null, "Помилка ініціалізації діалогу: відсутній StopDAO.", "Критична помилка", JOptionPane.ERROR_MESSAGE);
            throw new IllegalArgumentException("StopDAO не може бути null.");
        }
        this.stopDAO = stopDAO;
        logger.info("Ініціалізація RouteCreationDialog.");
        initComponents();
        loadStops();
        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(600, 450));
    }

    /**
     * Ініціалізує компоненти користувацького інтерфейсу діалогового вікна.
     * Налаштовує макет, створює та розміщує елементи керування.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel mainStopsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0.1;
        mainStopsPanel.add(new JLabel("Зупинка відправлення:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0.9;
        cmbDepartureStop = new JComboBox<>();
        mainStopsPanel.add(cmbDepartureStop, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0.1;
        mainStopsPanel.add(new JLabel("Зупинка призначення:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0.9;
        cmbDestinationStop = new JComboBox<>();
        mainStopsPanel.add(cmbDestinationStop, gbc);

        JPanel intermediateStopsOuterPanel = new JPanel(new BorderLayout(0, 5));
        intermediateStopsOuterPanel.setBorder(BorderFactory.createTitledBorder("Проміжні зупинки"));

        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        JPanel availablePanel = new JPanel(new BorderLayout(0, 5));
        availablePanel.add(new JLabel("Доступні зупинки:", SwingConstants.CENTER), BorderLayout.NORTH);
        availableStopsModel = new DefaultListModel<>();
        lstAvailableStops = new JList<>(availableStopsModel);
        JScrollPane availableScrollPane = new JScrollPane(lstAvailableStops);
        availableScrollPane.setPreferredSize(new Dimension(200, 150));
        availablePanel.add(availableScrollPane, BorderLayout.CENTER);

        JPanel selectedPanel = new JPanel(new BorderLayout(0, 5));
        selectedPanel.add(new JLabel("Обрані проміжні зупинки:", SwingConstants.CENTER), BorderLayout.NORTH);
        selectedStopsModel = new DefaultListModel<>();
        lstSelectedIntermediateStops = new JList<>(selectedStopsModel);
        JScrollPane selectedScrollPane = new JScrollPane(lstSelectedIntermediateStops);
        selectedScrollPane.setPreferredSize(new Dimension(200, 150));
        selectedPanel.add(selectedScrollPane, BorderLayout.CENTER);

        listsPanel.add(availablePanel);
        listsPanel.add(selectedPanel);

        JPanel intermediateControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        btnAddStop = new JButton("Додати >>");
        btnRemoveStop = new JButton("<< Видалити");
        btnMoveUp = new JButton("Вгору");
        btnMoveDown = new JButton("Вниз");

        btnAddStop.addActionListener(this::addIntermediateStopAction);
        btnRemoveStop.addActionListener(this::removeIntermediateStopAction);
        btnMoveUp.addActionListener(this::moveStopUpAction);
        btnMoveDown.addActionListener(this::moveStopDownAction);

        intermediateControlsPanel.add(btnAddStop);
        intermediateControlsPanel.add(btnRemoveStop);
        intermediateControlsPanel.add(Box.createHorizontalStrut(20));
        intermediateControlsPanel.add(btnMoveUp);
        intermediateControlsPanel.add(btnMoveDown);

        intermediateStopsOuterPanel.add(listsPanel, BorderLayout.CENTER);
        intermediateStopsOuterPanel.add(intermediateControlsPanel, BorderLayout.SOUTH);

        add(mainStopsPanel, BorderLayout.NORTH);
        add(intermediateStopsOuterPanel, BorderLayout.CENTER);

        JPanel controlButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnSave = new JButton("Зберегти маршрут");
        btnCancel = new JButton("Скасувати");

        btnSave.addActionListener(this::saveRouteAction);
        btnCancel.addActionListener(e -> {
            logger.debug("Створення маршруту скасовано користувачем.");
            saved = false;

            dispose();
        });

        controlButtonsPanel.add(btnSave);
        controlButtonsPanel.add(btnCancel);
        add(controlButtonsPanel, BorderLayout.SOUTH);

        ItemListener stopChangeListener = e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                updateAvailableIntermediateStops();
            }
        };
        cmbDepartureStop.addItemListener(stopChangeListener);
        cmbDestinationStop.addItemListener(stopChangeListener);
        logger.debug("Компоненти RouteCreationDialog ініціалізовані.");
    }

    /**
     * Завантажує список зупинок з бази даних та заповнює відповідні
     * комбо-бокси та список доступних зупинок.
     * У випадку помилки, відображає повідомлення та деактивує кнопку збереження.
     */
    private void loadStops() {
        logger.debug("Завантаження зупинок для RouteCreationDialog.");
        try {
            List<Stop> allStops = stopDAO.getAllStops();
            if (allStops == null || allStops.isEmpty()) {
                logger.warn("Список всіх зупинок порожній або null. Неможливо створити маршрут.");
                showDialogMessage(this, "Список доступних зупинок порожній. Додайте зупинки перед створенням маршруту.", "Помилка даних", JOptionPane.ERROR_MESSAGE);
                btnSave.setEnabled(false);
                cmbDepartureStop.setEnabled(false);
                cmbDestinationStop.setEnabled(false);
                availableStopsModel.clear();
                return;
            }


            btnSave.setEnabled(true);
            cmbDepartureStop.setEnabled(true);
            cmbDestinationStop.setEnabled(true);

            cmbDepartureStop.removeAllItems();
            cmbDestinationStop.removeAllItems();

            cmbDepartureStop.addItem(null);
            cmbDestinationStop.addItem(null);

            for (Stop stop : allStops) {
                cmbDepartureStop.addItem(stop);
                cmbDestinationStop.addItem(stop);
            }
            updateAvailableIntermediateStops();
            logger.info("Успішно завантажено {} зупинок.", allStops.size());

        } catch (SQLException e) {
            logger.error("Помилка SQL при завантаженні зупинок для RouteCreationDialog.", e);
            showDialogMessage(this, "Не вдалося завантажити список зупинок: " + e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
            btnSave.setEnabled(false);
            cmbDepartureStop.setEnabled(false);
            cmbDestinationStop.setEnabled(false);
            availableStopsModel.clear();
        } catch (Exception e) {
            logger.error("Непередбачена помилка при завантаженні зупинок.", e);
            showDialogMessage(this, "Непередбачена помилка при завантаженні зупинок: " + e.getMessage(), "Помилка програми", JOptionPane.ERROR_MESSAGE);
            btnSave.setEnabled(false);
            cmbDepartureStop.setEnabled(false);
            cmbDestinationStop.setEnabled(false);
            availableStopsModel.clear();
        }
    }

    /**
     * Оновлює список доступних проміжних зупинок.
     * Зупинки, які вже обрані як відправлення, призначення або проміжні,
     * не відображаються у цьому списку.
     */
    private void updateAvailableIntermediateStops() {
        logger.trace("Оновлення списку доступних проміжних зупинок.");
        Stop departure = (Stop) cmbDepartureStop.getSelectedItem();
        Stop destination = (Stop) cmbDestinationStop.getSelectedItem();

        availableStopsModel.clear();
        try {

            List<Stop> allStops = stopDAO.getAllStops();
            if (allStops == null) allStops = new ArrayList<>();

            List<Stop> currentSelectedIntermediate = Collections.list(selectedStopsModel.elements());

            for (Stop stop : allStops) {
                boolean isDeparture = (departure != null && stop.equals(departure));
                boolean isDestination = (destination != null && stop.equals(destination));
                boolean isAlreadySelectedIntermediate = currentSelectedIntermediate.contains(stop);

                if (!isDeparture && !isDestination && !isAlreadySelectedIntermediate) {
                    availableStopsModel.addElement(stop);
                }
            }
        } catch (SQLException e) {
            logger.error("Помилка SQL при оновленні списку доступних проміжних зупинок.", e);

        } catch (Exception e) {
            logger.error("Непередбачена помилка при оновленні списку доступних проміжних зупинок.", e);
        }
    }

    /**
     * Обробник дії для кнопки "Додати >>".
     * Переміщує обрані зупинки зі списку доступних до списку обраних проміжних зупинок.
     *
     * @param e Подія дії (ActionEvent).
     */
    private void addIntermediateStopAction(ActionEvent e) {
        List<Stop> selectedFromAvailable = lstAvailableStops.getSelectedValuesList();
        if (!selectedFromAvailable.isEmpty()) {
            logger.debug("Додавання проміжних зупинок: {}", selectedFromAvailable.stream().map(Stop::getName).collect(Collectors.joining(", ")));
            for (Stop stop : selectedFromAvailable) {
                selectedStopsModel.addElement(stop);
                availableStopsModel.removeElement(stop);
            }
        } else {
            logger.trace("Спроба додати проміжну зупинку, але нічого не вибрано в доступних.");
        }
    }

    /**
     * Обробник дії для кнопки "<< Видалити".
     * Переміщує обрані зупинки зі списку обраних проміжних до списку доступних зупинок,
     * якщо вони не є поточною зупинкою відправлення або призначення.
     *
     * @param e Подія дії (ActionEvent).
     */
    private void removeIntermediateStopAction(ActionEvent e) {
        List<Stop> selectedForRemoval = lstSelectedIntermediateStops.getSelectedValuesList();
        if (!selectedForRemoval.isEmpty()) {
            logger.debug("Видалення проміжних зупинок: {}", selectedForRemoval.stream().map(Stop::getName).collect(Collectors.joining(", ")));
            for (Stop stop : selectedForRemoval) {
                selectedStopsModel.removeElement(stop);

                Stop departure = (Stop) cmbDepartureStop.getSelectedItem();
                Stop destination = (Stop) cmbDestinationStop.getSelectedItem();
                boolean isDeparture = (departure != null && stop.equals(departure));
                boolean isDestination = (destination != null && stop.equals(destination));

                if (!isDeparture && !isDestination && !availableStopsModel.contains(stop)) {
                    availableStopsModel.addElement(stop);
                }
            }
        } else {
            logger.trace("Спроба видалити проміжну зупинку, але нічого не вибрано в обраних.");
        }
    }

    /**
     * Обробник дії для кнопки "Вгору".
     * Переміщує обрану проміжну зупинку на одну позицію вгору у списку обраних.
     *
     * @param e Подія дії (ActionEvent).
     */
    private void moveStopUpAction(ActionEvent e) {
        int selectedIndex = lstSelectedIntermediateStops.getSelectedIndex();
        if (selectedIndex > 0) {
            logger.debug("Переміщення проміжної зупинки вгору: індекс {}", selectedIndex);
            Stop stopToMove = selectedStopsModel.getElementAt(selectedIndex);
            selectedStopsModel.remove(selectedIndex);
            selectedStopsModel.insertElementAt(stopToMove, selectedIndex - 1);
            lstSelectedIntermediateStops.setSelectedIndex(selectedIndex - 1);
        } else {
            logger.trace("Спроба перемістити зупинку вгору, але вона вже перша або нічого не вибрано.");
        }
    }

    /**
     * Обробник дії для кнопки "Вниз".
     * Переміщує обрану проміжну зупинку на одну позицію вниз у списку обраних.
     *
     * @param e Подія дії (ActionEvent).
     */
    private void moveStopDownAction(ActionEvent e) {
        int selectedIndex = lstSelectedIntermediateStops.getSelectedIndex();

        if (selectedIndex != -1 && selectedIndex < selectedStopsModel.getSize() - 1) {
            logger.debug("Переміщення проміжної зупинки вниз: індекс {}", selectedIndex);
            Stop stopToMove = selectedStopsModel.getElementAt(selectedIndex);
            selectedStopsModel.remove(selectedIndex);
            selectedStopsModel.insertElementAt(stopToMove, selectedIndex + 1);
            lstSelectedIntermediateStops.setSelectedIndex(selectedIndex + 1);
        } else {
            logger.trace("Спроба перемістити зупинку вниз, але вона вже остання або нічого не вибрано.");
        }
    }

    /**
     * Обробник дії для кнопки "Зберегти маршрут".
     * Валідує введені дані (зупинка відправлення, призначення, унікальність проміжних зупинок).
     * Якщо валідація успішна, створює об'єкт {@link Route}, встановлює прапорець {@code saved} в true
     * та закриває діалогове вікно.
     *
     * @param e Подія дії (ActionEvent).
     */
    private void saveRouteAction(ActionEvent e) {
        logger.info("Спроба зберегти новий маршрут.");
        Stop departureStop = (Stop) cmbDepartureStop.getSelectedItem();
        Stop destinationStop = (Stop) cmbDestinationStop.getSelectedItem();

        if (departureStop == null) {
            showDialogMessage(this, "Будь ласка, виберіть зупинку відправлення.", "Валідація", JOptionPane.WARNING_MESSAGE);
            logger.warn("Валідація не пройдена: зупинка відправлення не вибрана.");
            return;
        }
        if (destinationStop == null) {
            showDialogMessage(this, "Будь ласка, виберіть зупинку призначення.", "Валідація", JOptionPane.WARNING_MESSAGE);
            logger.warn("Валідація не пройдена: зупинка призначення не вибрана.");
            return;
        }
        if (departureStop.equals(destinationStop)) {
            showDialogMessage(this, "Зупинка відправлення та призначення не можуть бути однаковими.", "Валідація", JOptionPane.WARNING_MESSAGE);
            logger.warn("Валідація не пройдена: зупинка відправлення та призначення однакові.");
            return;
        }

        List<Stop> intermediateStops = new ArrayList<>();
        for (int i = 0; i < selectedStopsModel.getSize(); i++) {
            Stop intermediate = selectedStopsModel.getElementAt(i);
            if (intermediate.equals(departureStop) || intermediate.equals(destinationStop)) {
                showDialogMessage(this,
                        "Проміжна зупинка '" + intermediate.getName() + "' не може бути такою ж, як зупинка відправлення або призначення.",
                        "Валідація", JOptionPane.WARNING_MESSAGE);
                logger.warn("Валідація не пройдена: проміжна зупинка {} збігається з відправленням/призначенням.", intermediate.getName());
                return;
            }

            if (intermediateStops.contains(intermediate)) {
                showDialogMessage(this,
                        "Проміжна зупинка '" + intermediate.getName() + "' вже додана до списку.",
                        "Валідація", JOptionPane.WARNING_MESSAGE);
                logger.warn("Валідація не пройдена: дублікат проміжної зупинки {}.", intermediate.getName());
                return;
            }
            intermediateStops.add(intermediate);
        }


        createdRoute = new Route(0, departureStop, destinationStop, intermediateStops);
        saved = true;
        logger.info("Новий маршрут підготовлено до збереження: {}", createdRoute.getFullRouteDescription());

        dispose();
    }

    /**
     * Перевіряє, чи було збережено маршрут.
     *
     * @return {@code true}, якщо маршрут було успішно створено та збережено, інакше {@code false}.
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Повертає створений об'єкт маршруту.
     *
     * @return Об'єкт {@link Route}, якщо маршрут було створено та збережено;
     *         {@code null}, якщо створення було скасовано або сталася помилка.
     */
    public Route getCreatedRoute() {
        return createdRoute;
    }

    // Геттери для компонентів UI
    public JComboBox<Stop> getCmbDepartureStop() { return cmbDepartureStop; }
    public JComboBox<Stop> getCmbDestinationStop() { return cmbDestinationStop; }
    public JList<Stop> getLstAvailableStops() { return lstAvailableStops; }
    public JList<Stop> getLstSelectedIntermediateStops() { return lstSelectedIntermediateStops; }
    public DefaultListModel<Stop> getAvailableStopsModel() { return availableStopsModel; }
    public DefaultListModel<Stop> getSelectedStopsModel() { return selectedStopsModel; }
    public JButton getBtnAddStop() { return btnAddStop; }
    public JButton getBtnRemoveStop() { return btnRemoveStop; }
    public JButton getBtnMoveUp() { return btnMoveUp; }
    public JButton getBtnMoveDown() { return btnMoveDown; }
    public JButton getBtnSave() { return btnSave; }
    public JButton getBtnCancel() { return btnCancel; }
}
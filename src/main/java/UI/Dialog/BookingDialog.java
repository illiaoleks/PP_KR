package UI.Dialog;
import DAO.PassengerDAO;
import DAO.TicketDAO;
import Models.*;
import Models.Enums.BenefitType;
import Models.Enums.TicketStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Діалогове вікно для бронювання квитка.
 */
public class BookingDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private static final DateTimeFormatter DIALOG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Flight selectedFlight;
    private final String selectedSeat;

    private JTextField txtFullName, txtDocumentNumber, txtDocumentType, txtPhoneNumber, txtEmail;
    private JComboBox<BenefitType> cmbBenefitType;
    private JLabel lblFlightInfo, lblSeatInfo, lblPriceInfo;
    private JButton btnConfirmBooking, btnCancel;

    private final PassengerDAO passengerDAO;
    private final TicketDAO ticketDAO;
    private boolean bookingConfirmed = false;

    // Прапорець для придушення повідомлень під час тестів
    private static final AtomicBoolean suppressMessagesForTesting = new AtomicBoolean(false);

    /**
     * Встановлює режим придушення повідомлень JOptionPane для тестування.
     * УВАГА: Використовуйте тільки в тестовому середовищі!
     * @param suppress true, щоб придушити повідомлення, false - щоб показувати.
     */
    public static void setSuppressMessagesForTesting(boolean suppress) {
        suppressMessagesForTesting.set(suppress);
        if (suppress) {
            logger.warn("УВАГА: Повідомлення JOptionPane придушені для тестування!");
        } else {
            logger.info("Режим придушення повідомлень JOptionPane вимкнено.");
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
            logger.info("JOptionPane придушено (тестовий режим): Титул='{}', Повідомлення='{}', Тип={}", title, message, typeStr);
        }
    }

    /**
     * Конструктор діалогу бронювання.
     * @param owner Батьківське вікно.
     * @param flight Обраний рейс.
     * @param seat Обране місце.
     * @param passengerDAO DAO для пасажирів.
     * @param ticketDAO DAO для квитків.
     */
    public BookingDialog(Frame owner, Flight flight, String seat, PassengerDAO passengerDAO, TicketDAO ticketDAO) {
        super(owner, "Бронювання квитка", true);
        logger.info("Ініціалізація діалогу бронювання для рейсу ID: {} та місця: {}",
                (flight != null ? flight.getId() : "N/A"), seat);

        this.selectedFlight = flight;
        this.selectedSeat = seat;
        this.passengerDAO = passengerDAO;
        this.ticketDAO = ticketDAO;

        if (flight == null || seat == null || passengerDAO == null || ticketDAO == null) {
            logger.error("Критична помилка: Один з параметрів конструктора BookingDialog є null.");
            SwingUtilities.invokeLater(() -> {
                showDialogMessage(owner, "Помилка ініціалізації діалогу. Недостатньо даних.", "Критична помилка", JOptionPane.ERROR_MESSAGE);
                dispose();
            });
            return;
        }

        initComponents();
        setFlightAndSeatInfo();

        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        logger.debug("Діалог бронювання успішно ініціалізовано.");
    }

    public void initComponents() {
        logger.debug("Ініціалізація компонентів UI для діалогу бронювання.");
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Інформація про рейс та місце"));
        lblFlightInfo = new JLabel();
        lblSeatInfo = new JLabel();
        lblPriceInfo = new JLabel();
        infoPanel.add(lblFlightInfo);
        infoPanel.add(lblSeatInfo);
        infoPanel.add(lblPriceInfo);

        JPanel passengerPanel = new JPanel(new GridBagLayout());
        passengerPanel.setBorder(BorderFactory.createTitledBorder("Дані пасажира"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; passengerPanel.add(new JLabel("ПІБ:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; txtFullName = new JTextField(25); passengerPanel.add(txtFullName, gbc);

        gbc.gridx = 0; gbc.gridy = 1; passengerPanel.add(new JLabel("Тип документа:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; txtDocumentType = new JTextField(20); passengerPanel.add(txtDocumentType, gbc);

        gbc.gridx = 0; gbc.gridy = 2; passengerPanel.add(new JLabel("Номер документа:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; txtDocumentNumber = new JTextField(15); passengerPanel.add(txtDocumentNumber, gbc);

        gbc.gridx = 0; gbc.gridy = 3; passengerPanel.add(new JLabel("Телефон:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; txtPhoneNumber = new JTextField(15); passengerPanel.add(txtPhoneNumber, gbc);

        gbc.gridx = 0; gbc.gridy = 4; passengerPanel.add(new JLabel("Email (опціонально):"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; txtEmail = new JTextField(25); passengerPanel.add(txtEmail, gbc);

        gbc.gridx = 0; gbc.gridy = 5; passengerPanel.add(new JLabel("Пільга:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        cmbBenefitType = new JComboBox<>(BenefitType.values());
        cmbBenefitType.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BenefitType) {
                    setText(((BenefitType) value).getDisplayName());
                }
                return this;
            }
        });
        cmbBenefitType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                BenefitType selectedBenefit = (BenefitType) e.getItem();
                logger.trace("Обрано пільгу: {}", (selectedBenefit != null ? selectedBenefit.getDisplayName() : "null"));
                updatePriceInfo();
            }
        });
        passengerPanel.add(cmbBenefitType, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnConfirmBooking = new JButton("Підтвердити бронювання");
        btnCancel = new JButton("Скасувати");

        btnConfirmBooking.addActionListener(this::confirmBookingAction);
        btnCancel.addActionListener(e -> {
            logger.debug("Натиснуто кнопку 'Скасувати'. Закриття діалогу бронювання.");
            dispose();
        });

        buttonPanel.add(btnConfirmBooking);
        buttonPanel.add(btnCancel);

        add(infoPanel, BorderLayout.NORTH);
        add(passengerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        logger.debug("Компоненти UI успішно створені та додані до діалогу.");
    }

    private void setFlightAndSeatInfo() {
        logger.debug("Встановлення інформації про рейс та місце.");
        String routeDesc = (selectedFlight.getRoute() != null) ? selectedFlight.getRoute().getFullRouteDescription() : "N/A";
        String flightInfoText = String.format("Рейс: %s (%s - %s)",
                routeDesc,
                selectedFlight.getDepartureDateTime().format(DIALOG_DATE_TIME_FORMATTER),
                selectedFlight.getArrivalDateTime().format(DIALOG_DATE_TIME_FORMATTER)
        );
        lblFlightInfo.setText(flightInfoText);
        lblSeatInfo.setText("Обране місце: " + selectedSeat);
        logger.trace("Інформація про рейс встановлена: {}", flightInfoText);
        logger.trace("Інформація про місце встановлена: {}", lblSeatInfo.getText());
        updatePriceInfo();
    }

    private void updatePriceInfo() {
        BenefitType benefit = (BenefitType) cmbBenefitType.getSelectedItem();
        if (benefit == null) {
            logger.warn("Не вдалося отримати обрану пільгу з JComboBox. Встановлено пільгу NONE.");
            benefit = BenefitType.NONE;
            cmbBenefitType.setSelectedItem(BenefitType.NONE);
        }
        BigDecimal finalPrice = calculatePriceWithBenefit(selectedFlight.getPricePerSeat(), benefit);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("uk", "UA"));
        String priceText = "Ціна до сплати: " + currencyFormat.format(finalPrice);
        lblPriceInfo.setText(priceText);
        logger.debug("Ціну оновлено. Обрана пільга: {}, Кінцева ціна: {}", benefit.getDisplayName(), finalPrice);
    }

    public BigDecimal calculatePriceWithBenefit(BigDecimal basePrice, BenefitType benefitType) {
        logger.trace("Розрахунок ціни. Базова ціна: {}, Пільга: {}", basePrice, (benefitType != null ? benefitType.getDisplayName() : "null"));
        if (basePrice == null) {
            logger.warn("Базова ціна для розрахунку є null. Повертається 0.");
            return BigDecimal.ZERO;
        }
        if (benefitType == null || benefitType == BenefitType.NONE) {
            logger.trace("Пільга не застосовується або null. Ціна: {}", basePrice);
            return basePrice;
        }
        double discountPercentage = 0.0;
        switch (benefitType) {
            case STUDENT: discountPercentage = 0.20; break;
            case PENSIONER: discountPercentage = 0.15; break;
            case COMBATANT: discountPercentage = 0.50; break;
        }
        BigDecimal discount = basePrice.multiply(BigDecimal.valueOf(discountPercentage));
        BigDecimal finalPrice = basePrice.subtract(discount).setScale(2, BigDecimal.ROUND_HALF_UP);

        logger.trace("Застосовано знижку {}%. Кінцева ціна: {}", (int)(discountPercentage*100), finalPrice);
        return finalPrice;
    }

    private void confirmBookingAction(ActionEvent event) {
        logger.info("Спроба підтвердити бронювання.");
        String fullName = txtFullName.getText().trim();
        String docType = txtDocumentType.getText().trim();
        String docNumber = txtDocumentNumber.getText().trim();
        String phone = txtPhoneNumber.getText().trim();
        String email = txtEmail.getText().trim();
        BenefitType benefit = (BenefitType) cmbBenefitType.getSelectedItem();

        if (benefit == null) {
            benefit = BenefitType.NONE;
            logger.warn("Пільга була null під час підтвердження, встановлено NONE.");
        }

        logger.debug("Дані пасажира: ПІБ='{}', ТипДок='{}', НомерДок='{}', Телефон='{}', Email='{}', Пільга='{}'",
                fullName, docType, docNumber, phone, (email.isEmpty() ? "N/A" : email), benefit.getDisplayName());

        if (fullName.isEmpty() || docType.isEmpty() || docNumber.isEmpty() || phone.isEmpty()) {
            logger.warn("Помилка валідації даних пасажира: не заповнені обов'язкові поля.");
            showDialogMessage(this, "Будь ласка, заповніть всі обов'язкові поля пасажира (ПІБ, Тип та Номер документа, Телефон).", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            logger.debug("Створення об'єкта Passenger.");
            Passenger passenger = new Passenger(0, fullName, docNumber, docType, phone, email.isEmpty() ? null : email, benefit);
            logger.debug("Додавання або отримання пасажира з DAO.");
            long passengerId = passengerDAO.addOrGetPassenger(passenger);
            passenger.setId(passengerId);
            logger.info("Пасажир успішно оброблений. ID пасажира: {}", passengerId);

            BigDecimal finalPrice = calculatePriceWithBenefit(selectedFlight.getPricePerSeat(), benefit);
            logger.debug("Розрахована кінцева ціна квитка: {}", finalPrice);

            LocalDateTime bookingTime = LocalDateTime.now();
            LocalDateTime expiryTime = bookingTime.plusHours(24);
            logger.debug("Час бронювання: {}, Термін дії броні: {}", bookingTime, expiryTime);

            Ticket newTicket = new Ticket(
                    0,
                    selectedFlight,
                    passenger,
                    selectedSeat,
                    bookingTime,
                    finalPrice,
                    TicketStatus.BOOKED
            );
            newTicket.setBookingExpiryDateTime(expiryTime);
            logger.debug("Створено об'єкт Ticket для бронювання: {}", newTicket);

            logger.debug("Спроба додати квиток через TicketDAO.");
            if (ticketDAO.addTicket(newTicket)) {
                bookingConfirmed = true;
                logger.info("Квиток успішно заброньовано. ID квитка: {}. Місце: {}, Рейс ID: {}", newTicket.getId(), selectedSeat, selectedFlight.getId());
                showDialogMessage(this, "Місце " + selectedSeat + " успішно заброньовано!\nТермін дії броні: " + newTicket.getBookingExpiryDateTime().format(DIALOG_DATE_TIME_FORMATTER), "Бронювання успішне", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                logger.warn("Не вдалося забронювати місце (ticketDAO.addTicket повернув false). Місце: {}, Рейс ID: {}", selectedSeat, selectedFlight.getId());
                showDialogMessage(this, "Не вдалося забронювати місце. Можливо, воно вже зайняте або сталася помилка збереження.", "Помилка бронювання", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException ex) {
            logger.error("Помилка бази даних під час підтвердження бронювання. Рейс ID: {}, Місце: {}",
                    (selectedFlight != null ? selectedFlight.getId() : "N/A"), selectedSeat, ex);
            showDialogMessage(this,
                    "Помилка під час взаємодії з базою даних: " + ex.getMessage(),
                    "Помилка бази даних",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception exGeneral) {
            logger.error("Непередбачена помилка під час підтвердження бронювання. Рейс ID: {}, Місце: {}",
                    (selectedFlight != null ? selectedFlight.getId() : "N/A"), selectedSeat, exGeneral);
            showDialogMessage(this,
                    "Сталася непередбачена помилка: " + exGeneral.getMessage(),
                    "Внутрішня помилка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isBookingConfirmed() {
        logger.trace("Перевірка статусу підтвердження бронювання: {}", bookingConfirmed);
        return bookingConfirmed;
    }

    public JTextField getTxtFullName() { return txtFullName; }
    public JTextField getTxtDocumentType() { return txtDocumentType; }
    public JTextField getTxtDocumentNumber() { return txtDocumentNumber; }
    public JTextField getTxtPhoneNumber() { return txtPhoneNumber; }
    public JTextField getTxtEmail() { return txtEmail; }
    public JComboBox<BenefitType> getCmbBenefitType() { return cmbBenefitType; }
    public JLabel getLblFlightInfo() { return lblFlightInfo; }
    public JLabel getLblSeatInfo() { return lblSeatInfo; }
    public JLabel getLblPriceInfo() { return lblPriceInfo; }
    public JButton getBtnConfirmBooking() { return btnConfirmBooking; }
    public JButton getBtnCancel() { return btnCancel; }
}
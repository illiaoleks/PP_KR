package UI;

import UI.Panel.FlightsPanel;
import UI.Panel.PassengersPanel;
import UI.Panel.ReportsPanel;
import UI.Panel.TicketsPanel;
import DB.DatabaseConnectionManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Головне вікно програми.
 * Використовує JTabbedPane для організації різних функціональних модулів.
 */
public class MainFrame extends JFrame {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    private static final AtomicBoolean suppressMessagesForTesting = new AtomicBoolean(false);

    public static void setSuppressMessagesForTesting(boolean suppress) {
        suppressMessagesForTesting.set(suppress);
        if (suppress) {
            logger.warn("УВАГА: Повідомлення JOptionPane придушені для тестування в MainFrame!");
        } else {
            logger.info("Режим придушення повідомлень JOptionPane вимкнено в MainFrame.");
        }
    }

    public void showDialogMessage(Component parentComponent, Object message, String title, int messageType) {
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
            logger.info("MainFrame JOptionPane придушено (тестовий режим): Титул='{}', Повідомлення='{}', Тип={}", title, message, typeStr);
        }
    }

    public MainFrame() {
        logger.info("Ініціалізація головного вікна програми (MainFrame).");
        setTitle("Автоматизована система управління автовокзалом");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();
        logger.debug("Створено JTabbedPane.");


        logger.debug("Створення FlightsPanel...");
        FlightsPanel flightsPanel = new FlightsPanel();
        tabbedPane.addTab("Управління рейсами", createIcon("/icons/bus_schedule.png"), flightsPanel, "Операції з рейсами: створення, редагування, скасування");
        logger.debug("Створення TicketsPanel...");
        TicketsPanel ticketsPanel = new TicketsPanel();
        tabbedPane.addTab("Квитки", createIcon("/icons/ticket.png"), ticketsPanel, "Бронювання та продаж квитків");
        logger.info("Вкладку 'Квитки' додано.");



        logger.debug("Створення PassengersPanel...");
        PassengersPanel passengersPanel = new PassengersPanel();
        tabbedPane.addTab("Пасажири", createIcon("/icons/passengers.png"), passengersPanel, "Управління даними пасажирів та історія поїздок");
        logger.info("Вкладку 'Пасажири' додано.");

        logger.debug("Створення ReportsPanel...");
        ReportsPanel reportsPanel = new ReportsPanel();
        tabbedPane.addTab("Звітність", createIcon("/icons/report.png"), reportsPanel, "Перегляд звітів та статистики");
        logger.info("Вкладку 'Звітність' додано.");

        add(tabbedPane);
        pack();
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        logger.info("Головне вікно програми успішно налаштовано.");
    }

    public ImageIcon createIcon(String path) {
        logger.trace("Спроба завантажити іконку за шляхом: {}", path);
        java.net.URL imgURL = getClass().getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            logger.warn("Не вдалося знайти іконку: {}", path);
            return null;
        }
    }


    public static void setupLookAndFeel() {
        logger.debug("Спроба встановити FlatLaf LookAndFeel.");
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatIntelliJLaf());
            logger.info("FlatLaf LookAndFeel успішно встановлено.");
        } catch (Exception ex) {
            logger.warn("Не вдалося встановити FlatLaf LookAndFeel. Спроба використати Nimbus.", ex);
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        logger.info("Nimbus LookAndFeel успішно встановлено.");
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Не вдалося встановити Nimbus LookAndFeel. Буде використано стандартний L&F.", e);
            }
        }
    }

    public static boolean checkDatabaseConnection() {
        logger.debug("Перевірка підключення до бази даних.");
        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            if (conn == null || conn.isClosed()) {
                logger.fatal("Критична помилка: не вдалося підключитися до бази даних (з'єднання null або закрите).");
                if (!suppressMessagesForTesting.get()) {
                    JOptionPane.showMessageDialog(null,
                            "Не вдалося підключитися до бази даних. Програма не може продовжити роботу.\n" +
                                    "Перевірте налаштування в 'db.properties' та доступність сервера MySQL.",
                            "Критична помилка БД", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
            logger.info("Підключення до БД успішне.");
            return true;
        } catch (SQLException ex) {
            logger.fatal("Критична помилка підключення до бази даних.", ex);
            if (!suppressMessagesForTesting.get()) {
                JOptionPane.showMessageDialog(null,
                        "Помилка підключення до бази даних: " + ex.getMessage() + "\n" +
                                "Програма не може продовжити роботу. Перевірте консоль для деталей.",
                        "Критична помилка БД", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
    }


    static void createAndShowGUI() {
        if (!checkDatabaseConnection()) {
            System.exit(1);
            return;
        }

        logger.debug("Створення екземпляра MainFrame.");
        MainFrame mainFrameInstance = new MainFrame();
        mainFrameInstance.setVisible(true);
        logger.info("Головне вікно програми відображено.");
    }

    public static void main(String[] args) {
        logger.info("Запуск програми 'Автоматизована система управління автовокзалом'.");
        setupLookAndFeel();
        SwingUtilities.invokeLater(MainFrame::createAndShowGUI);
    }
}
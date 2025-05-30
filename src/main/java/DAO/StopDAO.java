package DAO;

import DB.DatabaseConnectionManager;
import Models.Stop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO для роботи з об'єктами Stop (Зупинки).
 * Надає методи для отримання даних про зупинки з бази даних.
 */
public class StopDAO {
    private static final Logger logger = LogManager.getLogger("insurance.log");

    /**
     * Повертає список всіх зупинок з бази даних.
     * @return Список об'єктів {@link Stop}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<Stop> getAllStops() throws SQLException {
        logger.info("Спроба отримати всі зупинки.");
        List<Stop> stops = new ArrayList<>();
        String sql = "SELECT id, name, city FROM stops ORDER BY city, name";
        logger.debug("Виконується SQL-запит для отримання всіх зупинок: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Stop stop = new Stop(rs.getLong("id"), rs.getString("name"), rs.getString("city"));
                stops.add(stop);
                logger.trace("Зупинку додано до списку: ID={}, Назва={}, Місто={}", stop.getId(), stop.getName(), stop.getCity());
            }
            logger.info("Успішно отримано {} зупинок.", stops.size());
        } catch (SQLException e) {
            logger.error("Помилка при отриманні всіх зупинок.", e);
            throw e;
        }
        return stops;
    }

    /**
     * Повертає зупинку за її ідентифікатором.
     * @param id Ідентифікатор зупинки.
     * @return Optional, що містить {@link Stop}, якщо зупинку знайдено, або порожній Optional, якщо не знайдено.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public Optional<Stop> getStopById(long id) throws SQLException {
        logger.info("Спроба отримати зупинку за ID: {}", id);
        String sql = "SELECT id, name, city FROM stops WHERE id = ?";
        logger.debug("Виконується SQL-запит для отримання зупинки за ID: {} з ID={}", sql, id);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Stop stop = new Stop(rs.getLong("id"), rs.getString("name"), rs.getString("city"));
                    logger.info("Зупинку з ID {} знайдено: ID={}, Назва={}, Місто={}", id, stop.getId(), stop.getName(), stop.getCity());
                    return Optional.of(stop);
                } else {
                    logger.info("Зупинку з ID {} не знайдено.", id);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            logger.error("Помилка при отриманні зупинки за ID {}.", id, e);
            throw e;
        }
    }
}
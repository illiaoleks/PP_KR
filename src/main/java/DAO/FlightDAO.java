package DAO;

import DB.DatabaseConnectionManager;
import Models.Flight;
import Models.Enums.FlightStatus;
import Models.Route;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO для роботи з об'єктами Flight (Рейси).
 */
public class FlightDAO {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    private final RouteDAO routeDAO;

    /**
     * Конструктор для FlightDAO з ін'єкцією RouteDAO (для тестування та гнучкості).
     * @param routeDAO DAO для роботи з маршрутами.
     * @throws SQLException якщо виникає помилка при створенні RouteDAO за замовчуванням (якщо передано null).
     */
    public FlightDAO(RouteDAO routeDAO) throws SQLException {
        if (routeDAO == null) {
            logger.warn("RouteDAO було передано як null в конструктор FlightDAO. Спроба створити екземпляр RouteDAO за замовчуванням.");
            // Спроба створити RouteDAO за замовчуванням, якщо його не надано.
            // Це може бути корисним для випадків, коли FlightDAO створюється не в тестовому середовищі
            // і не має прямої ін'єкції RouteDAO.
            this.routeDAO = new RouteDAO();
        } else {
            this.routeDAO = routeDAO;
        }
        logger.debug("FlightDAO ініціалізовано з наданим або створеним RouteDAO.");
    }

    /**
     * Конструктор за замовчуванням для FlightDAO.
     * Створює власний екземпляр RouteDAO.
     * @throws SQLException якщо не вдалося створити RouteDAO.
     */
    public FlightDAO() throws SQLException {
        this(new RouteDAO()); // Викликає інший конструктор з новим екземпляром RouteDAO
        logger.debug("FlightDAO створено з RouteDAO за замовчуванням.");
    }


    /**
     * Повертає список всіх рейсів з бази даних.
     * @return Список об'єктів {@link Flight}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<Flight> getAllFlights() throws SQLException {
        logger.info("Спроба отримати всі рейси.");
        List<Flight> flights = new ArrayList<>();
        String sql = "SELECT id, route_id, departure_date_time, arrival_date_time, total_seats, bus_model, price_per_seat, status FROM flights ORDER BY departure_date_time DESC";
        logger.debug("Виконується SQL-запит: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                long flightId = rs.getLong("id");
                long routeId = rs.getLong("route_id");
                Route route = this.routeDAO.getRouteById(routeId)
                        .orElseThrow(() -> {
                            String errorMsg = "Маршрут ID " + routeId + " не знайдено для рейсу ID: " + flightId;
                            logger.warn(errorMsg); // Логування попередження про цілісність даних
                            return new SQLException(errorMsg);
                        });

                String statusStr = rs.getString("status");
                FlightStatus flightStatus;
                if (statusStr == null) {
                    String errorMsg = "Статус рейсу є null для рейсу ID " + flightId;
                    logger.error(errorMsg);
                    throw new SQLException(errorMsg);
                }
                try {
                    flightStatus = FlightStatus.valueOf(statusStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    String errorMsg = "Недійсний статус '" + statusStr + "' для рейсу ID " + flightId;
                    logger.error(errorMsg, e);
                    throw new SQLException(errorMsg, e);
                }

                flights.add(new Flight(
                        flightId,
                        route,
                        rs.getTimestamp("departure_date_time").toLocalDateTime(),
                        rs.getTimestamp("arrival_date_time").toLocalDateTime(),
                        rs.getInt("total_seats"),
                        flightStatus,
                        rs.getString("bus_model"),
                        rs.getBigDecimal("price_per_seat")
                ));
            }
            logger.info("Успішно отримано {} рейсів.", flights.size());
        } catch (SQLException e) {
            logger.error("Помилка при отриманні всіх рейсів", e);
            throw e;
        }
        return flights;
    }

    /**
     * Додає новий рейс до бази даних.
     * @param flight Об'єкт {@link Flight} для додавання.
     * @return {@code true}, якщо рейс успішно додано та ID встановлено, {@code false} в іншому випадку.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public boolean addFlight(Flight flight) throws SQLException {
        logger.info("Спроба додати новий рейс.");
        String sql = "INSERT INTO flights (route_id, departure_date_time, arrival_date_time, total_seats, bus_model, price_per_seat, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        logger.debug("Виконується SQL-запит для додавання рейсу: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, flight.getRoute().getId());
            pstmt.setTimestamp(2, Timestamp.valueOf(flight.getDepartureDateTime()));
            pstmt.setTimestamp(3, Timestamp.valueOf(flight.getArrivalDateTime()));
            pstmt.setInt(4, flight.getTotalSeats());
            pstmt.setString(5, flight.getBusModel());
            pstmt.setBigDecimal(6, flight.getPricePerSeat());
            pstmt.setString(7, flight.getStatus().name());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        flight.setId(generatedKeys.getLong(1));
                        logger.info("Рейс успішно додано. ID нового рейсу: {}", flight.getId());
                        return true;
                    } else {
                        logger.warn("Рейс додано ({} рядків), але не вдалося отримати згенерований ID.", affectedRows);
                        return false; // Або кинути виняток, оскільки це несподівана ситуація
                    }
                }
            } else {
                logger.warn("Рейс не було додано (affectedRows = 0).");
                return false;
            }
        } catch (SQLException e) {
            logger.error("Помилка при додаванні рейсу", e);
            throw e;
        }
    }

    /**
     * Оновлює дані існуючого рейсу в базі даних.
     * @param flight Об'єкт {@link Flight} з оновленими даними.
     * @return {@code true}, якщо рейс успішно оновлено, {@code false} в іншому випадку.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public boolean updateFlight(Flight flight) throws SQLException {
        logger.info("Спроба оновити рейс з ID {}.", flight.getId());
        String sql = "UPDATE flights SET route_id = ?, departure_date_time = ?, arrival_date_time = ?, total_seats = ?, bus_model = ?, price_per_seat = ?, status = ? WHERE id = ?";
        logger.debug("Виконується SQL-запит для оновлення рейсу: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, flight.getRoute().getId());
            pstmt.setTimestamp(2, Timestamp.valueOf(flight.getDepartureDateTime()));
            pstmt.setTimestamp(3, Timestamp.valueOf(flight.getArrivalDateTime()));
            pstmt.setInt(4, flight.getTotalSeats());
            pstmt.setString(5, flight.getBusModel());
            pstmt.setBigDecimal(6, flight.getPricePerSeat());
            pstmt.setString(7, flight.getStatus().name());
            pstmt.setLong(8, flight.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Рейс з ID {} успішно оновлено.", flight.getId());
                return true;
            } else {
                logger.warn("Рейс з ID {} не знайдено або не було оновлено (affectedRows = 0).", flight.getId());
                return false;
            }
        } catch (SQLException e) {
            logger.error("Помилка при оновленні рейсу з ID {}", flight.getId(), e);
            throw e;
        }
    }

    /**
     * Оновлює статус рейсу.
     * @param flightId Ідентифікатор рейсу.
     * @param status Новий статус рейсу.
     * @return {@code true}, якщо статус успішно оновлено, {@code false} в іншому випадку.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public boolean updateFlightStatus(long flightId, FlightStatus status) throws SQLException {
        logger.info("Спроба оновити статус рейсу ID {} на {}", flightId, status);
        String sql = "UPDATE flights SET status = ? WHERE id = ?";
        logger.debug("Виконується SQL-запит для оновлення статусу рейсу: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setLong(2, flightId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Статус рейсу ID {} успішно оновлено на {}.", flightId, status);
                return true;
            } else {
                logger.warn("Рейс з ID {} не знайдено або статус не було оновлено (affectedRows = 0).", flightId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Помилка при оновленні статусу рейсу ID {}: {}", flightId, status, e);
            throw e;
        }
    }

    /**
     * Повертає кількість зайнятих місць (заброньованих або проданих) для конкретного рейсу.
     * @param flightId Ідентифікатор рейсу.
     * @return Кількість зайнятих місць.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public int getOccupiedSeatsCount(long flightId) throws SQLException {
        logger.info("Спроба отримати кількість зайнятих місць для рейсу ID {}.", flightId);
        String sql = "SELECT COUNT(id) FROM tickets WHERE flight_id = ? AND (status = 'BOOKED' OR status = 'SOLD')";
        logger.debug("Виконується SQL-запит: {}", sql);
        int count = 0;

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, flightId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                    logger.info("Кількість зайнятих місць для рейсу ID {}: {}", flightId, count);
                } else {
                    // Це малоймовірно для COUNT(*), але для повноти
                    logger.info("Не знайдено даних про зайняті місця для рейсу ID {}. Повертається 0.", flightId);
                }
            }
        } catch (SQLException e) {
            logger.error("Помилка при отриманні кількості зайнятих місць для рейсу ID {}", flightId, e);
            throw e;
        }
        return count;
    }

    /**
     * Повертає рейс за його ідентифікатором.
     * @param id Ідентифікатор рейсу.
     * @return Optional, що містить {@link Flight} якщо знайдено.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public Optional<Flight> getFlightById(long id) throws SQLException {
        logger.info("Спроба отримати рейс за ID: {}", id);
        String sql = "SELECT id, route_id, departure_date_time, arrival_date_time, total_seats, bus_model, price_per_seat, status FROM flights WHERE id = ?";
        logger.debug("Виконується SQL-запит: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long routeId = rs.getLong("route_id");
                    Route route = this.routeDAO.getRouteById(routeId)
                            .orElseThrow(() -> {
                                String errorMsg = "Маршрут ID " + routeId + " не знайдено для рейсу ID: " + id;
                                logger.warn(errorMsg);
                                return new SQLException(errorMsg);
                            });

                    String statusStr = rs.getString("status");
                    FlightStatus flightStatus;
                    if (statusStr == null) {
                        String errorMsg = "Статус рейсу є null для рейсу ID " + id;
                        logger.error(errorMsg);
                        throw new SQLException(errorMsg);
                    }
                    try {
                        flightStatus = FlightStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        String errorMsg = "Недійсний статус '" + statusStr + "' для рейсу ID " + id;
                        logger.error(errorMsg, e);
                        throw new SQLException(errorMsg, e);
                    }

                    Flight flight = new Flight(
                            rs.getLong("id"),
                            route,
                            rs.getTimestamp("departure_date_time").toLocalDateTime(),
                            rs.getTimestamp("arrival_date_time").toLocalDateTime(),
                            rs.getInt("total_seats"),
                            flightStatus,
                            rs.getString("bus_model"),
                            rs.getBigDecimal("price_per_seat")
                    );
                    logger.info("Рейс з ID {} знайдено.", id);
                    return Optional.of(flight);
                } else {
                    logger.info("Рейс з ID {} не знайдено.", id);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            logger.error("Помилка при отриманні рейсу за ID {}: {}", id, e);
            throw e;
        }
    }
    /**
     * Повертає список рейсів на конкретну дату.
     * @param date Дата, на яку потрібно знайти рейси.
     * @return Список об'єктів {@link Flight}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<Flight> getFlightsByDate(LocalDate date) throws SQLException {
        logger.info("Спроба отримати рейси на дату: {}", date);
        List<Flight> flightsOnDate = new ArrayList<>();
        String sql = "SELECT id, route_id, departure_date_time, arrival_date_time, total_seats, bus_model, price_per_seat, status " +
                "FROM flights WHERE DATE(departure_date_time) = ? ORDER BY departure_date_time";
        logger.debug("Виконується SQL-запит: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long flightId = rs.getLong("id");
                    long routeId = rs.getLong("route_id");
                    Route route = this.routeDAO.getRouteById(routeId)
                            .orElseThrow(() -> {
                                String errorMsg = "Маршрут ID " + routeId + " не знайдено для рейсу ID: " + flightId;
                                logger.warn(errorMsg);
                                return new SQLException(errorMsg);
                            });

                    String statusStr = rs.getString("status");
                    FlightStatus flightStatus;
                    if (statusStr == null) {
                        String errorMsg = "Статус рейсу є null для рейсу ID " + flightId;
                        logger.error(errorMsg);
                        throw new SQLException(errorMsg);
                    }
                    try {
                        flightStatus = FlightStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        String errorMsg = "Недійсний статус '" + statusStr + "' для рейсу ID " + flightId;
                        logger.error(errorMsg, e);
                        throw new SQLException(errorMsg, e);
                    }

                    flightsOnDate.add(new Flight(
                            flightId,
                            route,
                            rs.getTimestamp("departure_date_time").toLocalDateTime(),
                            rs.getTimestamp("arrival_date_time").toLocalDateTime(),
                            rs.getInt("total_seats"),
                            flightStatus,
                            rs.getString("bus_model"),
                            rs.getBigDecimal("price_per_seat")
                    ));
                }
            }
            logger.info("Успішно отримано {} рейсів на дату {}.", flightsOnDate.size(), date);
        } catch (SQLException e) {
            logger.error("Помилка при отриманні рейсів на дату {}: {}", date, e);
            throw e;
        }
        return flightsOnDate;
    }
}
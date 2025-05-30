
package DAO;

import DB.DatabaseConnectionManager;
import Models.*;
import Models.Enums.FlightStatus;
import Models.Enums.TicketStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO для роботи з об'єктами Ticket (Квитки).
 */
public class TicketDAO {
    private static final Logger logger = LogManager.getLogger("insurance.log");
    // Змінено: Видалено final та пряму ініціалізацію
    private FlightDAO flightDAO;
    private PassengerDAO passengerDAO;
    private RouteDAO routeDAO;

    public TicketDAO(FlightDAO flightDAO, PassengerDAO passengerDAO, RouteDAO routeDAO) {
        this.flightDAO = flightDAO;
        this.passengerDAO = passengerDAO;
        this.routeDAO = routeDAO;
    }


    public TicketDAO() throws SQLException {

        this.flightDAO = new FlightDAO();
        this.passengerDAO = new PassengerDAO();
        this.routeDAO = new RouteDAO();
    }


    /**
     * Повертає список заброньованих або проданих місць для конкретного рейсу.
     * @param flightId Ідентифікатор рейсу.
     * @return Список рядків з номерами зайнятих місць.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<String> getOccupiedSeatsForFlight(long flightId) throws SQLException {
        logger.info("Спроба отримати зайняті місця для рейсу ID: {}", flightId);
        List<String> occupiedSeats = new ArrayList<>();
        String sql = "SELECT seat_number FROM tickets WHERE flight_id = ? AND (status = 'BOOKED' OR status = 'SOLD')";
        logger.debug("Виконується SQL-запит: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, flightId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    occupiedSeats.add(rs.getString("seat_number"));
                }
            }
            logger.info("Знайдено {} зайнятих місць для рейсу ID: {}", occupiedSeats.size(), flightId);
        } catch (SQLException e) {
            logger.error("Помилка при отриманні зайнятих місць для рейсу ID {}:", flightId, e);
            throw e;
        }
        return occupiedSeats;
    }

    /**
     * Додає новий квиток (бронювання) до бази даних.
     * @param ticket Об'єкт {@link Ticket} для додавання.
     * @return {@code true}, якщо квиток успішно додано та ID встановлено, {@code false} в іншому випадку.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public boolean addTicket(Ticket ticket) throws SQLException {
        logger.info("Спроба додати новий квиток: Рейс ID={}, Пасажир ID={}, Місце={}",
                ticket.getFlight().getId(), ticket.getPassenger().getId(), ticket.getSeatNumber());
        String sql = "INSERT INTO tickets (flight_id, passenger_id, seat_number, booking_date_time, booking_expiry_date_time, price_paid, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        logger.debug("Виконується SQL-запит для додавання квитка: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, ticket.getFlight().getId());
            pstmt.setLong(2, ticket.getPassenger().getId());
            pstmt.setString(3, ticket.getSeatNumber());
            pstmt.setTimestamp(4, Timestamp.valueOf(ticket.getBookingDateTime()));
            if (ticket.getBookingExpiryDateTime() != null) {
                pstmt.setTimestamp(5, Timestamp.valueOf(ticket.getBookingExpiryDateTime()));
            } else {
                pstmt.setNull(5, Types.TIMESTAMP);
            }
            pstmt.setBigDecimal(6, ticket.getPricePaid());
            pstmt.setString(7, ticket.getStatus().name());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        ticket.setId(generatedKeys.getLong(1));
                        logger.info("Квиток успішно додано. ID нового квитка: {}", ticket.getId());
                        return true;
                    } else {
                        logger.warn("Квиток додано ({} рядків), але не вдалося отримати згенерований ID. Рейс ID={}, Місце={}",
                                affectedRows, ticket.getFlight().getId(), ticket.getSeatNumber());
                        return false;
                    }
                }
            } else {
                logger.warn("Квиток не було додано (affectedRows = 0). Рейс ID={}, Місце={}",
                        ticket.getFlight().getId(), ticket.getSeatNumber());
                return false;
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && (e.getSQLState().equals("23000") || e.getSQLState().equals("23505")) &&
                    e.getMessage() != null && e.getMessage().toLowerCase().contains("uq_ticket_flight_seat")) {
                logger.warn("Помилка додавання квитка: Місце {} на рейсі {} вже зайняте. Порушення обмеження uq_ticket_flight_seat.",
                        ticket.getSeatNumber(), ticket.getFlight().getId(), e);
                return false;
            }
            logger.error("Помилка SQL при додаванні квитка: Рейс ID={}, Місце={}",
                    ticket.getFlight().getId(), ticket.getSeatNumber(), e);
            throw e;
        }
    }

    /**
     * Оновлює статус квитка та, опціонально, дату покупки.
     * @param ticketId Ідентифікатор квитка.
     * @param newStatus Новий статус квитка.
     * @param purchaseDateTime Дата та час покупки (може бути null, якщо статус не 'SOLD').
     * @return {@code true}, якщо статус успішно оновлено, {@code false} в іншому випадку.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public boolean updateTicketStatus(long ticketId, TicketStatus newStatus, LocalDateTime purchaseDateTime) throws SQLException {
        logger.info("Спроба оновити статус квитка ID {} на {}. Дата покупки: {}", ticketId, newStatus, purchaseDateTime);
        String sql;
        if (newStatus == TicketStatus.SOLD && purchaseDateTime != null) {
            sql = "UPDATE tickets SET status = ?, purchase_date_time = ?, booking_expiry_date_time = NULL WHERE id = ?";
            logger.debug("SQL для оновлення статусу (SOLD): {}", sql);
        } else if (newStatus == TicketStatus.CANCELLED) {
            sql = "UPDATE tickets SET status = ?, booking_expiry_date_time = NULL WHERE id = ?";
            logger.debug("SQL для оновлення статусу (CANCELLED): {}", sql);
        } else {
            sql = "UPDATE tickets SET status = ? WHERE id = ?";
            logger.debug("SQL для оновлення статусу (інший): {}", sql);
        }

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            if (newStatus == TicketStatus.SOLD && purchaseDateTime != null) {
                pstmt.setTimestamp(2, Timestamp.valueOf(purchaseDateTime));
                pstmt.setLong(3, ticketId);
            } else {
                pstmt.setLong(2, ticketId);
            }
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Статус квитка ID {} успішно оновлено на {}.", ticketId, newStatus);
                return true;
            } else {
                logger.warn("Квиток з ID {} не знайдено або статус не було оновлено.", ticketId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Помилка при оновленні статусу квитка ID {}:", ticketId, e);
            throw e;
        }
    }

    /**
     * Повертає список всіх квитків для конкретного пасажира (історія поїздок).
     * @param passengerId Ідентифікатор пасажира.
     * @return Список об'єктів {@link Ticket}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<Ticket> getTicketsByPassengerId(long passengerId) throws SQLException {
        logger.info("Спроба отримати історію поїздок для пасажира ID: {}", passengerId);
        List<Ticket> tickets = new ArrayList<>();
        String sql = "SELECT t.id, t.flight_id, t.passenger_id, t.seat_number, t.booking_date_time, t.purchase_date_time, t.booking_expiry_date_time, t.price_paid, t.status, " +
                "f.departure_date_time AS flight_departure_date_time, f.arrival_date_time AS flight_arrival_date_time, f.total_seats AS flight_total_seats, f.bus_model AS flight_bus_model, f.price_per_seat AS flight_price_per_seat, f.status AS flight_status, " +
                "r.id AS route_id, r.departure_stop_id, r.destination_stop_id, " +
                "ds.name AS dep_stop_name, ds.city AS dep_stop_city, " +
                "as_s.name AS arr_stop_name, as_s.city AS arr_stop_city " +
                "FROM tickets t " +
                "JOIN flights f ON t.flight_id = f.id " +
                "JOIN routes r ON f.route_id = r.id " +
                "JOIN stops ds ON r.departure_stop_id = ds.id " +
                "JOIN stops as_s ON r.destination_stop_id = as_s.id " +
                "WHERE t.passenger_id = ? ORDER BY f.departure_date_time DESC";
        logger.debug("Виконується SQL-запит для історії поїздок: {}", sql);

        // Тепер this.passengerDAO буде моком у тестах
        Passenger passenger = this.passengerDAO.findById(passengerId)
                .orElseThrow(() -> {
                    String errorMsg = "Пасажира з ID " + passengerId + " не знайдено для історії поїздок.";
                    logger.error(errorMsg);
                    return new SQLException(errorMsg);
                });

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, passengerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logger.trace("Обробка рядка результату для квитка ID: {}", rs.getLong("id"));
                    Stop departureStop = new Stop(rs.getLong("departure_stop_id"), rs.getString("dep_stop_name"), rs.getString("dep_stop_city"));
                    Stop arrivalStop = new Stop(rs.getLong("destination_stop_id"), rs.getString("arr_stop_name"), rs.getString("arr_stop_city"));
                    Route route = new Route(rs.getLong("route_id"), departureStop, arrivalStop, new ArrayList<>());

                    Flight flight = new Flight(
                            rs.getLong("flight_id"), route,
                            rs.getTimestamp("flight_departure_date_time").toLocalDateTime(),
                            rs.getTimestamp("flight_arrival_date_time").toLocalDateTime(),
                            rs.getInt("flight_total_seats"), FlightStatus.valueOf(rs.getString("flight_status")),
                            rs.getString("flight_bus_model"), rs.getBigDecimal("flight_price_per_seat"));

                    Ticket ticket = new Ticket(
                            rs.getLong("id"), flight, passenger, rs.getString("seat_number"),
                            rs.getTimestamp("booking_date_time").toLocalDateTime(),
                            rs.getBigDecimal("price_paid"), TicketStatus.valueOf(rs.getString("status")));
                    Timestamp purchaseTs = rs.getTimestamp("purchase_date_time");
                    if (purchaseTs != null) ticket.setPurchaseDateTime(purchaseTs.toLocalDateTime());
                    Timestamp expiryTs = rs.getTimestamp("booking_expiry_date_time");
                    if (expiryTs != null) ticket.setBookingExpiryDateTime(expiryTs.toLocalDateTime());
                    tickets.add(ticket);
                    logger.trace("Квиток ID {} додано до списку історії.", ticket.getId());
                }
            }
            logger.info("Знайдено {} квитків для історії поїздок пасажира ID {}.", tickets.size(), passengerId);
        } catch (SQLException e) {
            logger.error("Помилка при отриманні історії поїздок для пасажира ID {}:", passengerId, e);
            throw e;
        }
        return tickets;
    }

    /**
     * Повертає список всіх квитків, опціонально фільтрованих за статусом.
     * @param statusFilter Статус для фільтрації (може бути null, щоб отримати всі квитки).
     * @return Список об'єктів {@link Ticket}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public List<Ticket> getAllTickets(TicketStatus statusFilter) throws SQLException {
        logger.info("Спроба отримати всі квитки. Фільтр за статусом: {}", statusFilter != null ? statusFilter.name() : "немає");
        List<Ticket> tickets = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT t.id, t.flight_id, t.passenger_id, t.seat_number, t.booking_date_time, t.purchase_date_time, t.booking_expiry_date_time, t.price_paid, t.status " +
                        "FROM tickets t "
        );
        List<Object> params = new ArrayList<>();
        if (statusFilter != null) {
            sqlBuilder.append("WHERE t.status = ? ");
            params.add(statusFilter.name());
        }
        sqlBuilder.append("ORDER BY t.booking_date_time DESC");
        String sql = sqlBuilder.toString();
        logger.debug("Виконується SQL-запит: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long ticketId = rs.getLong("id");
                    long fId = rs.getLong("flight_id");
                    long pId = rs.getLong("passenger_id");
                    logger.trace("Обробка рядка результату для квитка ID: {}", ticketId);
                    // Тепер this.flightDAO та this.passengerDAO будуть моками у тестах
                    Flight flight = this.flightDAO.getFlightById(fId)
                            .orElseThrow(() -> {
                                String errorMsg = "Рейс ID " + fId + " не знайдено для квитка ID: " + ticketId;
                                logger.error(errorMsg);
                                return new SQLException(errorMsg);
                            });
                    Passenger passenger = this.passengerDAO.findById(pId)
                            .orElseThrow(() -> {
                                String errorMsg = "Пасажира ID " + pId + " не знайдено для квитка ID: " + ticketId;
                                logger.error(errorMsg);
                                return new SQLException(errorMsg);
                            });

                    Ticket ticket = new Ticket(
                            ticketId, flight, passenger, rs.getString("seat_number"),
                            rs.getTimestamp("booking_date_time").toLocalDateTime(),
                            rs.getBigDecimal("price_paid"), TicketStatus.valueOf(rs.getString("status")));
                    Timestamp purchaseTs = rs.getTimestamp("purchase_date_time");
                    if (purchaseTs != null) ticket.setPurchaseDateTime(purchaseTs.toLocalDateTime());
                    Timestamp expiryTs = rs.getTimestamp("booking_expiry_date_time");
                    if (expiryTs != null) ticket.setBookingExpiryDateTime(expiryTs.toLocalDateTime());
                    tickets.add(ticket);
                    logger.trace("Квиток ID {} додано до загального списку.", ticket.getId());
                }
            }
            logger.info("Успішно отримано {} квитків. Фільтр за статусом: {}", tickets.size(), statusFilter != null ? statusFilter.name() : "немає");
        } catch (SQLException e) {
            logger.error("Помилка при отриманні всіх квитків. Фільтр за статусом: {}", statusFilter != null ? statusFilter.name() : "немає", e);
            throw e;
        }
        return tickets;
    }

    /**
     * Повертає статистику продажів (сума та кількість) за вказаний період, згруповану по маршрутах.
     * @param startDate Початкова дата періоду.
     * @param endDate Кінцева дата періоду.
     * @return Мапа, де ключ - назва маршруту, а значення - мапа {"totalSales": BigDecimal, "ticketCount": Integer}.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public Map<String, Map<String, Object>> getSalesByRouteForPeriod(LocalDate startDate, LocalDate endDate) throws SQLException {
        logger.info("Спроба отримати статистику продажів за маршрутами за період: {} - {}", startDate, endDate);
        Map<String, Map<String, Object>> salesData = new HashMap<>();
        String sql = "SELECT r.id as route_id, SUM(t.price_paid) as total_amount, COUNT(t.id) as tickets_sold " +
                "FROM tickets t " +
                "JOIN flights f ON t.flight_id = f.id " +
                "JOIN routes r ON f.route_id = r.id " +
                "WHERE t.status = 'SOLD' AND DATE(t.purchase_date_time) BETWEEN ? AND ? " +
                "GROUP BY r.id";
        logger.debug("Виконується SQL-запит для статистики продажів: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(startDate));
            pstmt.setDate(2, java.sql.Date.valueOf(endDate));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long routeId = rs.getLong("route_id");
                    // Тепер this.routeDAO буде моком у тестах
                    Optional<Route> routeOpt = this.routeDAO.getRouteById(routeId);
                    String routeDescription;
                    if (routeOpt.isPresent()) {
                        routeDescription = routeOpt.get().getFullRouteDescription();
                    } else {
                        routeDescription = "Невідомий або видалений маршрут (ID: " + routeId + ")";
                        logger.warn("Маршрут з ID {} не знайдено під час генерації звіту продажів, але для нього є дані.", routeId);
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("totalSales", rs.getBigDecimal("total_amount"));
                    data.put("ticketCount", rs.getInt("tickets_sold"));
                    salesData.put(routeDescription, data);
                    logger.trace("Додано дані продажів для маршруту '{}': сума {}, кількість {}", routeDescription, data.get("totalSales"), data.get("ticketCount"));
                }
            }
            logger.info("Статистику продажів за {} маршрутами отримано для періоду: {} - {}", salesData.size(), startDate, endDate);
        } catch (SQLException e) {
            logger.error("Помилка при отриманні статистики продажів за маршрутами за період: {} - {}", startDate, endDate, e);
            throw e;
        }
        return salesData;
    }

    /**
     * Повертає кількість квитків за кожним статусом.
     * @return Мапа, де ключ - {@link TicketStatus}, а значення - кількість квитків.
     * @throws SQLException якщо виникає помилка доступу до бази даних.
     */
    public Map<TicketStatus, Integer> getTicketCountsByStatus() throws SQLException {
        logger.info("Спроба отримати кількість квитків за статусами.");
        Map<TicketStatus, Integer> statusCounts = new HashMap<>();
        String sql = "SELECT status, COUNT(id) as count FROM tickets GROUP BY status";
        logger.debug("Виконується SQL-запит для кількості квитків за статусами: {}", sql);

        try (Connection conn = DatabaseConnectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String statusStr = rs.getString("status");
                try {
                    TicketStatus status = TicketStatus.valueOf(statusStr);
                    statusCounts.put(status, rs.getInt("count"));
                    logger.trace("Статус: {}, Кількість: {}", status, rs.getInt("count"));
                } catch (IllegalArgumentException e) {
                    logger.warn("Невідомий статус квитка '{}' знайдено в базі даних під час підрахунку.", statusStr, e);
                }
            }
        } catch (SQLException e) {
            logger.error("Помилка при отриманні кількості квитків за статусами.", e);
            throw e;
        }
        for (TicketStatus ts : TicketStatus.values()) {
            statusCounts.putIfAbsent(ts, 0);
        }
        logger.info("Кількість квитків за статусами отримана: {}", statusCounts);
        return statusCounts;
    }
}
package com.fadhli.simulation.repository;

import com.fadhli.simulation.model.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TicketEvent t SET t.availableTickets = t.availableTickets - 1, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :eventId AND t.availableTickets > 0")
    int decrementTicketStock(@Param("eventId") Long eventId);

    /**
     * Menambah stok tiket. Kapasitas total ikut naik supaya perbandingan terjual terhadap total
     * tetap masuk akal setelah stok ditambah di tengah simulasi.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE TicketEvent t SET t.availableTickets = t.availableTickets + :amount, "
            + "t.totalTickets = t.totalTickets + :amount, t.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE t.id = :eventId")
    int restock(@Param("eventId") Long eventId, @Param("amount") int amount);
}

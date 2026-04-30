package it.exprivia.prenotazioni.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PrenotazioneGroupAccessCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    public void replaceUserGroups(Long utenteId, List<Long> gruppoIds) {
        jdbcTemplate.update("DELETE FROM prenotazione_utente_gruppo_cache WHERE utente_id = ?", utenteId);
        batchInsert(
                "INSERT INTO prenotazione_utente_gruppo_cache (utente_id, gruppo_id) VALUES (?, ?)",
                utenteId,
                gruppoIds
        );
    }

    public void replacePostazioneGroups(Long postazioneId, List<Long> gruppoIds) {
        jdbcTemplate.update("DELETE FROM prenotazione_postazione_gruppo_cache WHERE postazione_id = ?", postazioneId);
        batchInsert(
                "INSERT INTO prenotazione_postazione_gruppo_cache (postazione_id, gruppo_id) VALUES (?, ?)",
                postazioneId,
                gruppoIds
        );
    }

    private void batchInsert(String sql, Long resourceId, List<Long> gruppoIds) {
        if (gruppoIds == null || gruppoIds.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(sql, gruppoIds, gruppoIds.size(), (PreparedStatement ps, Long gruppoId) -> {
            setStatementParameters(ps, resourceId, gruppoId);
        });
    }

    private void setStatementParameters(PreparedStatement ps, Long resourceId, Long gruppoId) throws SQLException {
        ps.setLong(1, resourceId);
        ps.setLong(2, gruppoId);
    }
}

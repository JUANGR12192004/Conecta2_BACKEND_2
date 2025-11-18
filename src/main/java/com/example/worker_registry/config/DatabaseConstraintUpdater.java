package com.example.worker_registry.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures database check constraints allow all negotiation states we use in code.
 * This is a lightweight, idempotent safeguard for environments where the original
 * constraint was created before adding nuevos estados (e.g. ASIGNADO).
 */
@Component
public class DatabaseConstraintUpdater {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConstraintUpdater.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConstraintUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureOfertaEstadoConstraint() {
        try {
            // Read current constraint definition (if any)
            String def = jdbcTemplate.query("SELECT pg_get_constraintdef(oid) AS def " +
                            "FROM pg_constraint " +
                            "WHERE conname = 'ofertas_estado_check'",
                    rs -> rs.next() ? rs.getString("def") : null);

            String expected = "CHECK (((estado)::text = ANY ((ARRAY['EN_NEGOCIACION'::character varying, 'PENDIENTE_DE_PAGO'::character varying, 'ASIGNADO'::character varying, 'RECHAZADA'::character varying, 'CANCELADA'::character varying])::text[])))";
            boolean needsUpdate = def == null || !def.contains("ASIGNADO");
            if (!needsUpdate) {
                return;
            }

            log.info("Updating ofertas_estado_check constraint to allow ASIGNADO/RECHAZADA/CANCELADA");
            jdbcTemplate.execute("ALTER TABLE ofertas DROP CONSTRAINT IF EXISTS ofertas_estado_check");
            jdbcTemplate.execute("""
                    ALTER TABLE ofertas ADD CONSTRAINT ofertas_estado_check
                    CHECK (estado IN ('EN_NEGOCIACION','PENDIENTE_DE_PAGO','ASIGNADO','RECHAZADA','CANCELADA'))
                    """);
        } catch (Exception ex) {
            log.warn("Could not update ofertas_estado_check constraint: {}", ex.getMessage());
        }
    }
}

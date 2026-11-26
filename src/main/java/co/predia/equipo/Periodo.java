package co.predia.equipo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** El lapso sobre el que se cuentan las ventas, en hora de Colombia: este mes, este año o todo el historial. */
public enum Periodo {
    MES, ANIO, TODO;

    /** Inicio del lapso (incluido). */
    public Instant desde(Clock reloj) {
        ZonedDateTime ahora = ZonedDateTime.now(reloj).truncatedTo(ChronoUnit.DAYS);
        return switch (this) {
            case MES -> ahora.withDayOfMonth(1).toInstant();
            case ANIO -> ahora.withDayOfYear(1).toInstant();
            case TODO -> Instant.parse("2000-01-01T00:00:00Z");
        };
    }

    /** Fin del lapso (excluido): el inicio del mes o del año siguiente. */
    public Instant hasta(Clock reloj) {
        ZonedDateTime ahora = ZonedDateTime.now(reloj).truncatedTo(ChronoUnit.DAYS);
        return switch (this) {
            case MES -> ahora.withDayOfMonth(1).plusMonths(1).toInstant();
            case ANIO -> ahora.withDayOfYear(1).plusYears(1).toInstant();
            case TODO -> Instant.parse("2200-01-01T00:00:00Z");
        };
    }
}

package co.predia.comprador;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El rango de la cuota inicial en el simulador de compra, con reglas explícitas. La cuota inicial mínima es un
 * porcentaje del precio (el de la inmobiliaria: más bajo para vivienda de interés social) y el máximo es lo que el
 * comprador tiene ahorrado, sin pasar nunca del precio. Si el ahorro no alcanza la mínima, se simula con la mínima
 * y se dice cuánto falta. Los números nunca los calcula el navegador ni la IA.
 */
public final class CalculadoraCompra {

    public record Rango(BigDecimal minima, BigDecimal maxima, boolean ahorroAlcanza, BigDecimal faltante) {
    }

    private CalculadoraCompra() {
    }

    public static Rango rango(BigDecimal precio, BigDecimal pctCuotaInicial, BigDecimal ahorro) {
        if (precio == null || precio.signum() <= 0) {
            throw new IllegalArgumentException("El inmueble no tiene un precio de venta.");
        }
        BigDecimal minima = precio.multiply(pctCuotaInicial).setScale(0, RoundingMode.UP);
        BigDecimal ahorroSeguro = ahorro == null ? BigDecimal.ZERO : ahorro;
        boolean alcanza = ahorroSeguro.compareTo(minima) >= 0;
        // Nunca se financia menos de un peso: la cuota inicial máxima es el precio menos uno.
        BigDecimal tope = precio.subtract(BigDecimal.ONE);
        BigDecimal maxima = alcanza ? ahorroSeguro.min(tope) : minima;
        return new Rango(minima, maxima.max(minima), alcanza,
                alcanza ? BigDecimal.ZERO : minima.subtract(ahorroSeguro).max(BigDecimal.ZERO));
    }

    /** Verifica que la cuota inicial elegida esté dentro del rango. */
    public static void validar(Rango rango, BigDecimal cuotaInicial) {
        if (cuotaInicial == null || cuotaInicial.compareTo(rango.minima()) < 0) {
            throw new IllegalArgumentException("La cuota inicial mínima es " + rango.minima().toPlainString() + " pesos.");
        }
        if (cuotaInicial.compareTo(rango.maxima()) > 0) {
            throw new IllegalArgumentException("La cuota inicial no puede superar " + rango.maxima().toPlainString()
                    + " pesos.");
        }
    }
}

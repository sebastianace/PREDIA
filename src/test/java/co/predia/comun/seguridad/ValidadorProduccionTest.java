package co.predia.comun.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorProduccionTest {

    private static MockEnvironment entornoSano() {
        return new MockEnvironment()
                .withProperty("predia.demo.habilitada", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("spring.datasource.password", "Xk93-mQ7vLp2-ZtR8wNc")
                .withProperty("predia.jwt.secreto", "h7Qz2LmV9xKp4WcR8tYb3NfD6sJgA1eUoXiB5vZk0TqM")
                .withProperty("predia.usuarios.url-publica", "https://predia.ejemplo.co");
    }

    @Test
    void unaConfiguracionDeProduccionCorrectaNoTieneProblemas() {
        assertThat(ValidadorProduccion.problemas(entornoSano())).isEmpty();
    }

    @Test
    void detectaTodoLoQueQuedoDeDesarrollo() {
        MockEnvironment e = new MockEnvironment()
                .withProperty("predia.demo.habilitada", "true")
                .withProperty("springdoc.swagger-ui.enabled", "true")
                .withProperty("spring.datasource.password", "predia")
                .withProperty("predia.jwt.secreto", "cambia-este-valor-por-uno-aleatorio-de-al-menos-32-caracteres")
                .withProperty("predia.usuarios.url-publica", "http://localhost:5173");

        assertThat(ValidadorProduccion.problemas(e)).hasSize(5);
    }

    @Test
    void sinConfigurarNadaEsInseguroPorDefecto() {
        // Si faltan las propiedades, la demo cuenta como encendida y la clave como vacía: no arranca.
        assertThat(ValidadorProduccion.problemas(new MockEnvironment())).isNotEmpty();
    }

    @Test
    void arrancarConProblemasLanzaUnErrorQueNombraCadaUno() {
        MockEnvironment e = entornoSano().withProperty("predia.demo.habilitada", "true");

        assertThatThrownBy(() -> new ValidadorProduccion(e).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_HABILITADA");
    }
}

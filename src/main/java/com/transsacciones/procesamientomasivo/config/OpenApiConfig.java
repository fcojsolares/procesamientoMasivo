package com.transsacciones.procesamientomasivo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Procesamiento Masivo de Transacciones")
                        .description("Microservicio para la carga, validación y procesamiento resiliente "
                                + "de archivos CSV de transacciones financieras.")
                        .version("1.0.0")
                        .contact(new Contact().name("Equipo Backend").email("backend@pruebatecnica.com"))
                        .license(new License().name("Uso interno")));
    }
}

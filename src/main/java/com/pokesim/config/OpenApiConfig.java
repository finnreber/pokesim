package com.pokesim.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pokesim API")
                        .version("v1")
                        .description("""
                                FireRed-style Pokemon battle simulator (Gen 3 mechanics, Kanto roster). \
                                Unofficial, non-commercial fan project; Pokemon is a trademark of Nintendo / Creatures / \
                                GAME FREAK / The Pokemon Company. Data from PokeAPI.

                                **Try it without an account:** run `POST /api/v1/simulate`.

                                **To use protected endpoints:** call `POST /api/v1/auth/register`, then `POST /api/v1/auth/login`, \
                                click **Authorize** above and paste the `accessToken`."""))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}

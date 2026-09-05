package in.chalkbase.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI chalkbaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chalkbase API")
                        .description("School management system for Indian K-12 schools")
                        .version("v1")
                        .license(new License().name("Proprietary")));
    }
}

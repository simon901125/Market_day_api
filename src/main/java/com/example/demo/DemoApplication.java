package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void printSwaggerUrl(ApplicationReadyEvent event) {
		Environment env = event.getApplicationContext().getEnvironment();
		String port = env.getProperty("server.port", "8080");
		String swaggerPath = env.getProperty("springdoc.swagger-ui.path", "/swagger-ui/index.html");
		String publicBaseUrl = env.getProperty("app.swagger.public-base-url", "").replaceAll("/+$", "");
		if (publicBaseUrl.isBlank()) {
			publicBaseUrl = "http://localhost:" + port;
		}
		System.out.println("Swagger UI: " + publicBaseUrl + swaggerPath);
	}

}

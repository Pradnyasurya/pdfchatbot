package com.surya.pdfchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PdfChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(PdfChatbotApplication.class, args);
	}

}

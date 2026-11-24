package br.com.erp.financeiro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Módulo Financeiro (Grupo 8): API em :8085, rotas sob /api/financeiro e /public/financeiro. */
@SpringBootApplication
@EnableScheduling
public class FinanceiroApplication {

  public static void main(String[] args) {
    SpringApplication.run(FinanceiroApplication.class, args);
  }
}

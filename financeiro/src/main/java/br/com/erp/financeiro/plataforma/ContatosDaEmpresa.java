package br.com.erp.financeiro.plataforma;

import java.util.Optional;
import java.util.UUID;

/** E-mail de contato da empresa para a régua (o dado é do CRM; o Financeiro não guarda, RN-03). */
public interface ContatosDaEmpresa {

  Optional<String> email(UUID tenant, UUID empresaId);
}

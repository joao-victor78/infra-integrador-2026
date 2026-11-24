package br.com.erp.financeiro.api;

/** Detalhe de um erro dentro do envelope: campo, código estável e texto para o usuário. */
public record ErroCampo(String campo, String codigo, String detalhe) {}

package br.com.erp.financeiro.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Formato de toda listagem paginada (Contrato §8.3): no máximo 100 itens por página. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

  public static final int TAMANHO_MAXIMO = 100;
  public static final int TAMANHO_PADRAO = 20;

  public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversor) {
    return new Pagina<>(pagina.getContent().stream().map(conversor).toList(),
        pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
  }

  /** Valida pagina/tamanho da query string: 400 fora dos limites, nunca corte silencioso. */
  public static Pageable pedido(Integer pagina, Integer tamanho, Sort ordem) {
    int p = pagina == null ? 0 : pagina;
    int t = tamanho == null ? TAMANHO_PADRAO : tamanho;
    if (p < 0) throw ApiException.campo("pagina", "PAGINA_INVALIDA", "pagina deve ser 0 ou maior.");
    if (t < 1 || t > TAMANHO_MAXIMO) {
      throw ApiException.campo("tamanho", "TAMANHO_INVALIDO", "tamanho deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
    }
    return PageRequest.of(p, t, ordem);
  }
}

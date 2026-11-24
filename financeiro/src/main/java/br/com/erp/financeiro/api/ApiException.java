package br.com.erp.financeiro.api;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Erro com o código HTTP da §8.4 do Contrato. Vira { success:false, message, errors }. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final transient List<ErroCampo> erros;

  public ApiException(HttpStatus status, String mensagem, List<ErroCampo> erros) {
    super(mensagem);
    this.status = status;
    this.erros = erros == null ? List.of() : erros;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public List<ErroCampo> getErros() {
    return erros;
  }

  /** 404: não existe ou é de outro tenant — os dois casos respondem igual (Contrato §8.4). */
  public static ApiException naoEncontrado(String recurso) {
    return new ApiException(HttpStatus.NOT_FOUND, recurso + " não encontrado(a).", List.of());
  }

  /** 400 com um campo. */
  public static ApiException campo(String campo, String codigo, String detalhe) {
    return new ApiException(HttpStatus.BAD_REQUEST, "Dados inválidos.", List.of(new ErroCampo(campo, codigo, detalhe)));
  }

  /** 409: estado não permite a operação ou duplicidade. */
  public static ApiException conflito(String codigo, String detalhe) {
    return new ApiException(HttpStatus.CONFLICT, detalhe, List.of(new ErroCampo(null, codigo, detalhe)));
  }

  /** 422: bem formado, mas viola regra de negócio. */
  public static ApiException regra(String codigo, String detalhe) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, detalhe, List.of(new ErroCampo(null, codigo, detalhe)));
  }
}

package br.com.erp.financeiro.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converte toda exceção no envelope padrão, com o código HTTP da §8.4 do Contrato. */
@RestControllerAdvice
public class TratadorDeErros {

  private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);
  private static final String DADOS_INVALIDOS = "Dados inválidos: confira os campos.";

  /** Código estável por tipo de validação. @Digits é a regra de 2 casas decimais (RN-07, RNF-02). */
  private static final Map<String, String> CODIGOS = Map.of(
      "Digits", "PRECISAO_EXCEDIDA",
      "NotNull", "OBRIGATORIO",
      "NotBlank", "OBRIGATORIO",
      "NotEmpty", "OBRIGATORIO",
      "Positive", "VALOR_INVALIDO",
      "PositiveOrZero", "VALOR_NEGATIVO",
      "Min", "VALOR_INVALIDO",
      "Max", "VALOR_INVALIDO",
      "Size", "TAMANHO_INVALIDO",
      "Pattern", "FORMATO_INVALIDO");

  static String codigo(String validacao) {
    return CODIGOS.getOrDefault(validacao, "CAMPO_INVALIDO");
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Resposta<Void>> negocio(ApiException e) {
    return ResponseEntity.status(e.getStatus()).body(Resposta.falha(e.getMessage(), e.getErros()));
  }

  /** @Valid no corpo (MethodArgumentNotValidException é subclasse de BindException). */
  @ExceptionHandler(BindException.class)
  ResponseEntity<Resposta<Void>> validacao(BindException e) {
    List<ErroCampo> erros = new ArrayList<>();
    for (FieldError f : e.getFieldErrors()) {
      erros.add(f.isBindingFailure()
          ? new ErroCampo(f.getField(), "FORMATO_INVALIDO", "Valor em formato inválido.")
          : new ErroCampo(f.getField(), codigo(f.getCode()), f.getDefaultMessage()));
    }
    e.getGlobalErrors().forEach(g -> erros.add(new ErroCampo(null, codigo(g.getCode()), g.getDefaultMessage())));
    boolean precisao = erros.stream().anyMatch(x -> "PRECISAO_EXCEDIDA".equals(x.codigo()));
    return ResponseEntity.badRequest().body(Resposta.falha(
        precisao ? "Valor monetário com mais de 2 casas decimais: nada foi gravado." : DADOS_INVALIDOS, erros));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<Resposta<Void>> validacaoParametros(HandlerMethodValidationException e) {
    List<ErroCampo> erros = e.getParameterValidationResults().stream()
        .flatMap(r -> r.getResolvableErrors().stream().map(m -> new ErroCampo(
            r.getMethodParameter().getParameterName(),
            m.getCodes() != null && m.getCodes().length > 0 ? codigo(ultimo(m.getCodes()[0])) : "CAMPO_INVALIDO",
            m.getDefaultMessage())))
        .toList();
    return ResponseEntity.badRequest().body(Resposta.falha(DADOS_INVALIDOS, erros));
  }

  private static String ultimo(String codigo) {
    int i = codigo.indexOf('.');
    return i < 0 ? codigo : codigo.substring(0, i);
  }

  /** JSON malformado ou valor no tipo errado: 400 apontando o campo, quando dá para saber. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Resposta<Void>> jsonInvalido(HttpMessageNotReadableException e) {
    if (e.getCause() instanceof JsonMappingException j && !j.getPath().isEmpty()) {
      String campo = j.getPath().stream()
          .map(r -> r.getFieldName() != null ? r.getFieldName() : String.valueOf(r.getIndex()))
          .collect(Collectors.joining("."));
      return ResponseEntity.badRequest().body(Resposta.falha(DADOS_INVALIDOS,
          List.of(new ErroCampo(campo, "FORMATO_INVALIDO", "Valor em formato inválido."))));
    }
    return ResponseEntity.badRequest().body(Resposta.falha("Corpo da requisição ausente ou JSON inválido."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Resposta<Void>> parametroInvalido(MethodArgumentTypeMismatchException e) {
    return ResponseEntity.badRequest().body(Resposta.falha(DADOS_INVALIDOS,
        List.of(new ErroCampo(e.getName(), "FORMATO_INVALIDO", "Valor em formato inválido."))));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<Resposta<Void>> parametroAusente(MissingServletRequestParameterException e) {
    return ResponseEntity.badRequest().body(Resposta.falha(DADOS_INVALIDOS,
        List.of(new ErroCampo(e.getParameterName(), "OBRIGATORIO", "Parâmetro obrigatório."))));
  }

  /** Lançada pelo @PreAuthorize: autenticado, mas sem a permissão exigida (RNF-05). */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Resposta<Void>> semPermissao(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Resposta.falha("Sem permissão para esta operação."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Resposta<Void>> integridade(DataIntegrityViolationException e) {
    if (e.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Resposta.falha("Registro duplicado.",
          List.of(new ErroCampo(null, "DUPLICADO", "Já existe um registro com estes dados."))));
    }
    log.error("Violação de integridade", e);
    return ResponseEntity.unprocessableEntity().body(Resposta.falha("A operação viola uma regra do banco de dados."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Resposta<Void>> inesperado(Exception e) {
    if (e instanceof ErrorResponse erroDoSpring) {
      // 404 de rota inexistente, 405 de método errado e afins: já trazem o código certo
      return ResponseEntity.status(erroDoSpring.getStatusCode())
          .body(Resposta.falha("Requisição não atendida por este endpoint."));
    }
    log.error("Erro inesperado", e);
    return ResponseEntity.internalServerError().body(Resposta.falha("Erro inesperado. Tente de novo em instantes."));
  }
}

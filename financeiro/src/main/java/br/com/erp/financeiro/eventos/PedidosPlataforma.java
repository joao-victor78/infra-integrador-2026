package br.com.erp.financeiro.eventos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pedidos à plataforma (timeline, notificação, e-mail) por mensagem em identity.entrada — nunca por
 * chamada direta a outro módulo (RN-33, contratos/identity.asyncapi.yaml). Passam pelo outbox.
 */
@Component
public class PedidosPlataforma {

  private final Outbox outbox;

  public PedidosPlataforma(Outbox outbox) {
    this.outbox = outbox;
  }

  /** Linha na timeline da empresa; o texto chega pronto para exibição (máx. 500). */
  public void timeline(UUID empresaId, String tipoDoFato, String texto, String rota) {
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("empresaId", empresaId);
    dados.put("tipoDoFato", tipoDoFato);
    dados.put("texto", limitar(texto, 500));
    dados.put("rota", rota);
    outbox.gravar(TiposDeEvento.TIMELINE_REGISTRAR, dados);
  }

  /** Notificação a um usuário da plataforma. Categoria do enum do identity (COBRANCA_VENCIDA, COBRANCA_PAGA...). */
  public void notificacao(UUID usuarioId, String categoria, String titulo, String texto, String rota) {
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("usuarioId", usuarioId);
    dados.put("categoria", categoria);
    dados.put("titulo", limitar(titulo, 120));
    dados.put("texto", limitar(texto, 500));
    dados.put("rota", rota);
    outbox.gravar(TiposDeEvento.NOTIFICACAO_CRIAR, dados);
  }

  /** E-mail transacional por modelo cadastrado na plataforma. */
  public void email(String para, String modelo, Map<String, String> variaveis) {
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("para", para);
    dados.put("modelo", modelo);
    dados.put("variaveis", variaveis);
    outbox.gravar(TiposDeEvento.EMAIL_ENVIAR, dados);
  }

  private static String limitar(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}

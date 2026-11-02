# Contratos HTTP e eventos — PDV-007

[Spec](../sdd/features/PDV-007-contratos-api-eventos.md). OpenAPI3.1 em [openapi.json](openapi.json), JSON Schema2020-12 em [negócio](business-events.v1.schema.json) e [diagnóstico](probe.v1.schema.json). [Exemplos sintéticos](examples.json). Contratos refletem59 operações HTTP atuais (56 controllers +3 health),52 DTOs e cinco tipos de negócio. API sem prefixo /api, Bearer opaco, sem cookies/JWT. O projeto não fornece conta/senha padrão.

Com Node22.12+ e npm10+, na raiz:

```powershell
npm ci --ignore-scripts
npm run contracts:verify
npm run contracts:docs
```

Abrir contracts/dist/index.html para navegar DTOs, endpoints, respostas e exemplos. Artefato gerado/ignorado, sem deploy. Ferramentas e dependências fixadas no lockfile. Fontes oficiais: [lint Redocly](https://redocly.com/docs/cli/commands/lint), [OpenAPI3.1](https://spec.openapis.org/oas/v3.1.0), [JSON Schema2020-12](https://json-schema.org/draft/2020-12).

O gerador scripts/build-contracts.mjs mantém definições explícitas revisáveis; executar após evolução autorizada e revisar o JSON. O verificador compara os mappings reais e os campos de52 records Java, compila65 schemas e valida exemplos positivos/negativos. Não interpreta todas as anotações de validação ou regras transacionais: CPF/CNPJ, bytes UTF-8 da senha (72), comprimentos Java UTF-16, escala decimal original, preço/estoque/versão, propriedade e provas duráveis continuam validados pelo servidor e suas integrações. Dinheiro e int64 são números JSON; usar decodificação que preserve precisão, especialmente fora da faixa segura de Number. Não converter valores monetários por double nem arredondar.

Erros conhecidos usam `{code}` com HTTP status, sem mensagens internas. Parâmetro obrigatório ausente retorna400 invalid_request; mídia incompatível415 unsupported_media_type. Autenticação401 também fornece WWW-Authenticate:Bearer. O contrato não promete que falhas HTTP desconhecidas ou respostas de um proxy sejam DTOs válidos. POST checkout exige Idempotency-Key; outras operações idempotentes usam requestId no corpo. Replay conserva resposta original, inclusive202 de reconciliação. If-Match/ETag de estoque mantém412/428 e a versão do corpo não é substituída por uma tag inventada.

## Eventos e compatibilidade

Envelopes de negócio têm type/eventId/paymentId/outcome. Type inclui versão maior (.v1); mudanças incompatíveis exigem novo tipo e migração coordenada de produtor/consumidor. Não reinterpretar mensagens antigas, alterar payload persistido ou reaproveitar EventId com outro conteúdo. Adição de campo também exige coordenação porque os consumidores atuais rejeitam propriedades desconhecidas.

| Tipo | Outcome | Etapa |
| --- | --- | --- |
| pdv.payment.requested.v1 | null | Solicitação/decisão persistida |
| pdv.payment.result.v1 | APROVADO/RECUSADO/INDETERMINADO | Aplicação do resultado original |
| pdv.sale.confirm.v1 | APROVADO | Baixa/comprovante039 |
| pdv.payment.reconciled.v1 | APROVADO/RECUSADO | Prova durável adicional040 |
| pdv.sale.release.v1 | RECUSADO | Liberação única040 |

AMQP: application/json UTF-8, corpo até4096bytes, MessageId igual ao EventId, produtor usa correlationId=paymentId, mensagem persistente/mandatory. Solicitação usa payment.request; demais etapas payment.result. Header __TypeId__ não escolhe classe Java. JSON Schema não valida headers, tamanho serializado, deduplicação ou prova de pagamento: [transporte/ACK/retry/DLQ](../docs/workflows/business-messaging.md), [conclusão](../docs/workflows/sale-confirmation.md) e [reconciliação](../docs/workflows/payment-reconciliation.md) documentam os invariantes. Snapshots históricos de auditoria permanecem JSON da versão SQL registrada; não são um DTO atual de edição.

pdv.probe.request.v1/pdv.probe.response.v1 são diagnóstico separado, com type/requestId/text (até256 unidades UTF-16). Não servem para solicitar pagamento. Nunca enviar dados pessoais ou credenciais nesses exemplos/filas.

## Fronteira pendente

Compras, cancelamento/estorno, relatórios e permissões futuras só ganham rotas/eventos depois de suas tarefas/refinamentos. CANCELAMENTO_PENDENTE/CANCELADA aparecem no vocabulário aprovado de venda, mas ainda não têm caminho público de mutação. Vocabulário de OC CRIADA/ENVIADA/RECEBIDA/CANCELADA e fornecedor máximo N:1 já foram aprovados na ADR-007; transições de compras não são inventadas neste contrato. Nada aqui declara entrega acadêmica, HA, TLS ou funcionalidade futura por existir um schema.

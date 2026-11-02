# PDV-035 — Criar checkout idempotente

## Estado e fonte

Leitura viva em 01/11/2026 (Manaus): [PDV-035](https://app.notion.com/p/4092c9a9991781ee8018f30608375839), Backlog, RNF08. Sem responsável ou prazo informado. Fonte original: [baseline textual, seções 7, 8 e 12](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md).

Aceite original: “Idempotency-Key persistida com fingerprint e resposta; retry não gera segunda venda/reserva.”

**Refinamento aprovado explicitamente pelo proprietário nesta conversa em 01/11/2026 (Manaus).** Checkout confirmado e estados/recorte 035–037 aprovados; implementação e testes registrados abaixo conforme execução.

## Contexto e objetivo

Converter um carrinho próprio em snapshot de venda e reserva atômicos, sem duplicar efeitos em retries ou concorrência. Carrinho já captura preços e calcula BigDecimal, mas não constitui venda nem reserva.

## Definition of Ready

Lidas ao vivo PDV-029 e PDV-034, ambas Em validação. Artefatos publicados: be78af8 (reserva/TTL) e e150f0a (carrinho). Não aceitar predecessoras implicitamente. Refinamento aprovado, DoR satisfeito para este incremento.

Pre-flight executado: main local/origin em e150f0a, checkout limpo; PostgreSQL/RabbitMQ PDV saudáveis; Migrate SUCCESS na V13. Nenhuma operação no workspace Amor e Vida.

## Refinamento aprovado

- ADMIN/OPERATOR fazem checkout e consultam somente suas vendas, com permissões próprias e sem acesso a vendas de outro ator, inclusive ADMIN.
- POST /carts/{id}/checkout recebe versão esperada, método fictício e cenário determinístico de pagamento. Idempotency-Key obrigatória, escopada por ator e operação, com fingerprint incluindo carrinho e conteúdo semântico.
- Cliente/produtos ativos, carrinho não vazio e disponibilidade são revalidados na transação. Preço atual diferente do capturado causa 409; operador revisa o carrinho antes de tentar novamente. Sem desconto ou arredondamento automático.
- Snapshot de venda/itens, reserva com TTL já aprovado, intenção de pagamento e resposta idempotente confirmam ou revertem juntos. Carrinho fica imutável para edição após checkout; UNIQUE por carrinho impede outra venda mesmo com outra chave.
- Chave/conteúdo conhecidos recuperam resposta original antes de revalidar estado mutável; conteúdo diferente retorna 409. Retenção sem expiração neste incremento, sem apagar proteção contra duplicação.
- Intenção durável ainda não é publicação RabbitMQ; transporte confiável pertence à PDV-038. Checkout não anuncia pagamento aprovado ou venda concluída.

## Fluxos e invariantes

Lock de chave → lock de carrinho → validações de cliente/produtos em ordem estável → reserva atômica de saldos em ordem de UUID → snapshot e intenção → auditoria/resposta. Locks e escrita usam a mesma transação PostgreSQL. Duas chaves para o mesmo carrinho não duplicam venda; dois carrinhos disputando o último saldo não causam overselling. Falha tardia reverte saldo, venda, itens, reserva, intenção e congelamento.

Dinheiro usa BigDecimal e persistência decimal capaz de representar os extremos já permitidos no carrinho. Não limitar silenciosamente quantidade/preço antes aceitos. Dados de usuário, valores ou estados não podem ser impostos pelo corpo além do contrato explícito.

## Contratos e fronteiras

201 com snapshot da venda e Idempotent-Replay; 400 para contrato inválido; 401/403 para autenticação/permissão; 404 para agregado alheio; 409 para conflito de chave, versão, preço, estado ou disponibilidade. Contrato final registrado em docs/workflows/checkout.md. GET próprio devolve estado persistido; resposta de replay conserva snapshot original.

Sem frontend, início efetivo de pagamento, baixa física, comprovante, liberação por recusa, reconciliação ou cancelamento. Sem sistema financeiro/fiscal externo.

## Plano de teste e migrações

Nova migration sequencial após V13, preservando scripts aplicados. PostgreSQL real: upgrade com dados V13, rebuild em schema próprio e replay sem pendências, constraints/FKs e históricos imutáveis. HTTP real para RBAC/propriedade, ativos, preço/valores exatos, versão, cabeçalho obrigatório, replay após alteração, divergência, duas chaves/mesmo carrinho, mesma chave concorrente, disputa de saldo e rollback por falha de auditoria.

Executar scripts/api.ps1 -Action Verify, Migrate e Validate; integridade de planejamento, diff, links e ausência de segredos; CI do SHA próprio. Congelar fontes antes de Verify.

## Evidências executadas

Verify SUCCESS em 9m49s: 21 unitários + 237 integrações = 258 testes, zero falhas/erros/skips e zero Checkstyle. Dez novos HTTP e um PG: snapshot, proteção do carrinho, replay após mutações, propriedade/RBAC/revogação, contrato/chave/versão, ativos/preço, lote indisponível, auditoria e rollback integral/retry, mesma chave concorrente, duas chaves/mesmo carrinho, disputa pelo último saldo e valores extremos. Upgrade V13→V14 preservou carrinho existente; rebuild/replay completos em schemas próprios PASS. Banco Verify removido no finally.

Migrate e Validate V14 SUCCESS no laboratório. Regressão de carga: 71.199 requisições, zero erros, p95 17,295142 ms; carga aprovada 10.000 produtos, 10 clientes, 10s aquecimento/60s medição. Resultado de laboratório, sem inferir produção. Fontes/testes permaneceram congelados durante Verify.

Autorrevisão: locks de chave/carrinho/produtos/saldos, DTO estrito/propriedade, constraints/FKs, rollback transacional, extremos decimais, imutabilidade e limites de pagamento/mensageria conferidos. CI e SHA publicados serão vinculados à própria tarefa viva após push.

## Revisão e aceite

main direta, sem PR, conforme ADR-001 e instrução do proprietário. Após implementação/testes/autorrevisão/CI, registrar commit e evidências na própria tarefa e usar Em validação, aguardando aceite explícito.

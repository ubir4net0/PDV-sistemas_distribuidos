# PDV-050 — Painel de filas e reprocessamento

## Fonte e dependências

Notion lido em 02/11/2026: https://app.notion.com/p/4092c9a999178138b32be01d2f8de9c5. RF-A02. Aceite: “Admin vê pendências, DLQ, última sincronização e reprocessa com segurança.” Dependências PDV-038/049: conferir entrega049 antes de iniciar. Responsável, datas, prioridade e critérios preservados.

## Contrato técnico para execução

Preflight repetido com main sincronizada e PG/Rabbit saudáveis; Migrate V19 aprovado antes do código. A049 possui API302/0, worker55/0 e browser final17/17 aprovados; apenas CI final do ajuste de teste segue em execução, sem mudança de aplicação em relação ao backend já aprovado. Artefatos suficientes para este incremento, sem aceite implícito. Não interpretar status/commit como aceite do proprietário.

GET /operations/messaging: ADMIN, no-store, contagens PENDING/DEAD/PUBLISHED, última inbox, amostras de filas e página de entregas (status opcional; page>=0, size1–100). POST /operations/messaging/{eventId}/retry: ADMIN, corpo requestId UUID/versão int64/motivo1–500;200 com snapshot requestId/eventId/status/versão/horário, header Idempotent-Replay. Leitura sem corpo de mensagem, nome de cliente, valor de venda ou motivo da decisão financeira. Erros400/401/403/404/409/503 seguros.

ADMIN consulta visão limitada de outbox PENDING/DEAD/PUBLISHED, tentativas, revisão, erro permitido e horários; inbox informa última sincronização. Worker consulta filas do namespace de negócio configurado via AMQP passivo, persiste contagens/consumidores/horário, marca indisponibilidade sem apresentar contagem antiga como atual. Nada de corpos ou listagem de filas alheias.

Reprocessamento só republica o envelope canônico imutável já existente na outbox, com ID original e deduplicação inbox preservada. Não consome/remove DLQ, aceita payload arbitrário ou muda estado financeiro. ADMIN envia requestId, versão e motivo: operação auditada e idempotente; mesma chave/conteúdo retorna resposta original, divergência409. Só DEAD ou PUBLISHED ainda sem inbox no consumidor esperado; PENDING/concluído409, CAS e lock impedem dois comandos efetivos sobre a mesma revisão. Reset para PENDING/0 respeita limite de cinco tentativas do worker. Falha depois do publish pode repetir entrega; efeito continua protegido por identidades do domínio.

DLQ conta mensagens inclusive inválidas; não é mapa garantido evento→DLQ. Para uma mensagem sem envelope canônico recuperável, este painel não pode inventar um comando; diagnóstico e correção continuam manuais, sem purge. UI explica isso e requer confirmação contextual no próprio formulário de motivo. Timeout de comando mantém mesmo requestId/corpo para retry, sem gerar nova tentativa financeira.

## Plano de teste

Spec final e preflight antes do código. V20: upgrade/rebuild/replay, auditoria imutável e rollback. HTTP real/PG: ADMIN/OP/sem sessão, idempotência, versão, concorrência, motivo e concluído rejeitados, nenhum payload exposto. AMQP real: contagens/DLQ/falha do broker, replay do mesmo evento sem duplicar baixa/comprovante. Browser administrativo: carregar, falha, reprocessar e resposta perdida; testes e commit separados da049. Artefatos e limites registrados antes de Em validação; aceite só pelo proprietário.

## Evidências

Execução iniciada após evidências locais e artefatos da049. Resultados próprios pendentes.

Primeiro Verify worker encontrou erro de bootstrap: injeção do tipo concreto RabbitAdmin não era resolvida antes da configuração automática. Monitor passou a construir o cliente de inspeção com a ConnectionFactory existente, sem nova conexão/credencial ou declaração de filas. Repetição completa necessária.

Primeiro browser18/18 PASS1m48: comando real200 com resposta descartada, navegação e replay200/Idempotent-Replay preservando exatamente corpo/chave, uma auditoria/baixa/comprovante e físico reduzido uma vez. Fixture inicial DEAD é sintética e atômica no PG UUID/tmpfs, criada somente durante esse teste; esgotamento real de publicação é coberto no worker. Houve ajuste final de seleção após409 durante a rodada inicial; a repetição final com fontes congeladas é obrigatória e está em execução. Desktop/celular inspecionados sem overflow.

Migrate/Validate V20 PASS; contratos63 operações/64 conjuntos de campos Java/77 schemas/15 exemplos válidos e11 inválidos, planejamento/fontes/355links/UTF-8/diff PASS. Verificações completas de API/worker e CI do SHA final ainda pendentes. Specs e status finais ficam na própria tarefa, sem concluir predecessoras.

Repetição final frontend: typecheck/23 unit/build e18 Playwright PASS1m54 com fontes congeladas, mesmas provas de perda de resposta/replay/navegação e efeito único. Recursos próprios removidos. Worker corrigido passou20 integrações de mensageria, incluindo DLQ real e indisponibilidade sem zero falso; demais modos do Verify ainda em execução no momento do commit. API passou24 HTTP de checkout (4 novos), upgrade19→20 e rebuild/replay, sem falha nas integrações executadas até aqui; suíte global ainda em execução. Autorrevisão e staged/segredos aprovados antes do commit main sem PR. Resultados completos e CI serão registrados no Notion antes de Em validação; aceite explícito pendente.

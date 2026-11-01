# PDV-028 — Versões e ETags de estoque

## Estado, fonte e aceite original

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a9991781c39371e151bd0ba8af. Responsável/prazo não informados. RNF03. Aceite original: “Campo version/If-Match; conflito 409/412; dois escritores concorrentes não perdem atualização.” PDV-026 c1c8ca7/188 testes/CI SUCCESS e PDV-027 4671e39/207 testes/Backend quality41262600342/Planning integrity41262600321 SUCCESS entregues, Em validação sem aceite inferido. Main sincronizada/serviços saudáveis/Migrate V11 SUCCESS antes do código.

## Contexto, fronteiras e contrato planejado

Saldo tem versão independente dos metadados do produto. GET /inventory/{productId} retorna ETag forte "inventory-UUID-vVERSION". PATCH na mesma URI aplica comando {requestId,type,quantity,reason,version?}, com If-Match opcional; preserva os POST de movimentos/lote da PDV-027 com version obrigatório. ETag corresponde à representação de saldo, por isso PATCH usa a mesma URI do GET, sem reaproveitar tag como se fosse representação da coleção de movimentos.

GET/PATCH usam comparação forte, lista de tags e wildcard segundo [RFC9110 §13.1.1](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match). Tag fraca não satisfaz comparação; nenhuma tag atual resulta412, sintaxe inválida400. Wildcard exige representação existente, mas sozinho não fixa versão: PATCH ainda requer versão explícita no corpo ou uma tag concreta correspondente; sem isso428. Corpo version obsoleto resulta409. Avaliar If-Match antes da condição do corpo, e ambos novamente diante de disputa no UPDATE condicional. Cabeçalho limitado a4096 caracteres/32 tags, com parsing de strings opacas entre aspas (incluindo vírgula interna).

Sem header, PATCH com version preserva guarda409; sem nenhum,428. Condição satisfeita com version ausente fixa a versão observada e o UPDATE continua condicional, sem retry cego. Se outro escritor vencer e a tag deixar de corresponder,412. Respostas200/ETag de GET/PATCH refletem o mesmo DTO Balance, Cache-Control:no-store. POST legado continua201/version e não aceita If-Match para evitar ignorar precondição na URI errada; lote usa versão por item.

Escrita condicional no PostgreSQL, sem check-then-write inseguro; lote usa versão por item e reverte integralmente se uma delas divergir. Fingerprint legado da PDV-027 é preservado; PATCH tem namespace/fingerprint próprio, incluindo condição canônica e versão de corpo original nullable. Repetição idempotente confirmada pela identidade/ator e conteúdo devolve resultado original, mesmo se a precondição já estiver velha; não reaplica efeito. PATCH repetido retorna200/balance original e Idempotent-Replay:true, sem ETag de estado atual: cliente faz GET antes de nova escrita. Isso usa a exceção de operação comprovadamente já aplicada da RFC9110, sem concluir sucesso por igualdade de saldo. Reservas, projeções, frontend, cache de negócio, reenvio automático após conflito e escrita sem revisão explícita estão fora do escopo.

## Plano de teste e migrações

Dois clientes leem mesma versão/ETag e escrevem simultaneamente: exatamente um efeito e outro 409/412. ETags de recursos diferentes não autorizam escrita, formato/precondições corretos, idempotência não duplica efeitos, alterações distintas de produto não invalidam versão de saldo, lote com conflito reverte tudo. PostgreSQL/HTTP reais, regressões completas, migration/rebuild/replay somente se novo DDL necessário; planejamento/links/diff/segredos, autorevisão, commit/CI próprios.

## Evidências, revisão e aceite

Leitura viva, spec/refinamento e preflight executados. `powershell -NoProfile -File scripts/api.ps1 -Action Verify`: BUILD SUCCESS em 10m13s, 21 unitários + 199 integrações = 220 testes, zero falhas/erros/skips; lint zero. Os 13 testes HTTP novos cobrem tags fortes/fracas/estrangeiras, listas com vírgula opaca, sintaxe/limites, wildcard, ausência de revisão, prioridade 412/409, dois escritores, replay concorrente/conhecido sem ETag enganosa, rollback de lote, compatibilidade legada, metadados e revogação de autoridade. PostgreSQL 16.15 real, Java 21.0.12.1, HTTP real e banco descartável próprio removido ao final. Sem novo DDL; V1–V11 preservadas. JAR contém 11 migrations e nenhuma fixture/classe de teste.

Regressão de carga da PDV-021: 10.000 produtos sintéticos, 10 clientes, 10s de aquecimento e 60s de medição; 68.169 requisições/zero erros, p95 18,130673ms. Resultado de laboratório, sem inferir capacidade de produção. Planejamento: 60 IDs/títulos/aceites/dependências, DAG, 19 RF/20 RNF e três hashes originais aprovados; cinco documentos UTF-8/45 links locais válidos e diff sem whitespace inválido. Autorevisão preserva fingerprints legados, guards e invariantes SQL; sem reservas/frontend/Saga nem aceite inferido. RFC9110 e API oficial HttpHeaders consultadas. Publicação e CI do SHA serão vinculadas na tarefa viva; a entrega fica disponível para aceite do proprietário.


Smoke do JAR no perfil development, sem bootstrap: readiness UP, GET anônimo de /inventory 401; contagens locais de users/products/balances/operations/movements todas zero. Container temporário encerrado/removido, runner exit 0. Primeira sondagem precedeu readiness; após aguardar a inicialização, o endpoint público correto /actuator/health/readiness confirmou UP.

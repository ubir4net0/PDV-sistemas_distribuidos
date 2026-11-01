# PDV-027 — Entradas, saídas e ajustes atômicos

## Estado, fonte e aceite original

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a99917814faa7bd4b02551120e. Responsável/prazo não informados. RF-E02. Aceite original: “Transações com movimentos imutáveis, motivo e usuário; lote tudo-ou-nada ou erro por item definido.” PDV-026 entregue em c1c8ca7 com188 testes/CI Backend quality41261528485 e Planning integrity41261528408 SUCCESS; Em validação, sem aceite inferido. DEC-05 foi resolvida neste escopo pelo refinamento confirmado. Preflight main/serviços/Migrate V10 aprovado.

## Refinamento confirmado pelo proprietário

Proprietário respondeu “Confirmar esse refinamento” e “Usar quantidades inteiras”. Somente ADMIN com inventory.write registra entradas/saídas/ajustes; ADMIN/OPERATOR com inventory.read consultam. Lote tudo-ou-nada: qualquer erro reverte saldos, versões, operação e movimentos de todos os itens. Entrada/saída recebem quantidade positiva; ajuste informa o novo saldo físico absoluto (não delta), nunca abaixo do reservado. Motivo obrigatório por item e ator da autenticação; cliente não informa ator ou reservado. Produtos de qualquer estado admitem correção de estoque, sem alterar suas regras de venda. Quantidades inteiras, sem arredondamento de frações.

## Fronteiras e contratos planejados

POST /inventory/{productId}/movements recebe requestId UUID, type ENTRY/EXIT/ADJUST, quantity inteira, reason (1–500, aparado) e version não negativa. POST /inventory/movements/batches recebe requestId e items (1–100) com productId e demais campos. Produto repetido no lote é400; ordem canônica de processamento/resultado é UUID textual crescente. GET /inventory/{productId}/movements retorna Page, ordenada por versão do saldo após movimento. Sem PUT/PATCH/DELETE de movimentos. Trilha guarda antes/depois, variação, tipo, motivo, ator e operação; registros imutáveis por trigger no banco.

SQL crítico na transação: UPDATE condicional por productId/version, disponibilidade/reservado e limites BIGINT, em ordem determinística para o lote. O lock natural do UPDATE e sua condição protegem concorrência, sem depender só de leitura prévia. Version obsoleta409, falta de disponível409, ajuste abaixo do reservado409, overflow de quantidade/versão409; inválidos400, ausência404, persistência503. Qualquer falha reverte lote completo. Frações JSON são rejeitadas antes do domínio, sem truncamento.

V11 cria inventory_operations e inventory_movements, com FKs/constraints/imutabilidade, ator consistente por FK composta e uma versão de movimento por produto. RequestId é escopado por ator; advisory lock transacional PostgreSQL da chave evita corrida de repetição. Mesma chave/ator e mesmo conteúdo canônico retorna201 com corpo original e Idempotent-Replay:true, sem novo efeito. Chave com conteúdo diferente409 request_conflict. Canonicalização preserva tipos/valores, normaliza motivo e ordena itens por UUID; SHA256 da serialização JSON configurada da lista de Item canônicos (preservar formato v1 nas evoluções). Header da operação é inserido uma vez, com resultado completo, seguido dos movimentos na mesma transação; sem linhas pendentes visíveis/atualizações de resultado. Identidade própria da operação de estoque não antecipa checkout/idempotência genérica da PDV-035. Sem TTL ou exclusão automática de trilha. Sem alteração manual de reservado, reserva, compras ou Saga.

## Plano de verificação e migrations

Migration sequencial posterior à PDV-026, PostgreSQL real e ciclo upgrade/rebuild/replay. Lote válido/inválido em item intermediário, rollback real em falha de trilha, saldos nunca negativos/abaixo do reservado, imutabilidade por UPDATE/DELETE rejeitados, ator correto, acesso/revogação, repetição concorrente de requestId, conteúdo conflitante, dois escritores/versões, entradas inválidas e histórico. Verify completo, Migrate/Validate/Info, planejamento/links/diff/segredos; commit/CI próprios antes da PDV-028.

## Evidências, revisão e aceite

Verify Java21/PostgreSQL16.15 SUCCESS em9m14s: **21 unitários+186 IT (207)**, zero falhas/erros/skips e lint zero. Dezoito HTTP novos de movimentos e um IT de upgrade testam lote válido/rollback em segundo item, falha real de ledger revertendo saldo/versão/header, ator e imutabilidade de operação/movimentos, reservado sintético protegido, correção de produto inativo, ajuste absoluto, extremos BIGINT/versão, frações/entradas inválidas, dois escritores com um vencedor, repetição concorrente com um efeito, conteúdo conflitante e replay do snapshot original. Upgrade V10→V11 preserva saldo/abertura e rebuild/replay passa. Carga regressão:82.274 req/0erros/p95 local14,765ms. Banco próprio removido.

Migrate/Validate/Info V11 SUCCESS; JAR somente V1–V11 sem fixtures. Planejamento/fontes,64 links locais/UTF-8, diff/staging/credenciais aprovados. Autorrevisão confirma CAS por versão/saldo/limites, ordem determinística de lote, identidade escopada/advisory lock, histórico imutável e transação única. Commit/CI próprios ligados na tarefa viva após publicação, main sem PR. Em validação aguardará aceite real; reservas/ETags/compras/Saga não foram declarados implementados.

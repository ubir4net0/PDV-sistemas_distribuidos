# PDV-001 auditoria e baseline proposta

## Incremento solicitado novos diagramas

Em 30/10/2026 o proprietário pediu gerar novos diagramas a partir da especificação disponível. Este incremento autoriza derivar modelos do texto do ZIP; não os apresenta como os originais ausentes nem como contratos implementados ou decisões de domínio aprovadas.

Entregas: arquitetura, casos de uso, classes, DER por domínio, sequências de venda/cancelamento, estados de venda/pagamento/reserva, cadastros, alternativas de fornecedor-produto e confiabilidade da mensageria. Arquivos Mermaid editáveis, SVG para consulta e índice navegável em docs/diagrams/. Identificar fonte, requisitos e decisões pendentes por desenho; preservar a alternativa de cardinalidade e as duas nomenclaturas de ordem de compra, sem escolher silenciosamente.

Aceite deste incremento: renderizar todos os desenhos sem erro, inspecionar visualmente legibilidade, conferir invariantes de estoque/timeout/idempotência, verificar os hashes das fontes e publicar por commit direto em main. Usar ferramentas de renderização temporárias fora das dependências da aplicação. Não criar migrations, código de produto, decisões aprovadas ou status de conclusão por gerar imagens. Aceite da baseline e diagramas continua pendente do proprietário.

Resultado técnico do incremento: 15 fontes Mermaid, 15 SVGs, índice com RF/RNF/decisões por desenho, manifesto e script PowerShell reproduzível. Todos os desenhos finais foram renderizados em Mermaid CLI 12.0.0 usando Chrome local e inspecionados por 15 prévias PNG fora do Git. SVGs verificados como XML com viewBox. Verificador de planejamento aprovou os 60 IDs, títulos/aceites/dependências, grafo e 39 requisitos, além dos hashes das três fontes originais. Mudanças em nomes de estados e cardinalidades não foram promovidas a decisões aprovadas.

Comandos executados: ./scripts/render-diagrams.ps1 -Preview, regeneração direcionada com -Diagram após ajustes de sintaxe/legibilidade, validação XML dos 15 SVGs, node scripts/verify-planning.mjs e git diff --check. Sem testes de aplicação, migrations ou mudança de persistência neste incremento documental. O registro abaixo é o estado histórico da auditoria anterior ao pedido dos novos diagramas.

## Estado e fonte

Notion: https://app.notion.com/p/4092c9a9991781b89668e2fb5d0da613?pvs=204

Leitura viva e inspeção do pacote em 30/10/2026. Status Backlog; SRC-01 impeditivo ao aceite integral. Este documento registra a auditoria inicial solicitada, sem liberar implementação.

## Objetivo e fronteiras

Conferir fontes Notion/GitHub/ZIP, reconciliar 60 tarefas e mapear lacunas, sem inventar diagramas ou estado de execução. Não inclui código de domínio nem decisão silenciosa sobre contratos.

## Critério original

Ler página e anexos vivos, inventariar lacunas, registrar decisões e evitar duplicação de cards.

## Contratos e dependências

Não há contrato API/evento implementado. A primeira auditoria não tem tarefa predecessora. Fontes acadêmicas originais ausentes; estados/cardinalidade e prioridades com dependências cruzadas precisam de refinamento.

## Evidências executadas

Fetch da página mestre antes de alterações: parágrafo vazio, sem subpáginas/database/anexos. Busca PDV no projeto e workspace acessível: vazia. GitHub isEmpty=true e ls-remote sem referências. DOCX: 60 títulos, mesmos 60 aceites do MD, sem mídias/objetos. Board criado e relido com 60 IDs únicos, has_more=false e equivalência dos campos originais. Relatório: docs/reports/reconciliacao.md.

## Verificação e aceite

Rodar node scripts/verify-planning.mjs e revisar o relatório; testes de aplicação não se aplicam. Aceite integral continua pendente enquanto SRC-01 não for resolvido e a baseline não for validada pelo responsável. Nenhum avanço de status por cadastro ou PR.

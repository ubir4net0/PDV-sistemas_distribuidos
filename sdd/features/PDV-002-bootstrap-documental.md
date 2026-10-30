# PDV-002 bootstrap documental do repositório vazio

## Estado e fonte

Notion: https://app.notion.com/p/4092c9a9991781359c70ed9efb999125?pvs=204

Leitura viva em 30/10/2026. Critério original: Bootstrap documentado do repositório vazio; main/develop e convenções; sem segredos; proteção após setup.

Status inicial Backlog preservado. PDV-001 ainda possui lacuna de fontes; este é exclusivamente o bootstrap inicial documental permitido pela seção Entrega do primeiro ciclo e pela exceção de repositório vazio do pacote. Não libera implementação das tarefas dependentes.

## Contexto e objetivo

GitHub consultado: repositório vazio, sem referências. Clone separado em C:/laragon/www/PDV-sistemas_distribuidos, sem arquivos do usuário. Preparar histórico, fontes, workflow, specs e plano revisáveis, preservando o workspace Amor e Vida.

## Fronteiras

Inclui main/develop iniciais, uma feature de documentação com commits separados, monorepo reservado por READMEs, relatório e registry de links Notion, verificação do planejamento e PR para develop. Exclui código de produto, dependências instaladas, migrations, schema, infraestrutura iniciada, mudanças em outros projetos, merge e deploy. Proteções dependem da revisão do setup e devem ser registradas como pendentes se não configuradas.

## Contratos de API e eventos

Não aplicável ao bootstrap documental. Serão definidos em PDV-007 após decisões de domínio.

## Dependências e decisões

PDV-001 continua em Backlog com SRC-01. Java padrão 17; alvo 21 ainda precisa de toolchain. Spring Boot estável deverá ser confirmado na implementação de PDV-005. Diagrama original e aceites acadêmicos ausentes.

## Critérios de aceite deste incremento

- Fontes e 60 cards rastreáveis, sem duplicação nem atribuições inventadas.
- Histórico main/develop inicial e feature/PDV-002-bootstrap-planejamento com PR revisável.
- Instruções, template SDD, relatório de divergências e ordem topológica.
- Nenhuma alegação de implementação, teste de aplicação ou proteção não verificada.

## Plano de verificação

Conferir Notion integralmente, unicidade, títulos, aceites e dependências. Executar verificador do planejamento, git diff --check e inspeção de segredos; verificar branches/PR remotos. Sem testes de produto nesta entrega documental.

## Evidências e aceite

Registrar resultados em docs/reports/primeiro-ciclo.md no incremento da feature. Aceite integral de PDV-002 permanece pendente; revisão do bootstrap e proteções não são presumidas.

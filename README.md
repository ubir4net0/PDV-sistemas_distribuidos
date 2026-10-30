# PDV de Sistemas Distribuídos

Projeto acadêmico para portfólio. Primeiro ciclo: planejamento e bootstrap documental, em 30/10/2026. A aplicação ainda não foi implementada.

- [Página mestre](https://app.notion.com/p/4032c9a9991780cb9c18eb22cd7ca2a2)
- [Roadmap e execução com 60 tarefas](https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad)
- [Decisões e inconsistências](https://app.notion.com/p/4092c9a9991781ddbb8ec662094f8ff3)
- [Plano de implementação](https://app.notion.com/p/4092c9a99917816cafecc6ab7f25df80)

Stack requerida: Java 21, Spring Boot e Maven; PostgreSQL/Flyway; RabbitMQ; React/TypeScript/Vite/Tailwind. API e worker serão processos independentes com banco compartilhado no laboratório acadêmico.

Pagamentos e estornos serão exclusivamente locais e fictícios. Não integrar gateways, bancos, Pix real, cartões reais, NF-e ou APIs comerciais.

O projeto é mantido individualmente pelo proprietário. O fluxo vigente usa commits diretos em main, sem PR ou aprovação de terceiros obrigatórios, conforme a [ADR-001](docs/decisions/ADR-001-fluxo-individual.md). develop e a feature inicial são preservadas como histórico. O Notion é a fonte viva dos status; commit ou push não comprovam entrega por si só. Deploy exige autorização específica.

## Leitura e verificação

- [Guia para agentes](AGENTS.md) e [workflow Notion](docs/workflows/notion-task.md).
- [Estado observado](docs/context/current-state.md) e [arquitetura alvo](docs/context/architecture.md).
- [Índice de ADRs](docs/decisions/README.md): processos, transações, Saga, mensageria, idempotência e limites acadêmicos documentados na PDV-003.
- [15 diagramas propostos](docs/diagrams/README.md): arquitetura, casos de uso, classes, DER, sequências e estados, com fontes Mermaid editáveis e SVGs.
- [Reconciliação das fontes](docs/reports/reconciliacao.md), [ordem proposta](docs/planning/implementation-order.md) e [rastreabilidade de 19 RF e 20 RNF](docs/planning/requirements-coverage.md).
- [Fontes originais e hashes](docs/sources/README.md); o registry local guarda links, não status vivo.

Com Node 22, executar `node scripts/verify-planning.mjs`. O comando valida integridade dos 60 cards planejados, dependências, links, cobertura e fontes. Não executa testes de produto. O workflow Planning integrity faz a mesma verificação nos pushes de main e permite execução manual; ele não substitui a CI de API/worker/frontend prevista em PDV-008.

Não existe comando para subir a aplicação nesta baseline. O Java padrão observado é 17; configurar Java 21 e os scaffolds nas tarefas elegíveis. Diagramas originais e decisões bloqueantes constam no relatório. A main permite commits diretos e mantém bloqueados force-push e exclusão.

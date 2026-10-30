# PDV de Sistemas Distribuídos

Projeto acadêmico para portfólio. Primeiro ciclo: planejamento e bootstrap documental, em 30/10/2026. A aplicação ainda não foi implementada.

- [Página mestre](https://app.notion.com/p/4032c9a9991780cb9c18eb22cd7ca2a2)
- [Roadmap e execução com 60 tarefas](https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad)
- [Decisões e inconsistências](https://app.notion.com/p/4092c9a9991781ddbb8ec662094f8ff3)
- [Plano de implementação](https://app.notion.com/p/4092c9a99917816cafecc6ab7f25df80)

Stack requerida: Java 21, Spring Boot e Maven; PostgreSQL/Flyway; RabbitMQ; React/TypeScript/Vite/Tailwind. API e worker serão processos independentes com banco compartilhado no laboratório acadêmico.

Pagamentos e estornos serão exclusivamente locais e fictícios. Não integrar gateways, bancos, Pix real, cartões reais, NF-e ou APIs comerciais.

O histórico inicial main/develop é a exceção de bootstrap do repositório vazio. Implementação futura ocorre em feature/PDV-XXX-descricao e PR para develop, sem merge ou deploy automático. O Notion é a fonte viva dos status; criar um card ou abrir PR não comprova entrega.

## Leitura e verificação

- [Guia para agentes](AGENTS.md) e [workflow Notion](docs/workflows/notion-task.md).
- [Estado observado](docs/context/current-state.md) e [arquitetura alvo](docs/context/architecture.md).
- [Reconciliação das fontes](docs/reports/reconciliacao.md), [ordem proposta](docs/planning/implementation-order.md) e [rastreabilidade de 19 RF e 20 RNF](docs/planning/requirements-coverage.md).
- [Fontes originais e hashes](docs/sources/README.md); o registry local guarda links, não status vivo.

Com Node 22, executar `node scripts/verify-planning.mjs`. O comando valida integridade dos 60 cards planejados, dependências, links, cobertura e fontes. Não executa testes de produto. O workflow Planning integrity faz a mesma verificação no PR; ele não substitui a CI de API/worker/frontend prevista em PDV-008.

Não existe comando para subir a aplicação nesta baseline. O Java padrão observado é 17; configurar Java 21 e os scaffolds nas tarefas elegíveis. Diagramas originais e decisões bloqueantes constam no relatório. Proteções de branches ainda precisam ser aplicadas após revisão do setup.

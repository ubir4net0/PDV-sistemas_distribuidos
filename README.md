# PDV de Sistemas Distribuídos

Projeto acadêmico para portfólio. Infraestrutura local PostgreSQL/RabbitMQ e scaffold técnico da API Java 21 disponíveis. Funcionalidades de negócio, worker e frontend ainda não foram implementados.

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

## Ambiente local

Na raiz, com Docker ativo e PowerShell:

```powershell
./scripts/init-local-env.ps1
docker compose up -d --wait --wait-timeout 180
./scripts/verify-infra.ps1
```

PostgreSQL em `127.0.0.1:55432`; RabbitMQ AMQP em `127.0.0.1:5673` e management em `http://127.0.0.1:15673`. Credenciais são geradas no `.env` ignorado pelo Git. Consulte [infra/README.md](infra/README.md) para configuração, reinício e limites dos testes. Rede e volumes pertencem ao projeto `pdv-local`.

## Scaffold da API

Com Docker ativo, na raiz:

```powershell
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Run
```

API em `http://127.0.0.1:8081/actuator/health`. O build usa Maven Wrapper e Java 21 isolado, sem alterar o Java 17 do Windows. [Instruções e limites da API](backend/api/README.md). Health representa somente o processo; este scaffold ainda não conecta banco/filas e bloqueia os demais caminhos até a implementação dos contratos e autenticação.

Diagramas originais e decisões bloqueantes constam no relatório. A main permite commits diretos e mantém bloqueados force-push e exclusão.

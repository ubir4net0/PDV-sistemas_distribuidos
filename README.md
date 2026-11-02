# PDV de Sistemas Distribuídos

PDV-049 adiciona [observabilidade local](docs/workflows/observability.md): spans seguros de checkout/AMQP, logs JSON centralizados V19 e painel ADMIN. Cobertura e limitações no contrato.

PDV-045 acrescenta [rastreamento da venda](docs/workflows/sale-timeline.md): eventos persistidos, horários, correlação e falhas seguras, somente para o dono da venda. Sem novas transições ou DDL.

Projeto acadêmico para portfólio com API Java 21, PostgreSQL e worker independente com RabbitMQ. Autenticação, cadastros, estoque, reservas, carrinho, checkout idempotente e coordenação persistida estão disponíveis. O worker tem modos explícitos de expiração e pagamento simulado. Outbox/Inbox e transporte RabbitMQ de negócio estão disponíveis em modo explícito; conclusão com baixa única e comprovante digital estão implementados. Recusa libera reservado e reconciliação própria resolve TIMEOUT somente com prova durável do simulador local. Frontend oferece login, clientes, PDV/carrinho/checkout e acompanhamento de pagamento simulado; demais telas, compras e cancelamento permanecem nas tarefas próprias.

- [Página mestre](https://app.notion.com/p/4032c9a9991780cb9c18eb22cd7ca2a2)
- [Roadmap e execução com 60 tarefas](https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad)
- [Decisões e inconsistências](https://app.notion.com/p/4092c9a9991781ddbb8ec662094f8ff3)
- [Plano de implementação](https://app.notion.com/p/4092c9a99917816cafecc6ab7f25df80)

Stack requerida: Java 21, Spring Boot e Maven; PostgreSQL/Flyway; RabbitMQ; React/TypeScript/Vite/Tailwind. API e worker serão processos independentes com banco compartilhado no laboratório acadêmico.

Pagamentos e estornos serão exclusivamente locais e fictícios. Não integrar gateways, bancos, Pix real, cartões reais, NF-e ou APIs comerciais.

O projeto é mantido individualmente pelo proprietário. O fluxo vigente usa commits diretos em main, sem PR ou aprovação de terceiros obrigatórios, conforme a [ADR-001](docs/decisions/ADR-001-fluxo-individual.md). develop e a feature inicial são preservadas como histórico. O Notion é a fonte viva dos status; commit ou push não comprovam entrega por si só. Deploy exige autorização específica.

## Leitura e verificação

- [Guia para agentes](AGENTS.md) e [workflow Notion](docs/workflows/notion-task.md).
- [Roteiro de execução de uma tarefa](docs/workflows/task-execution.md): DoR/DoD, comandos, autorevisão, evidências e limites de escrita no fluxo individual.
- [Estado observado](docs/context/current-state.md) e [arquitetura alvo](docs/context/architecture.md).
- [OpenAPI, eventos e exemplos versionados](contracts/README.md): contratos atuais, validação automatizada e documentação HTML.
- [Contrato de catálogo](docs/workflows/catalog.md): categorias/produtos, estados, permissões, versões e histórico.
- [Telas de clientes](docs/workflows/frontend-customers.md): lista, busca, formulário, conflitos e permissões.
- [PDV e checkout na interface](docs/workflows/frontend-pos.md): carrinho, totais exatos e recuperação da mesma operação.
- [Pagamento simulado na interface](docs/workflows/frontend-payments.md): estados, consulta própria e reconciliação.
- [Contrato de fornecedores](docs/workflows/suppliers.md): CNPJ, vínculo opcional de produtos e inativação com histórico.
- [Contrato de estoque](docs/workflows/inventory.md): saldos inteiros, abertura técnica e leituras transacionais.
- [Índice de ADRs](docs/decisions/README.md): processos, transações, Saga, mensageria, idempotência e limites acadêmicos documentados na PDV-003.
- [15 diagramas propostos](docs/diagrams/README.md): arquitetura, casos de uso, classes, DER, sequências e estados, com fontes Mermaid editáveis e SVGs.
- [Reconciliação das fontes](docs/reports/reconciliacao.md), [ordem proposta](docs/planning/implementation-order.md) e [rastreabilidade de 19 RF e 20 RNF](docs/planning/requirements-coverage.md).
- [Fontes originais e hashes](docs/sources/README.md); o registry local guarda links, não status vivo.

Com Node 22, executar `node scripts/verify-planning.mjs`. O comando valida integridade dos 60 cards planejados, dependências, links, cobertura e fontes. O workflow Planning integrity faz a mesma verificação nos pushes de main e permite execução manual.

O workflow Backend quality verifica API e worker em Java 21: Checkstyle, compilação, unitários, JAR e integração HTTP/PostgreSQL/AMQP real com bancos e broker temporários. Não faz deploy. [Gates, comandos e limites da CI](docs/workflows/ci.md). Frontend quality verifica TypeScript, unitários, build e Chromium com API/PostgreSQL próprios; [frontend e comandos locais](frontend/README.md). Persistência inclui base técnica e [identidade](docs/workflows/identity-security.md).

## Ambiente local

Na raiz, com Docker ativo e PowerShell:

```powershell
./scripts/init-local-env.ps1
docker compose up -d --wait --wait-timeout 180
./scripts/verify-infra.ps1
```

PostgreSQL em `127.0.0.1:55432`; RabbitMQ AMQP em `127.0.0.1:5673` e management em `http://127.0.0.1:15673`. Credenciais são geradas no `.env` ignorado pelo Git. Consulte [infra/README.md](infra/README.md) para configuração, reinício e limites dos testes. Rede e volumes pertencem ao projeto `pdv-local`.

## Scaffold da API

Com Docker e PostgreSQL PDV saudáveis, na raiz:

```powershell
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Run -TokenTtlSeconds 900 -ReservationTtlSeconds 900
```

API em `http://127.0.0.1:8081/actuator/health`. O build usa Maven Wrapper e Java 21 isolado, sem alterar o Java 17 do Windows. [Instruções e limites da API](backend/api/README.md). Health/readiness incluem PostgreSQL sem detalhes públicos; liveness mede o processo. Login, perfil próprio, logout e catálogo de permissões restrito a ADMIN estão disponíveis por Bearer opaco; [contrato de autenticação](docs/workflows/identity-security.md). Os demais caminhos seguem bloqueados. A [base Flyway da PDV-009](docs/workflows/database-migrations.md) cria namespace/histórico e testa rebuild/replay; V2/V3 adicionam identidade e tokens, sem implementar os cadastros comerciais.

## Scaffold do worker

Com PostgreSQL e RabbitMQ locais saudáveis:

```powershell
./scripts/worker.ps1 -Action Verify
./scripts/worker.ps1 -Action Run
```

Processo independente, sem porta HTTP, com diagnóstico JSON tipado por AMQP e tratamento de erros em DLQ. O build testa o broker real em filas próprias e não implementa pagamentos ou eventos de venda. [Contrato técnico, operação e limites](backend/worker/README.md).

Diagramas originais e decisões bloqueantes constam no relatório. A main permite commits diretos e mantém bloqueados force-push e exclusão.

PDV-016 oferece POST /customers com validação local CPF/CNPJ (numérico/alfanumérico), unicidade PostgreSQL e CRUD JPA. Operação e limites no [guia de clientes](docs/workflows/customers.md). Consulta/edição/histórico (017) e endereços/regiões (018) estão disponíveis em commits próprios, com permissões aprovadas.

Reservas com TTL/renovação e barreira transacional estão documentadas em [reservas](docs/workflows/reservations.md). Run da API requer também -ReservationTtlSeconds 900 na demo; integração checkout/Saga e agendamento do worker são incrementos próprios.

Expiração: com migrations aplicadas, `./scripts/worker.ps1 -Action Run -ExpireReservations`; [operação e limites](backend/worker/README.md).

[Carrinho e itens](docs/workflows/carts.md): preço copiado, quantidades inteiras e totais decimais, sem reserva/checkout automático.

[Checkout idempotente](docs/workflows/checkout.md): snapshot/reserva/intenção atômicos, replay e carrinho congelado, sem pagamento/baixa automática.

[Saga persistida](docs/workflows/sale-saga.md), V15: início protegido, progresso e resultados reentrantes; sem baixa/liberação/conclusão automática.

[Pagamento simulado local](docs/workflows/simulated-payments.md): worker.ps1 -Action Run -SimulatePayments, combinado com -ExpireReservations quando desejado.

[Mensageria de negócio](docs/workflows/business-messaging.md): worker.ps1 -Action Run -BusinessMessaging -ExpireReservations; não combinar com -SimulatePayments.

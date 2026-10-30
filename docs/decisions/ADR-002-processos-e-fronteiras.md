# ADR-002 processos e fronteiras

Data: 30/10/2026. Tarefa: [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Estado: arquitetura declarada no pacote, documentada para validação; não implementada.

## Contexto e fontes

A especificação v1.0, seções 2, 5 e 8, exige Java 21/Spring Boot, API e worker independentes, React/TypeScript e PostgreSQL compartilhado no laboratório. RNF05 exige API stateless; RNF06 exige RabbitMQ. O [diagrama de arquitetura](../diagrams/svg/01-arquitetura.svg) representa esse alvo como proposta.

## Decisão registrada

| Componente | Responsabilidade alvo | Fronteira |
| --- | --- | --- |
| frontend | Interface, consultas e comandos autenticados | Não altera banco nem decide saldo/pagamento |
| backend/api | HTTP, autenticação/autorização, comandos, consulta e coordenação da venda | Regras de estoque e venda protegidas por transações locais |
| backend/worker | Trabalho assíncrono e pagamento/estorno fictício determinístico | Processo independente; sem servidor HTTP público obrigatório |
| PostgreSQL | Estado transacional durável e registros técnicos | Compartilhado entre processos no perfil acadêmico |
| RabbitMQ | Transporte assíncrono de mensagens | Não substitui a fonte de verdade nem o histórico do negócio |

API e worker devem construir e iniciar separadamente. Desligar o worker não deve derrubar a API; trabalho já aceito fica pendente e observável. Aceitar novos comandos durante indisponibilidade depende das precondições e da política de capacidade a definir, sem prometer disponibilidade ilimitada.

Sessão ou fluxo não deve depender de memória exclusiva de uma instância da API. Estado durável, idempotência e progresso da Saga precisam ser compartilhados. Cache local não pode decidir disponibilidade de estoque. Escalar réplicas só será comprovado com balanceador e ensaio real.

A API é a fronteira proposta para comandos do negócio e coordenação da Saga; o worker processa simulações. Atribuições exatas dos consumidores, publicador outbox e agendadores serão fechadas nas specs PDV-006/036/038, evitando dois escritores sem regra sobre o mesmo efeito.

## Justificativa e alternativas

Um único processo simplificaria execução, mas ocultaria a fronteira de falha exigida pelo exercício. Dois processos com bancos isolados exigiriam mais sincronização e operação; podem ser evolução futura. O pacote escolhe dois processos com banco compartilhado para demonstrar mensageria e falhas mantendo o laboratório viável.

## Consequências e limites

Há acoplamento de schema e ponto de falha comum no PostgreSQL. Não declarar isolamento de dados de microserviços, alta disponibilidade ou escalabilidade comprovada. Banco compartilhado não autoriza um componente a contornar as invariantes de outro.

Versão estável do Spring Boot e detalhes de build serão verificados em fonte oficial na PDV-005. Este registro não seleciona novas dependências.

## Validação futura

PDV-005/006: builds e processos separados. PDV-004: dependências com health checks. PDV-047/050: correlação entre processos e falha do worker sem bloqueio indefinido da API. Ensaio de múltiplas instâncias para RNF05. Nenhum desses testes foi executado nesta tarefa documental.

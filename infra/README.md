# Infraestrutura local do PDV

O [Compose da raiz](../compose.yaml) inicia PostgreSQL 16.15 e RabbitMQ 4.3.7 com management, usando imagens oficiais fixadas por digest. A [PDV-004](../sdd/features/PDV-004-infra-local.md) registra escopo e evidências. Este Compose contém apenas dependências locais; [API](../backend/api/README.md) e [worker](../backend/worker/README.md) executam separadamente. A API tem [base técnica Flyway](../docs/workflows/database-migrations.md), sem tabelas de domínio. Frontend não existe.

## Iniciar no Windows

Requisitos: Docker Engine ativo, Docker Compose com `up --wait` e PowerShell 5.1 ou superior. Executar na raiz deste repositório:

```powershell
./scripts/init-local-env.ps1
docker compose config --quiet
docker compose up -d --wait --wait-timeout 180
./scripts/verify-infra.ps1
```

O gerador cria `.env` com duas senhas aleatórias, preservando um arquivo existente. Não copie valores para Git/Notion. `.env.example` é modelo público com senhas vazias; ele sozinho não permite iniciar os serviços. Para configuração manual, crie `.env` a partir do modelo e preencha as senhas somente no arquivo ignorado. O formato de variáveis segue o Docker Compose.

`docker compose config --quiet` valida sem exibir valores. A saída completa de `config`, `inspect` ou `env` pode conter credenciais; não a anexe às evidências. Os usuários locais de bootstrap são administrativos para este laboratório, sem representar as credenciais futuras da aplicação. Não use este perfil como configuração de produção.

## Acesso local

| Serviço | Host | Dentro da rede Compose | Configuração em .env |
| --- | --- | --- | --- |
| PostgreSQL | 127.0.0.1:55432 | postgres:5432 | PDV_POSTGRES_DB/USER/PASSWORD/PORT |
| RabbitMQ AMQP | 127.0.0.1:5673 | rabbitmq:5672 | PDV_RABBITMQ_USER/PASSWORD/VHOST/PORT |
| Management | http://127.0.0.1:15673 | rabbitmq:15672 | PDV_RABBITMQ_MANAGEMENT_PORT |

Por padrão, banco e vhost se chamam `pdv`; usuários se chamam `pdv_dev`. Consulte as senhas no seu `.env` local. As três portas são limitadas a loopback e podem ser trocadas antes de subir os serviços. Não mudar a exposição para interfaces públicas como parte desta tarefa.

O projeto Compose padrão é `pdv-local`, com rede `pdv-local_default` e volumes `pdv-local_postgres_data`/`pdv-local_rabbitmq_data`. Não reutilize nome de projeto, rede ou volumes do Amor e Vida. O hostname estável `rabbitmq` preserva a identidade do nó nos dados do broker. Os comandos respeitam as opções de projeto do Compose; confira o projeto selecionado antes de executar operações.

## Verificar e reiniciar

```powershell
docker compose ps
./scripts/verify-infra.ps1 -Restart
docker compose stop
docker compose up -d --wait --wait-timeout 180
```

O smoke test exige os dois serviços saudáveis. Ele autentica por TCP no PostgreSQL, verifica UTC e cria um marcador sintético; autentica na API HTTP management do RabbitMQ, cria fila durável própria, publica e consome uma mensagem persistente. `-Restart` reinicia apenas os dois serviços do projeto e verifica o marcador e a mensagem após recuperação. Os objetos de teste têm nomes aleatórios e são removidos no `finally`; nenhuma fila de negócio é purgada e nenhum banco é resetado.

Health PostgreSQL usa `pg_isready`; o teste autenticado complementa essa checagem. Health RabbitMQ verifica aplicação, alarmes locais e conectividade dos listeners. Isso não substitui testes de contrato ou consumo AMQP pelo futuro worker, nem prova alta disponibilidade. A frequência de 30 segundos evita abrir processos de diagnóstico continuamente.

`stop` preserva containers e volumes. Não usar `down -v` para solucionar problemas: ele remove os dados deste projeto. Alterar senha, usuário, banco ou vhost em `.env` não atualiza automaticamente os dados de um volume já inicializado; mudanças exigem procedimento específico de administração/migração. Não apague volumes para contornar credenciais divergentes.

Se o smoke test for interrompido abruptamente, podem restar uma tabela `public.pdv_infra_smoke_<id>` e uma fila `pdv.infra-smoke.<id>`. Identifique a execução antes de remover somente seus objetos de teste; não aplique limpeza ampla.

## Limites e fontes

UTC é configurado nos serviços; NTP do host não é demonstrado. O perfil local usa HTTP/AMQP sem TLS e um nó de banco/broker. Não comprova RNF05 multi-instância, RNF07/NTP, HA, TLS, uptime, outbox/inbox, transações de estoque ou Saga. A PDV-009 testa Flyway com rebuild/replay restrito à base vazia; backup/restore de dados e migrations de domínio continuam pendentes.

Referências consultadas: [imagem oficial PostgreSQL](https://hub.docker.com/_/postgres), [imagem oficial RabbitMQ](https://github.com/docker-library/docs/blob/master/rabbitmq/README.md), [health checks RabbitMQ](https://www.rabbitmq.com/docs/monitoring) e [prontidão no Compose](https://docs.docker.com/compose/how-tos/startup-order/). Atualização de imagem requer novo digest e repetição dos testes; não trocar o digest sem revisar a versão dos dados.

# PDV-004 Docker Compose e health checks

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a9991781d9a047d00b35386b9e. Leitura em 30/10/2026. Fonte: especificação v1.0, seções 2 e 12, PDV-004; RNF05 e RNF07. Aceite original: “PostgreSQL e RabbitMQ sobem com health checks; ambiente reproduzível e sem senhas versionadas.”

## Contexto, dependências e Definition of Ready

O repositório e o fluxo individual já existem (PDV-002); as escolhas de PostgreSQL/RabbitMQ e processos independentes estão documentadas nas ADRs 002–006 (PDV-003). Os aceites formais dessas tarefas permanecem pendentes no Notion. Este incremento atende apenas à preparação e verificação da infraestrutura local isolada, que não depende de estados, cardinalidades ou schema propostos. Não encerra PDV-001/002/003 nem libera código de domínio dependente das decisões abertas.

O pedido atual autoriza executar outra tarefa do projeto; criação de recursos locais novos e reversíveis está no escopo. Não atribuir responsável Notion ou prazo sem identidade/acordo.

## Fronteiras

Inclui Compose na raiz, imagens oficiais fixadas por digest após verificação, PostgreSQL e RabbitMQ com management, health checks, volumes próprios, UTC e portas limitadas a 127.0.0.1. Incluir geração local de credenciais, instruções e smoke test com os serviços reais.

Exclui API, worker, frontend, schema de domínio, migrations, filas de negócio, HA, TLS implantado, pagamentos e deploy remoto. Não alterar containers/volumes do Amor e Vida. Não publicar credenciais nem saída de configuração expandida no Git/Notion.

## Contratos e decisões locais

Projeto Compose padrão `pdv-local`; serviços `postgres` e `rabbitmq`; nomes de volumes prefixados pelo projeto. Portas padrão do host: PostgreSQL 55432, AMQP 5673 e management 15673. São escolhas locais configuráveis, sem efeito nos contratos futuros de API/eventos. Containers usam portas padrão internas. Não fixar container_name nem reutilizar rede/volume de outro projeto.

Credenciais serão geradas aleatoriamente em `.env`, ignorado pelo Git. `.env.example` conterá apenas configuração pública e senhas vazias; Compose recusará início sem as senhas. Não regenerar credenciais sobre arquivo existente. Alterar `.env` não altera automaticamente usuários de volumes já inicializados.

## Critérios de aceite

1. Compose valida e inicia os dois serviços reais com health checks saudáveis.
2. PostgreSQL aceita autenticação TCP, executa consulta e usa UTC.
3. RabbitMQ autentica, publica e consome mensagem sintética em fila própria de teste, removida ao final.
4. Reinício dos serviços preserva um marcador de infraestrutura e uma mensagem persistente em fila durável de teste; remover apenas os recursos de teste criados nesta sessão.
5. Reexecutar setup preserva `.env`; configuração falha sem senhas; imagens fixadas e nenhum segredo versionado.
6. Portas, rede e volumes são próprios; os serviços existentes do Amor e Vida não sofrem alteração.

## Plano de teste e migrations

Executar gerador local, `docker compose config --quiet`, `docker compose up -d --wait --wait-timeout 180`, smoke test e ensaio de reinício. Conferir identidades/portas dos serviços pré-existentes antes/depois. Executar verificador de planejamento e `git diff --check`, revisar staged diff e ignorados. Verificar digest das imagens oficiais após pull.

Não criar migrations ou tabelas de domínio. O ensaio usa apenas objetos sintéticos identificados e criados pela própria verificação, com limpeza restrita a esses objetos. Flyway e ciclo migrate/restore/replay pertencem à PDV-009 após scaffold; não presumir Maven Wrapper existente.

## Evidências executadas

Pre-flight: main limpa e sincronizada por fast-forward; Docker Engine 29.9.2 e Compose 5.6.1 disponíveis. Nenhum projeto PDV ativo; portas 55432/5673/15673 livres. Amor e Vida utiliza PostgreSQL na porta 5433 e outros containers, preservados.

Resultados desta sessão:

- Imagens oficiais confirmadas por pull/digest; execução retornou PostgreSQL 16.15 e RabbitMQ 4.3.7. Digests constam no Compose, sem tags flutuantes como única identidade.
- `docker compose config --quiet` passou; `docker compose up -d --wait --wait-timeout 180` iniciou ambos com health `healthy`.
- `./scripts/verify-infra.ps1 -Restart` passou: SQL autenticado por TCP, timezone UTC, autenticação HTTP management, mensagem roteada/publicada/consumida e marcador/mensagem preservados após reinício. O script final também passou sem `-Restart` após ajuste da transação de criação e limpeza independente dos dois objetos.
- Consulta posterior encontrou zero tabelas `pdv_infra_smoke_*`; `rabbitmqctl list_queues -p pdv` não encontrou filas remanescentes. Limpeza dos recursos sintéticos comprovada.
- `date +%Z` do broker retornou UTC. Não houve ensaio NTP.
- Segunda execução do gerador preservou o SHA256 de `.env`, sem imprimir seu conteúdo. `git check-ignore .env` confirmou exclusão; Compose com `.env.example` recusou senhas vazias como esperado.
- Seis containers do Amor e Vida continuaram em execução, com início entre 11:33 e 11:38 UTC, anterior a esta tarefa. Portas 5433 e demais serviços preservados; somente o projeto pdv-local foi reiniciado.
- Verificador de planejamento passou: 60 IDs/aceites/dependências/links, grafo, 19 RF + 20 RNF e hashes das três fontes. Parser PowerShell não encontrou erros nos dois scripts. `git diff --check` passou.
- Autorevisão do staged: 23 links Markdown locais válidos, `.env` fora do índice e as duas senhas locais ausentes do diff. Nenhum arquivo de outro repositório foi adicionado.

Não foram criadas migrations ou entidades de domínio. O teste de mensagens usa HTTP management; conexão/contrato AMQP pelo futuro worker, carga, HA, TLS e recuperação de desastre permanecem sem ensaio. Saúde local não comprova os RNFs completos. Commit e CI serão registrados na tarefa após publicação.

## Revisão e aceite

Commit direto em main com PDV-004, sem PR conforme ADR-001. Registrar testes, limitações, hash e CI na tarefa corrente. Health local não comprova RNF05 multi-instância ou RNF07/NTP no host; apenas timezone UTC dos serviços. Aceite formal do proprietário permanece separado da evidência técnica.

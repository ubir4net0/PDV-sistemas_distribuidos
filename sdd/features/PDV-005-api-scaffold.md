# PDV-005 scaffold da API Spring Boot

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a9991781788a8bf31980f507db. Leitura em 30/10/2026. Fonte: especificação v1.0, seções 2 e 12. Aceite original: “Java 21, Maven Wrapper, endpoints /actuator/health, camadas por domínio e build limpo.”

## Contexto e Definition of Ready

PDV-002 entrega repositório/fluxo e PDV-003 documenta arquitetura; seus aceites formais permanecem pendentes. PDV-004 disponibiliza serviços reais locais. O pedido atual autoriza o próximo incremento técnico: bootstrap da API sem entidades, enums ou contratos de negócio dependentes de SRC-01/DEC-01 a DEC-06. Não encerrar predecessoras nem aprovar a baseline por iniciar um scaffold.

ENV-01: o host usa Java 17. Confirmar Java/Javac 21 em imagem oficial e usá-la para build e execução, sem alterar Java global. Spring Initializr consultado ao vivo indicou Spring Boot 4.1.2 estável; a documentação oficial informa compatibilidade com Java 21. Fixar versões e digest da toolchain antes de verificar. Não inventar responsável Notion ou prazo.

## Fronteiras

Inclui aplicação mínima em backend/api, Java 21, Spring Boot estável, Maven Wrapper oficial, Web/Validation/Security/Actuator, configuração de health, organização modular e scripts/instruções de build local isolado. Camadas por domínio são apenas estrutura inicial; não representam casos de uso implementados.

Exclui login/RBAC, JPA/entidades, migrations/Flyway, datasource, filas/consumidores, checkout, Saga, pagamentos, frontend, CI de produto e deploy. JPA/AMQP serão adicionados quando houver integração real nas tarefas correspondentes; não usar drivers fake ou desabilitar validações para declarar integração concluída.

## Contratos técnicos e segurança

GET /actuator/health e probes liveness/readiness retornam saúde do processo, sem detalhes sensíveis. Não comprovam conectividade PostgreSQL/RabbitMQ nem prontidão de funcionalidades futuras. Somente health deve ter acesso anônimo; demais caminhos permanecem bloqueados até autenticação/contratos. Não criar usuário/senha padrão, login fake ou endpoints de negócio de exemplo.

Porta local 8081 em loopback; publicação Docker apenas em 127.0.0.1. Maven deve exigir JDK 21. Não introduzir segredo no POM, YAML, imagem, logs ou Notion. Nenhum contrato de evento aprovado.

## Critérios de aceite e validação

1. Imagem oficial executa Java e Javac 21; versão Spring Boot confirmada em fonte oficial.
2. Maven Wrapper presente para Unix/Windows, versão da distribuição fixada e checksum verificado.
3. Build clean verify pelo Wrapper, incluindo testes HTTP reais em porta aleatória: health/probes UP sem detalhes; demais caminhos bloqueados; sem sessão/login gerado.
4. JAR empacotado inicia com Java 21 e responde health via porta local; encerrar somente o processo/container de teste ao final.
5. Pacotes separados por domínio e camadas documentados, sem regras ou schema fictícios.
6. Planejamento/fontes originais preservados; diff sem erros e nenhuma credencial versionada.

## Plano de teste e migrations

Gerar bootstrap oficial em diretório temporário sob .git, inspecionar e copiar apenas arquivos necessários. Executar Wrapper pela toolchain Java 21, testes HTTP e smoke do JAR. Manter serviços da PDV-004 saudáveis e preservar Amor e Vida. Conferir scripts PowerShell, links, fontes e staged diff.

Não há alteração de persistência; não criar tabelas ou executar migration. Flyway migrate/restore/replay depende da PDV-009. JDK 17 do host permanece sem alteração; Wrapper Windows será fornecido, mas não declarar build nativo Windows verificado com JDK 21 se apenas o container for usado.

## Evidências executadas

Pre-flight: main limpa/sincronizada; PostgreSQL/RabbitMQ locais healthy. Java padrão do Windows 17, Maven global ausente. Metadata oficial Initializr retornou 4.1.2 como estável padrão.

- Toolchain oficial fixada por digest: `scripts/api.ps1 -Action Version` executou Java/Javac 21.0.12.1 e Maven Wrapper com Maven 3.10.0. Java global do Windows continuou 17.0.20.1.
- Wrapper 3.3.4 gerado pelo plugin oficial no formato bin: extrai Maven com o próprio Java, sem depender de unzip. Arquivo Maven ZIP conferido contra SHA512 oficial do Maven Central; SHA256 fixado na configuração. JAR do Wrapper comparado ao artefato oficial baixado por HTTPS e SHA256 fixado. Não foi presumida assinatura PGP verificada.
- `scripts/api.ps1 -Action Verify` final passou: clean, Enforcer Java 21/Maven, compilação release 21, dez testes HTTP reais, zero falhas/erros/skips, empacotamento e repackage do JAR. Testes cobrem health/probes sem detalhes, caminhos bloqueados, POST bloqueado sem cookie de sessão e ausência de contas padrão.
- Smoke externo do JAR por curl.exe: health/liveness/readiness 200 UP; /actuator/env 403; publicação restrita a 127.0.0.1:8081. O artefato final foi novamente iniciado e passou em health/probes e POST health 403 sem cookie de sessão. Containers de teste encerrados; o script trata encerramento normal por sinal.
- Planejamento passou: 60 IDs/aceites/dependências, grafo, 19 RF + 20 RNF e SHA256 das três fontes preservados. Sintaxe PowerShell e staged diff passaram; 26 links Markdown locais válidos. `.env`, target e as duas senhas locais ficaram fora do staged.
- Wrapper Unix com modo executável 100755; gitattributes fixa LF para mvnw e fontes Java/XML/properties, CRLF para cmd. Seis containers Amor e Vida continuaram em execução com início anterior à tarefa; serviços PostgreSQL/RabbitMQ do PDV continuaram healthy.

Limites: build nativo Windows com JDK 21 não foi executado; fornecidos Wrapper Windows e alternativa Docker verificada. Não há integração JPA/AMQP/Flyway, teste de negócio, performance ou deploy. A CI existente valida planejamento; CI de produto pertence à PDV-008. Commit e execução remota serão vinculados na tarefa após publicação.

## Revisão e aceite

Commit direto em main, sem PR conforme ADR-001; autorevisão, testes e hash/CI na tarefa corrente. Scaffold e health não concluem RF/RNF ou funcionalidades futuras. Aceite formal do proprietário permanece separado das evidências técnicas.

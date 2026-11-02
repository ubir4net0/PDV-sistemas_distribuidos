# PDV-019 — Telas de clientes

## Fonte, aceite e DoR

[Tarefa viva](https://app.notion.com/p/4092c9a9991781be9841f5804c942398), relida em 02/11/2026: “Lista, busca, formulário e validações acessíveis com feedback claro e estados vazios.” Tela04. Responsável/prazo não informados. PDV-015/016/017 relidas ao vivo; login e API de clientes entregues tecnicamente, sem aceitar predecessoras. Regras de acesso/estado seguem recorte já aprovado nas016–018. Sem novas regras de negócio.

Pre-flight: main ed9c2d6 sincronizada, working tree limpa, PostgreSQL/RabbitMQ PDV healthy, Flyway V18 sem pendências (8,836s). Repositório PDV separado do Amor e Vida.

## Fronteiras e contratos

Lista paginada, busca por nome/documento/status, criação/edição PF/PJ, validação de documento/DV local e feedback400/409/403/rede. Detalhe e status ADMIN com versão; sem DELETE, endereço, cadastro de usuários ou consulta externa. Ações dependem das concessões reais retornadas pelo perfil; API permanece autoridade. Mostrar carga/vazio/erro/retry e conflito sem sobrescrever edição concorrente. Versões int64 sem arredondamento, parser JSON exato e serialização numérica. Sem dados/token em storage/log/URL; filtros ficam na memória.

## Verificação planejada

TypeScript, build, unitários de documento/DTO/int64 e requisição autenticada; Chromium real contra API/PostgreSQL UUID próprios. Cenários: lista vazia, criação, DV inválido, documento duplicado, busca/paginação, edição/conflito, status ADMIN e OPERATOR impedido, falha/retry, teclado e mobile/desktop. Fixtures sintéticas apenas no banco descartável; nenhuma migration/alteração Java. Infra e regressões auth preservadas; testes e commit próprios antes043.

## Evidências e revisão

Executado em 02/11/2026: npm ci reproduzível, TypeScript, 16 unitários e build Vite aprovados. Browser Chromium final: nove testes aprovados em 1,0min (seis auth e três clientes), zero falhas/skips; API/PostgreSQL UUID próprios, reconstrução V1–V18 e duas identidades sintéticas privadas. Lista vazia, DV inválido, cadastro/duplicidade, edição, status ADMIN e ação ausente para OPERATOR mesmo com concessão indevida, conflito real após edição concorrente preservando campos, descarte/reconsulta,21 registros/paginação e falha/retry de leitura aprovados. Layout1280×720 e390×844 inspecionado, sem overflow. Recursos próprios removidos, laboratório preservado.

Rodadas iniciais encontraram nome de permissão incorreto no link, seletor de célula ambíguo e sincronização do teste após status. Corrigidos; revisão também separou document_conflict de version_conflict (duplicidade permite correção, versão obsoleta exige recarga) e bloqueou abertura de outro formulário durante edição/envio. Shell React separado do entrypoint para preservar HMR. A suíte completa foi repetida após as correções, sem relaxar gates.

Planejamento (60 IDs/39 requisitos/três hashes), links/UTF8, diff e staging/segredos conferidos antes do commit. Sem alteração Java/DDL; CI backend repetirá sua suíte própria, sem atribuir teste histórico a execução local desta019. Evidências/commit/CI do SHA publicado serão vinculados no card. Main sem PR; Em validação aguarda aceite explícito. Endereços/histórico continuam somente na API, fora do aceite específico de lista/busca/formulário desta tarefa.

# ADR-006 limites acadêmicos e evidências

Data: 30/10/2026. Tarefa: [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Estado: limites explícitos do pacote documentados; nenhuma funcionalidade ou RNF comprovado.

## Contexto e fontes

Especificação v1.0, seções 1, 2, 5 e 10. O projeto é acadêmico para portfólio; existem metas de infraestrutura avançada que dependem de ensaios reais. Documentação de arquitetura deve permitir distinguir alvo de comportamento executado.

## Decisão registrada

Pagamentos e estornos são exclusivamente locais e fictícios, com cenários determinísticos. PIX, cartões e boleto são rótulos demonstrativos. Não integrar gateways, bancos, cobrança real, NF-e ou API comercial financeira/fiscal. Comprovante interno não é documento fiscal.

Dois processos compartilham banco no laboratório; Compose e health checks futuros demonstram ambiente local, sem provar alta disponibilidade. Dados de demonstração devem ser sintéticos e identificados. Não versionar credenciais, tokens, dados reais de cartão ou exportações pessoais.

## Evidências exigidas

| Requisito | Arquitetura alvo | O que ainda deverá provar |
| --- | --- | --- |
| RNF04 | Leitura analítica dedicada/réplica/CQRS | Isolamento de leitura, lag e fallback em ensaio real |
| RNF05 | API stateless com balanceador | Múltiplas instâncias atendendo sem estado local exclusivo |
| RNF06/08/10 | Mensageria, idempotência, transações locais | Falhas e concorrência com PostgreSQL/RabbitMQ reais |
| RNF12 | Failover de banco em laboratório | Queda e recuperação automática medidas |
| RNF13 | Meta de uptime de 99,5% | Janela e método definidos; disponibilidade observada |
| RNF15 | TLS 1.3 no ambiente implantado | Negociação real externa/interna; HTTP local não comprova |
| RNF19 | Evolução compatível sem indisponibilidade | Deploy/migration com tráfego e versões coexistentes |
| RNF20 | Backup e recuperação | Restauração ensaiada e integridade dos dados |

Nenhuma linha está implementada ou atendida nesta tarefa. Definições de carga média, TTLs, períodos e infraestrutura permanecem abertas; não inventar números para declarar aprovação. RNF04 não exige introduzir réplica no primeiro scaffold: seu laboratório tem tarefas próprias.

## Justificativa e alternativas

Implementar infraestrutura de produção desde o primeiro incremento ampliaria escopo e não eliminaria a necessidade de comprovação. O laboratório incremental preserva os requisitos e registra a evidência na tarefa correspondente. Substituir evidência por diagrama ou driver fake foi rejeitado como critério de conclusão.

## Consequências e validação

Planejamento, código, teste local e ensaio implantado são evidências diferentes. CI de integridade documental não comprova testes de produto. Versão de biblioteca não implica atendimento de segurança ou disponibilidade. Mudança de escopo acadêmico deve ser refinada, preservando RF/RNF e IDs.

Nesta tarefa, verificar coerência dos documentos, links, fonte original e matriz de aceite. Os testes operacionais ficam nas tarefas de implementação. Deploy permanece sujeito à autorização específica; nenhuma publicação de ambiente foi realizada.

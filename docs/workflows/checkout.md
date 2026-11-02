# Checkout idempotente — PDV-035

Contrato aprovado pelo proprietário e [ADR-008](../decisions/ADR-008-estados-venda-pagamento.md). [Spec e evidências](../../sdd/features/PDV-035-checkout-idempotente.md). Nenhum serviço financeiro real.

ADMIN/OPERATOR com sale.checkout fazem POST /carts/{id}/checkout somente no próprio carrinho. Idempotency-Key obrigatória: 1–128 caracteres ASCII A–Z/a–z/0–9/underscore/ponto/dois-pontos/hífen, exatamente um valor. Corpo estrito: version (inteiro não negativo), method e scenario. Métodos são somente rótulos CARTAO_CREDITO/CARTAO_DEBITO/PIX/BOLETO/DINHEIRO. Cenários explícitos APROVADO/RECUSADO/TIMEOUT; checkout ainda não executa simulação.

201 retorna id/number/cartId/customerId/reservationId/paymentId/status/paymentStatus/method/scenario/version/total/items/createdAt/updatedAt, com Cache-Control no-store e Idempotent-Replay true/false. number é sequência da venda com possíveis lacunas por rollback; não é número fiscal/comprovante. GET /sales/{id}, com sale.read e propriedade, lê estado atual em REPEATABLE_READ. Não existe endpoint para forçar aprovação/conclusão.

Primeira execução: arbitrar chave por ator; bloquear carrinho; verificar revisão, conversão prévia e itens; manter locks compartilhados de cliente/produtos ativos; comparar preços atuais; reservar por UPDATE condicionado a disponibilidade em ordem de produto. Snapshot de venda/itens, reserva, pagamento CRIADO, intenção durável e resposta original confirmam ou revertem juntos. Versão do carrinho avança e o agregado fica congelado, inclusive por guardas PostgreSQL. Sem desconto, baixa física ou arredondamento. NUMERIC(60,2) preserva os extremos de preço/quantidade admitidos no carrinho.

Fingerprint SHA-256 cobre carrinho, versão e enums. Chave escopada por ator/checkout sem expiração. Replay autorizado devolve resposta original antes de consultar estado mutável; preço/ativo ou avanço posterior não alteram snapshot. Outra chave para o carrinho dá conflito. Falhas não persistem sucesso e permitem retry após correção.

400: cabeçalho/corpo/campo desconhecido inválido. 401/403: autenticação/permissão reavaliadas inclusive no replay. 404: agregado inexistente/alheio, inclusive ADMIN. 409: versão, vazio/convertido, preço alterado (price_changed), inativo, indisponibilidade ou chave divergente. 503: persistência, sem expor SQL/dados. Para revisar preço, remover/reincluir captura o preço atual conforme contrato do carrinho.

V14 adiciona vínculos reais venda/carrinho/ator/reserva/pagamento, unicidade por carrinho/reserva, snapshots/auditoria/resposta imutáveis e intenção durável. Intenção não equivale a Outbox/Inbox integrada e API não publica diretamente após commit. Coordenação/simulador 036/037; transporte confiável 038; baixa/comprovante 039; reconciliação/liberação 040; cancelamento 041.

Verify/Migrate/Validate via scripts/api.ps1, PostgreSQL real e bancos de teste exclusivos. Development opt-in adiciona sale.read/checkout aos dois papéis, sem conta padrão. Run continua exigindo TokenTtlSeconds e ReservationTtlSeconds explícitos.

# Rastreamento de venda — PDV-045

GET `/sales/{id}/timeline` exige ADMIN/OPERATOR e `sale.read`, somente venda própria. Venda alheia/inexistente retorna404, falta de sessão401 e falta de permissão403. `Cache-Control: no-store`. Não devolve snapshots, credenciais, dados de cliente ou motivos administrativos.

`events` contém ID, revisão, tipo, estado da venda/pagamento e instante ISO UTC, ordenado por revisão. Estado ausente em evidência histórica permanece null. `deliveries` contém tipo/ID da mensagem, estado PENDING/PUBLISHED/DEAD, tentativas, erro seguro e horário; falha de publicação não é resultado financeiro. Leituras compartilham snapshot REPEATABLE_READ. A correlação de32hex deriva do UUID do pagamento e permite relacionar a operação; instrumentação de spans/centralização pertence à PDV-049.

Na tela de pagamento, “Consultar histórico” carrega os eventos reais e “Atualizar histórico” reconsulta. Datas são exibidas no fuso do navegador com indicação do timezone. Falha oculta a trilha anterior e permite repetir a leitura; troca de venda/sessão aborta a consulta. A linha do tempo não cria transições ou confirma estados futuros.

Ver [spec e verificações](../../sdd/features/PDV-045-rastreamento-venda.md), [pagamentos](frontend-payments.md) e [contrato OpenAPI](../../contracts/README.md). Cancelamento/estorno permanecem PDV-041; nenhuma alteração de domínio ou DDL neste incremento.

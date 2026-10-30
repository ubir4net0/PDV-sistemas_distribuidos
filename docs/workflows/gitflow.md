# Gitflow e proteções

main é a linha de liberação; develop integra; feature/PDV-XXX-slug parte de develop; hotfix/PDV-XXX-slug parte de main; release/versao parte de develop. Commits convencionais com ID e PR para develop. Sem force-push main/develop, exclusão de branches, merge ou deploy automático.

Em 30/10/2026 foi confirmado repositório vazio. A exceção inicial cria um commit documental base em main e o mesmo commit em develop; as alterações seguintes ficam em feature/PDV-002-bootstrap-planejamento e PR. Essa exceção está consumida, não é fluxo recorrente.

Proteções de branches são parte do aceite integral PDV-002, após revisão do setup. Recomenda-se PR obrigatório, aprovação de revisão, checks pertinentes, resolução de discussões, bloqueio de force-push e exclusão. Não impor check de aplicação que ainda não existe. Este ciclo não presume proteção ativa; verificar/configurar com o responsável no setup, sem inventar aprovação de equipe.

# Fluxo individual e proteção do histórico

Conforme a ADR-001, o proprietário trabalha sozinho e usa commits diretos em main. Não exigir Gitflow, feature branch, develop, PR ou aprovação de outra conta. O caminho deste arquivo foi mantido para preservar referências existentes; o fluxo Gitflow original foi substituído.

O [roteiro operacional](task-execution.md) apresenta os comandos do ciclo e a conferência de CI por SHA; os [gates Notion](notion-task.md) definem transições e limites de escrita.

Conferir git status e preservar mudanças locais. Em main, atualizar origin/main por fast-forward quando o working tree permitir; executar a tarefa/spec e autorevisar o diff. Criar commit convencional com ID e registrar hash, fonte viva, testes e limitações na tarefa. Fazer push normal para o remoto autorizado quando incluído no escopo. Não criar PR automaticamente. Branches auxiliares são opcionais e não alteram os gates de validação.

main mantém proteção contra force-push e exclusão, aplicada também a administradores, permitindo commits diretos sem PR ou revisão obrigatórios. A CI roda no push de main; falha deve ser corrigida antes de prosseguir, sem alegar teste aprovado. Commit e push não concluem tarefa por si só.

O bootstrap inicial criou main/develop e a feature documental. Na adoção do fluxo individual, main incorpora os commits já existentes por fast-forward e recebe o commit desta decisão. O PR anterior é substituído; branches e fontes originais ficam preservadas. Deploy e ações destrutivas continuam exigindo autorização específica.

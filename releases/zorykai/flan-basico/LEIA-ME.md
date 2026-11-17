# Flan Zorykai — comandos básicos

Substitua o Flan-Zorykai.jar antigo por Flan-Zorykai-basico.jar e reinicie o servidor. Instale somente uma cópia do Flan.

Para jogadores comuns, mantém criar/selecionar e redimensionar terrenos com a enxada, inspecionar com graveto, listar, obter informações, renomear, excluir os próprios terrenos, confirmar operações, escolher modo de seleção e gerenciar grupos/amigos/permissões. O menu de gerenciamento e a ajuda continuam disponíveis.

Os demais comandos exigem OP no nível configurado em `permissionLevel` no Flan (2 na configuração enviada). Essa condição é adicional às permissões originais do Flan. Atalhos administrativos dentro de comandos básicos também exigem esse nível. Uma concessão de permissão no LuckPerms não elimina a exigência de OP dos comandos extras.

Comandos básicos: `/flan add`, `group`, `info`, `switchMode`, `permission`, `confirm`, `delete`, `deleteAll`, `deleteSubClaim`, `deleteAllSubClaims`, `expand`, `list`, `name`, `menu`, `personalGroups`, `help` e `?`. Regras originais de propriedade e permissões continuam válidas.

Comandos extras reservados para OP incluem teleporte, home, compra/venda de blocos, transferência, configuração de mensagens, fake players, resgate, administração e recarga. Essa variante restringe comandos; os menus e as permissões internas de ações do Flan são preservados.

Não altera os dados de terrenos/jogadores, as traduções, os nomes visuais, a seleção por itens nem a integração com Guard. O cooldown também não foi modificado: mantenha `"claimingCooldown": 0` para contornar o erro confirmado no JAR original.

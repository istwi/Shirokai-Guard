# Zorykai Guard — variante visual

[Baixar o JAR 0.5.1-alpha.1](releases/zorykai/v0.5.1-alpha.1/shirokai-guard-0.5.1-alpha.1-zorykai-visual.jar) · [Checksum](releases/zorykai/v0.5.1-alpha.1/SHA256SUMS.txt)

Esta branch distribui uma variante **somente visual** do JAR original: nome do mod,
título padrão do menu, prefixo de erro e cabeçalho de ajuda usam Zorykai/ZORYKAI.
Os fontes, métodos, IDs, namespaces, comandos, permissões, pacotes e classes mantêm
os nomes originais. As instruções dos métodos não foram modificadas.

Substitua o JAR antigo por este; não mantenha os dois instalados. Configurações
externas já existentes em `config/shirokai-guard/menus.json` e `messages.json` não
são sobrescritas e podem continuar exibindo nomes personalizados/antigos.

O JAR modificado passou nos oito GameTests com Flan real. Consulte o
[relatório e detalhes](releases/zorykai/v0.5.1-alpha.1/LEIA-ME.md) e a
[auditoria binária](releases/zorykai/v0.5.1-alpha.1/audit.json).

Os fontes continuam intocados: recompilar o projeto normalmente produz a variante
original Shirokai. O JAR visual pronto está no link acima. A branch `main` não foi alterada.

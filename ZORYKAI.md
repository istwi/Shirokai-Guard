# Zorykai Guard

[Baixar o JAR 0.5.2-alpha.1](releases/zorykai/v0.5.2-alpha.1/shirokai-guard-0.5.2-alpha.1-zorykai-visual.jar) · [Checksum](releases/zorykai/v0.5.2-alpha.1/SHA256SUMS.txt)

Esta atualização acrescenta recuperação por `/sg bypass`, autorização adicional para OP nível 4 e proteção contra criação/redimensionamento de claims Flan sobre regiões Guard. Consulte [todos os comandos](docs/COMANDOS.md) e o [changelog](CHANGELOG.md).

O JAR usa Zorykai/ZORYKAI nos quatro textos visuais da variante anterior. IDs, namespaces, comandos, permissões, pacotes, classes e caminhos de configuração continuam iguais. As mudanças funcionais estão nos fontes desta branch; a aplicação posterior da marca visual altera apenas constantes de texto e o nome visual em `fabric.mod.json`.

Substitua o JAR antigo pelo novo; não mantenha os dois instalados. Configurações existentes em `config/shirokai-guard/menus.json` e `messages.json` são preservadas e podem continuar exibindo nomes personalizados/antigos. Reinicie o servidor.

Recompilar os fontes produz a marca visual original Shirokai. O JAR Zorykai pronto está no link acima. A branch `main` permanece na versão anterior.

A versão visual anterior permanece em [0.5.1-alpha.1](releases/zorykai/v0.5.1-alpha.1/LEIA-ME.md).

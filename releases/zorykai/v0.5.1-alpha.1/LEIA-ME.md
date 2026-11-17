# Guard — alteração visual para Zorykai

Base: istwi/Shirokai-Guard, versão 0.5.1-alpha.1, commit `6cc761041f12182f5001384e7b96073f3b8f54a0`.
Todas as branches remotas foram consultadas; somente `main` foi encontrada.

## Textos alterados no JAR

- Nome visual do mod: `Shirokai Guard` → `Zorykai Guard`.
- Título padrão: `✦ SHIROKAI GUARD` → `✦ ZORYKAI GUARD`.
- Prefixo padrão de erro: `Shirokai Guard: %s` → `Zorykai Guard: %s`.
- Cabeçalho de `/sg help`: somente `Shirokai` → `Zorykai`.

Os outros comandos e argumentos no texto de ajuda permaneceram idênticos.
Cores, caracteres de formatação, placeholders e demais textos foram preservados.

## Integridade

Somente três entradas internas foram alteradas: dois arquivos de classe (apenas
constantes de texto, sem recompilação) e o campo `name` de `fabric.mod.json`.
As outras 103 entradas ficaram idênticas byte a byte, incluindo biblioteca
embutida, mixins e refmap. As instruções dos métodos, campos, assinaturas e atributos
das classes modificadas permaneceram idênticos; novas constantes UTF-8 impedem
qualquer mudança indireta em identificadores que compartilhassem o texto original.

ID `shirokai_guard`, versão, namespaces, diretórios de configuração, comandos,
permissões, pacotes, classes e integrações continuam com os nomes técnicos originais.
Nomes técnicos/loggers e diagnósticos exclusivos do console não foram renomeados.
Nenhum arquivo-fonte ou arquivo rastreado no Git foi alterado. O JAR original foi preservado.

O próprio JAR modificado foi carregado no servidor de testes com Flan real:
**8 GameTests aprovados, nenhuma falha**. Os arquivos de configuração novos
confirmaram o título `✦ ZORYKAI GUARD` e o prefixo `Zorykai Guard: %s`.
Os testes não constituem uma sessão multiplayer completa nem inspeção visual por cliente.

SHA-256 do arquivo modificado:
`407bbf9c32413b71a9565f36788ce389d60d31ee8e2cfb0e5279a76c32c15a08`

## Instalação e configurações existentes

Pare o servidor, substitua o JAR antigo do Guard por este e reinicie. Não instale
as duas variantes simultaneamente: elas mantêm o mesmo ID e versão para compatibilidade.
Raids e Essentials não foram alterados nesta entrega.

**Arquivos externos já existentes não são sobrescritos.** Se `menus.json` ou
`messages.json` em `config/shirokai-guard/` já guardam textos antigos/personalizados,
esses valores continuam prevalecendo. A mudança dos textos desses arquivos exigiria
uma edição separada; este trabalho modifica somente os textos padrão contidos no JAR.

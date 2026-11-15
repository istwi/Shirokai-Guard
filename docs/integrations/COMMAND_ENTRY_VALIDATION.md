# Validação — command-only, Guard 0.4.0-alpha.1

Executada em 15/11/2026 com Java 21, Minecraft 1.21.1 e wrappers dos repositórios.
Bases: Guard `50e875a`, Essentials `cf9d9f0`. O Raids `3b70114` não foi alterado.

| Perfil | Resultado |
| --- | --- |
| JUnit Guard | 54 aprovados; nenhuma falha, erro ou teste ignorado |
| JUnit Essentials atualizado | 47 aprovados; nenhuma falha, erro ou teste ignorado |
| GameTests Guard + Cobblemon + Essentials atualizado + Raids existente | 62 aprovados |
| GameTests Guard sem os mods opcionais | 52 aprovados |
| JAR Guard + Flan 1.12.8 real | 8 aprovados |

Relatórios XML: [`../reports/command-entry`](../reports/command-entry).

## Casos exercitados

- Correspondência de comando **e argumentos**, recusando outro warp, prefixos,
  argumentos extras e namespace não cadastrado. Preservação de argumentos entre
  aspas, incluindo maiúsculas e espaços internos; sem curingas.
- Persistência de listas independentes por região; prioridade e herança do modo
  sem cópia implícita da lista; movimento para dentro recusado e saída permitida.
- `/sg entry-command` adicionando, listando, removendo e limpando comandos.
- Despacho pelo `CommandManager` real e aliases aninhados: a regra corresponde ao
  comando externo, sem herdar autorização do comando interno não cadastrado.
- Tokens vinculados ao jogador, regiões e destino, uso único, destino incorreto,
  alteração da lista durante a espera, troca da região protegida e desconexão.
- Limpeza do contexto ao lançar exceção e revogação após falha do callback.
- API de raids incapaz de abrir região `command-only`, e token de comando incapaz
  de abrir região `authorized-only`. O adaptador de produção do Raids existente
  continua passando no perfil conjunto.
- Comandos **reais** `/setwarp loja`, `/setwarp outra`, `/warp loja`, `/warp outra`,
  `/tpa <jogador>` e `/tpaccept`, executados pelo Essentials atualizado.
- Warp não cadastrado com o mesmo destino recusado. `/warp loja` executa um warmup
  real de um segundo e entra. Outro teleporte de sistema para o mesmo destino,
  durante o warmup, não pode aproveitar nem consumir seu token.
- Após a saída, novo teleporte comum para a loja é recusado. TPA aceito por jogador
  dentro da loja não autoriza entrada. Sem a região restrita, outro warp funciona.
- Regressões de proteção, login/respawn, movimento/veículos, Ender Pearl, mudança de
  dimensão e da integração anterior de autorização de raids continuam na suíte.
- Flan real preserva suas recusas independentes, inclusive diante do bypass Guard.

O teste de warmup inicialmente verificava uma quantidade fixa de ticks. O servidor
GameTest avança mais rápido que o relógio usado pelo Essentials; o teste foi ajustado
para esperar a conclusão real da operação. A execução final completou os 62 testes.
O relógio do Essentials permaneceu real; o teste não reduziu o warmup nem alterou as regras de autorização para fazê-lo passar.

## Reproduzir

```bash
# Na raiz do Guard, com JDK 21
./gradlew --no-daemon test runGametest build
./gradlew --no-daemon -PwithCobblemon -PwithArenaIntegrations \
  -ParenaIntegrationMods=/caminho/absoluto/jars test runGametest build
# Em Essentials atualizado
./gradlew --no-daemon test build
# Em validation/flan
./gradlew --no-daemon runGametest
```

O diretório de integração deve conter somente Essentials 0.3.2-guard.1 e Raids
0.1.20-guard.1. O patch incremental do Essentials é
[`patches/Shirokai-Essentials-command-entry.patch`](patches/Shirokai-Essentials-command-entry.patch),
aplicável sobre `cf9d9f0`. Bibliotecas internas do Essentials são expostas ao runtime
de desenvolvimento pelo perfil, sem alteração dos JARs distribuídos.

## Limites

Não foi jogada uma raid multiplayer completa. O perfil testa a API e o adaptador
real do Raids, preservando a separação entre os modos. A carga assíncrona de chunks
preserva o token no objeto da operação, mas não foi forçada num mundo descarregado
nesta rodada. Não há suporte automático a qualquer mod de teleporte: métodos que
não chamam a API de comando são recusados nas regiões `command-only`. RTP com
coordenadas dinâmicas calculadas após o comando precisa de integração específica.

A variante conjunta Flan + Cobblemon e LuckPerms real não foram repetidos nesta
atualização; seus resultados anteriores permanecem nos relatórios históricos.

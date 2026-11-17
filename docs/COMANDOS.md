# Manual completo de comandos — Shirokai Guard

Este manual corresponde ao **Guard 0.5.1-alpha.1**, para Minecraft 1.21.1 / Fabric / Java 21.
A entrada por comando usa **Essentials 0.3.2-guard.1**; a integração de raids usa **Raids 0.1.20-guard.1**.

## Índice

- [Como ler e usar os comandos](#como-ler-e-usar-os-comandos)
- [Começar: criar uma região protegida](#começar-criar-uma-região-protegida)
- [Seleção de áreas](#seleção-de-áreas)
- [Criar, consultar e alterar regiões](#criar-consultar-e-alterar-regiões)
- [Owners e members](#owners-e-members)
- [Configurar flags e grupos](#configurar-flags-e-grupos)
- [Referência de todas as flags](#referência-de-todas-as-flags)
- [Entrada por raid ou comando](#entrada-por-raid-ou-comando)
- [Prioridades e herança](#prioridades-e-herança)
- [Menus, visualização e templates](#menus-visualização-e-templates)
- [Manutenção e diagnóstico](#manutenção-e-diagnóstico)
- [Permissões](#permissões)
- [Receitas prontas](#receitas-prontas)
- [Problemas frequentes](#problemas-frequentes)

## Como ler e usar os comandos

No chat do Minecraft, use `/sg`. No console, omita a barra inicial.

- `<região>`, `<jogador>` e outros campos entre `< >` são obrigatórios: substitua pelo valor real, sem os sinais.
- `[x y z]` e outros campos entre `[ ]` são opcionais: não digite os colchetes.
- Os exemplos usam `loja`, `spawn` e `raid_arena` como IDs. Use os nomes das suas regiões.
- IDs de regiões aceitam letras minúsculas `a-z`, números, `_` e `-`, até 64 caracteres.
- Regiões pertencem a uma **dimensão**. Estar no Overworld não permite editar pelo mesmo ID uma região do Nether.
- A seleção também pertence à dimensão. Ao trocar de dimensão, faça uma seleção válida nela.
- Os comandos de seleção, menu, visualização, bypass e confirmação de templates precisam de um jogador.
- Consultas e alterações sem posição pessoal podem ser executadas pelo console; usam o mundo da fonte do comando. Para administrar outra dimensão pelo console, use o contexto vanilla de `/execute in <dimensão> run sg ...` quando aplicável.

### Ajuda

```mcfunction
/sg
/sg help
```

Mostram um resumo dos comandos. Para examinar uma região ou suas regras, use `/sg info <região>` e `/sg flags <região>`.

**Criar uma região não bloqueia automaticamente construção nem acesso.** Flags não configuradas normalmente permitem ações, salvo herança ou outras regras aplicáveis. Os defaults de `flags.json` podem acrescentar regras a novas regiões.

## Começar: criar uma região protegida

Exemplo no Overworld, cobrindo de X=100/Z=100 até X=150/Z=150:

```mcfunction
/sg pos1 100 -64 100
/sg pos2 150 319 150
/sg define spawn
/sg flag spawn build deny
/sg flag spawn pvp deny
/sg info spawn
/sg visualize spawn
```

As duas posições são os cantos opostos de um cuboide. Os blocos das bordas estão incluídos.
Os valores `-64` e `319` são exemplos da altura do Overworld vanilla; em outra dimensão, prefira selecionar e usar `/sg expand vert`.

Para permitir construção aos owners:

```mcfunction
/sg flag spawn build allow @owners
```

O criador entra como owner quando a região é criada por um jogador, mas **ser owner não concede bypass automático**. É a regra para `@owners` que libera a ação.

## Seleção de áreas

Selecionar uma área ainda não cria nem redimensiona uma região existente.

### `/sg wand`

Ativa a ferramenta de seleção e entrega um machado de madeira:

- Clique esquerdo em bloco: define `pos1`.
- Clique direito em bloco: define `pos2`.

O machado de madeira só atua como ferramenta de seleção quando essa função está ativa para o jogador. Use `/sg sel clear` para desativá-la.

### `/sg pos1 [x y z]` e `/sg pos2 [x y z]`

Definem o primeiro e o segundo canto. Sem coordenadas, usam o bloco da posição atual do jogador.

```mcfunction
/sg pos1
/sg pos2
/sg pos1 100 60 100
/sg pos2 150 100 150
```

As coordenadas fornecidas aqui são números inteiros absolutos. Esse parser não aceita `~` ou `^`.
A ordem dos cantos não importa: o Guard calcula os mínimos e máximos.

### `/sg sel clear`

```mcfunction
/sg sel clear
```

Limpa a seleção e desativa a wand. Também encerra estados locais associados, como visualização e operações pendentes da interface/template.
Não apaga regiões já criadas.

### `/sg expand <quantidade> <direção>`

Aumenta a seleção na direção indicada:

```mcfunction
/sg expand 10 east
/sg expand 5 up
```

| Direção | Efeito |
| --- | --- |
| `north` | Expande para Z negativo |
| `south` | Expande para Z positivo |
| `east` | Expande para X positivo |
| `west` | Expande para X negativo |
| `up` | Aumenta o limite superior Y |
| `down` | Diminui o limite inferior Y |

A quantidade deve ser um inteiro positivo. Não muda a direção conforme o olhar do jogador.

### `/sg expand vert`

```mcfunction
/sg expand vert
```

Expande a seleção por toda a altura da dimensão atual, preservando X e Z.
Útil para proteger uma loja, spawn ou arena contra entrada por cima ou por baixo.

### `/sg contract <quantidade> <direção>`

Encolhe a seleção retirando blocos do lado informado:

```mcfunction
/sg contract 5 east
/sg contract 2 up
```

Usa as mesmas seis direções de `expand`. Uma contração que inverta os limites é recusada.
Não existe `/sg contract vert`.

### Aplicar a seleção

Para criar: `/sg define <região>`.
Para alterar os limites de uma região existente: `/sg redefine <região>`.

## Criar, consultar e alterar regiões

### `/sg define <região>`

Cria uma região com a seleção atual:

```mcfunction
/sg define loja
```

Exige os dois cantos, ID ainda não usado na dimensão e disponibilidade dentro do limite de regiões configurado.
As flags padrão de `flags.json` são copiadas para a região nova. Um jogador criador é adicionado como owner.

### `/sg define __global__`

```mcfunction
/sg define __global__
```

Cria uma região que cobre toda a dimensão atual, sem precisar de seleção. Ela não tem uma caixa para selecionar ou visualizar.
Não é uma região de todas as dimensões ao mesmo tempo. Não recebe prioridade especial automaticamente: configure prioridades conforme as exceções desejadas.

### `/sg list`

Lista os IDs das regiões da dimensão atual:

```mcfunction
/sg list
```

### `/sg info <região>`

Mostra os dados da região, como dimensão, limites, prioridade, pai, pessoas e flags:

```mcfunction
/sg info loja
```

### `/sg flags <região>`

Mostra as flags locais configuradas e os nomes disponíveis no servidor:

```mcfunction
/sg flags loja
```

As flags Pokémon só aparecem como disponíveis quando Cobblemon está instalado.
Essa saída de flags locais não é uma explicação completa do resultado de herança/sobreposição; confira também pais e outras regiões no local.

### `/sg select <região>`

Copia os limites da região para sua seleção:

```mcfunction
/sg select loja
/sg expand 5 east
/sg redefine loja
```

O primeiro comando apenas seleciona. O redimensionamento só altera a região depois de `redefine`.
A região `__global__` não pode ser selecionada porque não tem limites.

### `/sg redefine <região>`

Substitui os limites da região pela seleção atual, mantendo sua identidade, flags, pessoas, prioridade e pai:

```mcfunction
/sg pos1 100 60 100
/sg pos2 160 100 150
/sg redefine loja
```

Não renomeia a região nem cria uma segunda região. A região global não aceita uma caixa comum.

### `/sg remove <região>`

Remove a definição da região e suas regras:

```mcfunction
/sg remove loja
```

**Não apaga blocos nem construções.** Este comando de chat não pede uma segunda confirmação.
Se a região tem filhos, remova primeiro os vínculos deles com `/sg setparent <filho> none`, ou reorganize a herança.

### `/sg setpriority <região> <inteiro>`

Define a prioridade usada nas sobreposições:

```mcfunction
/sg setpriority spawn 0
/sg setpriority loja 10
```

Valores maiores têm precedência para flags definidas. A prioridade é um inteiro e também aceita negativos.
Uma região de prioridade maior sem valor para determinada flag não apaga automaticamente a regra de uma região de prioridade menor.
Veja [Prioridades e herança](#prioridades-e-herança).

### `/sg setparent <região> <pai|none>`

Configura ou remove o pai da região:

```mcfunction
/sg setparent loja spawn
/sg setparent loja none
```

O pai precisa existir na mesma dimensão. Ciclos são recusados.
O vínculo não altera os limites e não exige que a caixa filha esteja fisicamente dentro da caixa pai.
Flags ausentes podem ser herdadas. Owners e members do pai também influenciam a classificação de grupos.

## Owners e members

Os comandos recebem um nome de jogador **online** ou UUID. Para offline, informe UUID.

| Comando | O que faz | Exemplo |
| --- | --- | --- |
| `/sg addowner <região> <jogador|UUID>` | Adiciona owner | `/sg addowner loja Ana` |
| `/sg removeowner <região> <jogador|UUID>` | Remove owner local | `/sg removeowner loja Ana` |
| `/sg addmember <região> <jogador|UUID>` | Adiciona member | `/sg addmember loja Bruno` |
| `/sg removemember <região> <jogador|UUID>` | Remove member local | `/sg removemember loja Bruno` |

Essas listas classificam o jogador para aplicar flags por grupo. **Elas não concedem automaticamente permissão administrativa para executar `/sg`**, nem permitem qualquer ação sem uma flag apropriada.
Remover uma pessoa da lista local não remove um vínculo herdado do pai. Se o jogador está nas duas categorias aplicáveis, owners têm precedência na classificação.

Exemplo: todos sem construir, owners e members com construção:

```mcfunction
/sg flag loja build deny
/sg flag loja build allow @owners
/sg flag loja build allow @members
```

## Configurar flags e grupos

### `/sg flag <região> <flag> <valor> [@grupo]`

```mcfunction
/sg flag loja build deny
/sg flag loja chest-access allow @owners
/sg flag loja greeting Bem-vindo à loja!
```

Sem grupo, altera `@all`.

| Grupo | Jogadores atingidos |
| --- | --- |
| `@all` | Regra geral, usada quando não existe valor específico para o grupo do jogador |
| `@owners` | Owners locais ou herdados |
| `@members` | Members locais ou herdados que não foram classificados como owners |
| `@nonmembers` | Quem não é owner nem member; inclui ações sem um jogador identificado na resolução |

O grupo, quando informado, fica **no final** do comando.

### `allow`, `deny` e `unset`

Para a maioria das flags:

- `allow`: permite aquela ação na resolução do Guard. Não força outro mod a permitir.
- `deny`: recusa aquela ação.
- `unset`: remove o valor **local daquele grupo**, voltando a considerar herança, fallback e sobreposições.

```mcfunction
/sg flag loja build deny
/sg flag loja build allow @owners
/sg flag loja build unset @owners
```

O último comando remove a exceção local dos owners. A regra `build deny` para todos pode voltar a valer para eles.
`unset` não é sinônimo de `allow`.

### Valores especiais

| Flags | Valores |
| --- | --- |
| `entry-mode` | `normal`, `deny`, `authorized-only`, `command-only`, `unset` |
| `greeting`, `farewell` | Texto literal ou `unset` para remover |
| `blocked-commands`, `allowed-commands` | Raízes de comandos separadas por vírgula ou `unset` |
| `entry-commands` | Armazenamento da lista de entrada; administre com `/sg entry-command` |

`invincible allow` ativa invulnerabilidade; `deny` não causa dano por si só.
`hunger deny` congela o processamento de fome, incluindo a regeneração feita pelo HungerManager.

## Referência de todas as flags

As descrições abaixo indicam o efeito de **`deny`**, salvo quando outro valor for indicado.
Proteções se aplicam aos caminhos vanilla e de integração implementados. Não há garantia de interceptar um mod que escreva diretamente no mundo fora desses contratos.

### Construção, uso e mecanismos

| Flag | Efeito e observações |
| --- | --- |
| `build` | Fallback geral de quebra e colocação. Não significa automaticamente impedir todo uso de item ou abertura de inventário. |
| `block-break` | Impede quebra de blocos; quando ausente, consulta `build`. |
| `block-place` | Impede colocação de blocos; quando ausente, consulta `build`. |
| `interact` | Recusa interações de uso com blocos nos callbacks protegidos. |
| `use` | Recusa uso de itens e ações de uso de bloco verificadas pelo Guard. |
| `chest-access` | Recusa acesso a blocos que fornecem telas/inventários, como baús. Outras flags de uso também podem recusar. |
| `item-frame` | Protege interação/dano de molduras nos hooks cobertos. |
| `armor-stand` | Protege interação/dano de suportes de armadura nos hooks cobertos. |
| `piston` | Recusa movimentos de pistão nas origens/destinos verificados. |
| `hopper` | Recusa transferências de funis nos caminhos protegidos. |
| `farmland-trample` | Impede pisoteamento de terra arada. |
| `water-flow` | Impede propagação de água nos destinos verificados. |
| `lava-flow` | Impede propagação de lava nos destinos verificados. |

Uma liberação de `chest-access` não supera `interact deny`, `use deny` ou a recusa de outro mod.
A exceção opcional de crates permite que o mod de crates processe a interação com seus próprios blocos; não libera construção geral nem ignora Flan.

### Ambiente e crescimento

| Flag | Efeito |
| --- | --- |
| `fire-spread` | Impede espalhamento de fogo nos hooks protegidos. |
| `ice-form` | Impede formação de gelo. |
| `ice-melt` | Impede derretimento de gelo. |
| `snow-melt` | Impede derretimento de neve. |
| `leaf-decay` | Impede decomposição natural de folhas. |
| `crop-growth` | Impede os crescimentos implementados, incluindo plantações, cana, bambu, cacto e outros hooks registrados. Não promete impedir toda mutação de plantas por qualquer mod. |

### Entidades e combate

| Flag | Efeito |
| --- | --- |
| `entity-spawning` | Recusa adição de entidades ao mundo nos hooks cobertos; é mais amplo que apenas criaturas hostis. |
| `mob-spawning` | Recusa spawn das entidades classificadas como mobs. |
| `projectile` | Recusa spawn de projéteis e dano por projéteis nos caminhos protegidos. |
| `mob-damage` | Impede dano de mobs a jogadores nos hooks cobertos. |
| `pvp` | Impede dano de jogador contra jogador; a resolução considera os locais dos envolvidos. |
| `entity-damage` | Recusa dano a entidades nos caminhos cobertos, com especializações para molduras/suportes e Pokémon. |
| `entity-interact` | Recusa interação com entidades nos caminhos cobertos, com especializações para molduras/suportes e Pokémon. |
| `tnt` | Protege contra efeitos de explosões classificadas como TNT. |
| `creeper-explosion` | Protege contra efeitos de explosões de creepers. |
| `other-explosion` | Protege contra outras explosões classificadas pelo Guard. |

Blocos protegidos contra quebra também são considerados na filtragem de destruição de explosões.
Flags de combate não concedem automaticamente permissão para captura ou batalhas Pokémon.

### Jogadores, entrada e teleporte

| Flag | Efeito |
| --- | --- |
| `entry` | Recusa entrada em regiões nos caminhos de fronteira/teleporte protegidos. Também é considerada na segurança de login/respawn. |
| `exit` | Recusa saída nos caminhos protegidos. Pode impedir retorno de uma arena; configure conscientemente. |
| `teleport` | Recusa teleportes quando a origem ou o destino é protegido por essa negativa. Não é uma lista de comandos. |
| `entry-mode` | Escolhe o mecanismo de entrada; veja a seção seguinte. |
| `entry-commands` | Guarda os comandos completos da lista local de `command-only`; use `/sg entry-command`. |
| `enderpearl` | Recusa o uso/teleporte da Ender Pearl no hook específico. A proteção central de entrada ainda pode recusar uma pérola quando essa flag estiver liberada. |
| `chorus-fruit` | Recusa o uso de Chorus Fruit no hook específico. A proteção central continua verificando os teleportes resultantes. |
| `fly` | Impede voo de habilidades e entrada já voando. Preserva a capacidade/permissão fora da área; espectador é exceção. Não controla élitros nem montaria. |
| `elytra` | Impede iniciar voo de élitros e entrar já planando. Não controla o voo de habilidades. |
| `invincible` | **`allow`** ativa invulnerabilidade do jogador na resolução do Guard. |
| `fall-damage` | Impede dano de queda. |
| `hunger` | Congela o processamento de fome e sua regeneração associada. |
| `item-drop` | Impede descarte voluntário nos caminhos protegidos. Não reescreve todos os drops de morte ou entregas de outros mods. |
| `item-pickup` | Impede que o jogador recolha itens do chão nos caminhos protegidos. |
| `greeting` | Texto mostrado na entrada, por exemplo `/sg flag loja greeting Bem-vindo à loja!`. |
| `farewell` | Texto mostrado na saída, por exemplo `/sg flag loja farewell Volte sempre!`. |

Mensagens de saudação/despedida usam a checagem periódica de regiões; não são um sistema de comandos clicáveis ou execução automática.

### Restrições de comandos no local

```mcfunction
/sg flag spawn blocked-commands kill,op
/sg flag evento allowed-commands spawn,msg
/sg flag evento allowed-commands unset
```

| Flag | Significado |
| --- | --- |
| `blocked-commands` | Bloqueia as raízes listadas quando o jogador executa comandos no local protegido. |
| `allowed-commands` | Restringe a execução no local às raízes listadas. Uma proibição aplicável ainda pode recusar. |

Essas flags comparam raízes, como `warp`, e não uma combinação de argumentos como `warp loja`.
Use vírgulas entre nomes; `*` é reconhecido nessas listas como correspondência geral.
Um namespace não escapa de um bloqueio no nome simples. Outros aliases precisam ser considerados explicitamente.

**Não use `blocked-commands back,home,tpa` para configurar entrada exclusiva em uma loja.** Isso bloquearia a execução desses comandos no local de origem, em vez de implementar a verificação específica de destino. Para isso, use `entry-mode command-only`.

### Pokémon — Cobblemon opcional

| Flag | Efeito |
| --- | --- |
| `pokemon-mount` | Impede subir em Pokémon e entrar já montado. Não bloqueia barcos/cavalos comuns, Pokémon no ombro ou Pokémon voando sem jogador. |
| `pokemon-spawn` | Recusa spawn/adição de Pokémon nos hooks da integração. |
| `pokemon-capture` | Recusa o evento de tentativa de captura antes de a captura começar. |
| `pokemon-battle` | Recusa a abertura de batalha pelo evento de pré-início verificado. |
| `pokemon-interact` | Recusa interações de Pokémon nos callbacks e pacotes cobertos. |
| `pokemon-damage` | Recusa dano à entidade Pokémon nos caminhos protegidos; não reescreve o HP interno das batalhas. |
| `pokemon-pasture` | Protege uso do Pasture e operações de tether/adicionar/retirar cobertas pela integração. |

Exemplo:

```mcfunction
/sg flag spawn pokemon-capture deny
/sg flag spawn pokemon-battle deny
```

Essas flags exigem Cobblemon instalado. **`pokemon-breeding` não existe nesta versão.**
Não se alteram recompensas, HP ou regras internas de participação do Raids por meio dessas flags.

## Entrada por raid ou comando

### Modos de `entry-mode`

| Valor | Resultado |
| --- | --- |
| `normal` | Não exige autorização desta flag; outras flags continuam valendo. |
| `deny` | Recusa entrada, inclusive a tentativa autorizada por integração. |
| `authorized-only` | Exige a autorização da integração de raids atualmente implementada. Não reconhece o texto `/raid` como senha. |
| `command-only` | Exige autorização do comando completo cadastrado para a região, por uma integração compatível. |
| `unset` | Remove o modo local para o grupo; pode voltar a herdar outra configuração. |

Esses modos protegem a **entrada**. Quem já está dentro pode mover-se ou teleportar entre pontos da mesma região, sujeito às demais flags.
Ao sair, a autorização anterior não permite voltar.
Login/respawn dentro de região restrita usa a busca segura existente em chunks carregados; sem destino permitido e seguro, o jogador é desconectado com orientação administrativa.

### Raid: somente `/raid entrar`

Depois de criar a região que cobre toda a arena:

```mcfunction
/sg flag raid_arena entry unset
/sg flag raid_arena teleport unset
/sg flag raid_arena exit unset
/sg flag raid_arena entry-mode authorized-only
```

- `/raid entrar`: o Raids atualizado valida a participação e solicita a autorização.
- `/raid sair`: retorna normalmente se as demais regras permitirem.
- `/back`, `/home`, TPA, outros teleportes e entrada caminhando: não concedem autorização para entrar na arena.
- Um novo `/raid entrar` solicita uma nova autorização.

O destino definido pelo Raids deve estar dentro da região. Os três `unset` removem apenas as negativas locais; pais e sobreposições ainda podem impor restrições.
`/raid entrar` e `/raid sair` pertencem ao mod Raids, não são subcomandos de `/sg`.

### Loja: somente `/warp loja`

```mcfunction
/sg flag loja entry-mode command-only
/sg entry-command loja add warp loja
```

O Essentials precisa ter o warp já criado. Por exemplo, se você tiver as permissões dele, fique no destino e use `/setwarp loja`.
O Guard não cria o warp nem altera o destino do Essentials.

| Situação | Resultado da proteção de entrada |
| --- | --- |
| Fora → `/warp loja` para a loja | Autorização concedida pelo Essentials atualizado, respeitando as demais regras |
| Fora → `/warp lojateste`, também dentro da loja | Bloqueado se esse comando não estiver cadastrado |
| Fora → `/back` para a loja | Bloqueado |
| Fora → TPA aceito por alguém na loja | Bloqueado |
| Caminhar de fora para dentro | Bloqueado |
| Já dentro → outro ponto da mesma região | A entrada não é nova; pode ser permitido pelas demais flags |
| Teleporte para uma região comum fora da loja | Continua com as regras normais |

### Todos os subcomandos de `entry-command`

```mcfunction
/sg entry-command loja add warp loja
/sg entry-command loja add loja
/sg entry-command loja list
/sg entry-command loja remove warp loja
/sg entry-command loja clear
```

| Sintaxe | O que faz |
| --- | --- |
| `/sg entry-command <região> add <comando completo>` | Adiciona uma entrada exata à lista local. Repetir a mesma entrada não cria duplicata. |
| `/sg entry-command <região> list` | Mostra a lista configurada nessa região. |
| `/sg entry-command <região> remove <comando completo>` | Remove somente a entrada exata informada. |
| `/sg entry-command <região> clear` | Remove todas as entradas locais. A região continua `command-only` e passa a não ter comandos autorizados nessa lista. |

Ao cadastrar, não precisa incluir `/` antes de `warp` ou `loja`.

A correspondência inclui **todos os argumentos**: `warp loja` não permite `warp`, `warp spawn` ou `warp loja extra`.
Não há curingas nessa lista. A raiz é normalizada para minúsculas; argumentos preservam maiúsculas/minúsculas e conteúdo entre aspas. Espaços separadores são normalizados.
Limites: 32 comandos, até 256 caracteres por comando e 2048 caracteres no valor total da lista.

Aliases precisam de regra própria para o comando realmente digitado. Cadastrar `loja` **não cria** `/loja`.
O alias precisa existir e encaminhar a execução para o fluxo integrado de teleporte.
A lista é local: herdar `command-only` de um pai não copia a lista de comandos dele.

O suporte pronto é para os teleportes de destino fixo do **Essentials atualizado** e aliases compatíveis desse fluxo, inclusive warmup e propagação da autorização na carga de chunks.
Um comando de outro mod que teleporte diretamente, ou um RTP com destino calculado depois, precisa de adaptador próprio; cadastrar o texto não o integra automaticamente.

O token acompanha a operação específica. Ele não deixa uma janela de acesso para TPA, `/back` ou outro teleporte enquanto o warmup está pendente.
Detalhes e testes: [entrada por comando](integrations/COMMAND_ENTRY.md) e [relatório](integrations/COMMAND_ENTRY_VALIDATION.md).

### Impedir voo, élitros e montaria

```mcfunction
/sg flag loja fly deny
/sg flag loja elytra deny
/sg flag loja pokemon-mount deny
```

São três restrições independentes. Quem chega já nessa atividade tem a entrada recusada
na borda. Se a restrição for ativada onde alguém já está, o servidor encerra a atividade
com proteção temporária contra dano de queda, sem dar invulnerabilidade geral.
O Guard mantém a permissão/capacidade de voo para fora da área. `allow` não concede
voo a quem não tem a capacidade original. O espectador é exceção a `fly`, mas o
criativo precisa respeitar a flag quando não estiver com bypass ativo.

Detalhes, cuidados ao aplicar sobre jogadores no ar e testes:
[voo e montaria](integrations/FLIGHT_AND_MOUNTS.md).

## Prioridades e herança

A resolução funciona por flag, não como uma seleção de uma única região para todas as regras:

1. O Guard consulta as regiões aplicáveis ao ponto e ao jogador.
2. Usa a maior prioridade com valor definido para aquela flag, incluindo os valores herdados.
3. Para estados comuns em empate, `deny` vence.
4. No grupo do jogador, o valor específico precede a regra `@all` da região consultada.
5. `block-break` e `block-place` usam `build` como fallback quando não têm valor próprio aplicável na região.

Exemplo de spawn protegido com uma área de construção liberada:

```mcfunction
/sg flag spawn build deny
/sg setpriority spawn 0
/sg setpriority terreno 10
/sg flag terreno build allow
```

A permissão de construir não libera automaticamente baús, combate, comandos ou entrada.

Para modos de entrada empatados, todas as exigências restritivas precisam ser atendidas; `deny` prevalece.
Uma autorização de raid não abre uma região `command-only`, e um token de comando não abre uma `authorized-only`.
Evite sobrepor esses dois modos na mesma prioridade se a intenção for permitir entrada por apenas um deles.

Uma região `normal` com prioridade maior pode formar uma exceção local. Sair dessa exceção para o restante restrito exige autorização novamente.
Uma lista de entrada nunca concede bypass global.

## Menus, visualização e templates

### `/sg menu`

Abre a interface administrativa do Guard:

```mcfunction
/sg menu
```

Permite navegar por regiões, flags/grupos, pessoas, prioridades, herança, seleção, templates e configurações.
A interface revalida permissões ao executar ações e protege seus ícones contra retirada.

Quando a interface solicitar texto, digite a resposta no chat. A entrada é privada, expira em 60 segundos e pode ser cancelada com `/sg cancel`.
O botão **VOLTAR AO MENU** executa `/menu` com as permissões normais do jogador. Esse comando é de outro sistema e precisa existir no servidor.

### `/sg cancel`

Cancela entradas/ações pendentes da interface e a confirmação pendente de template:

```mcfunction
/sg cancel
```

Não cancela por si só um TPA, teleporte ou raid de outro mod.

### `/sg visualize <região|here|off>`

```mcfunction
/sg visualize loja
/sg visualize here
/sg visualize off
```

- Nome da região: mostra amostras das bordas próximas usando partículas.
- `here`: mostra regiões no local, com limite de até quatro caixas nessa visualização.
- `off`: desativa a visualização escolhida.

Repetir o mesmo alvo alterna a visualização. Não carrega chunks nem desenha todos os blocos de uma caixa enorme.
A seleção pode continuar mostrando partículas próprias mesmo depois de `visualize off`; limpe-a com `/sg sel clear`.
A região global não tem borda desenhável.

### `/sg template list`

Lista os templates disponíveis:

```mcfunction
/sg template list
```

Os modelos iniciais incluem `spawn`, `arena`, `loja`, `evento` e `ginasio`. São opcionais e podem ser personalizados nos arquivos de configuração.
Aplicar o template `loja` não cria automaticamente um warp nem configura uma integração por comando.

### `/sg template apply <região> <template>`

Prepara uma aplicação e mostra o código de confirmação:

```mcfunction
/sg template apply loja loja
```

O primeiro `loja` é a região; o segundo é o template. **Ainda não altera as flags nesse passo.**

### `/sg template confirm <código>`

Use o código que acabou de receber:

```mcfunction
/sg template confirm a1b2c3d4
```

`a1b2c3d4` é apenas um exemplo: copie o código real do seu chat, em até 60 segundos.
A confirmação pertence ao jogador e à dimensão. Alterar a região/template antes de confirmar invalida o plano.
O template mescla apenas suas flags explícitas; preserva geometria, ID, pessoas, prioridade, pai e flags não listadas.
Flags Pokémon são ignoradas na aplicação quando Cobblemon não está instalado.

### `/sg template save <nome> <região>`

Salva as flags locais da região como um novo modelo:

```mcfunction
/sg template save minha_loja loja
```

Exige `region.flag` e `admin`. Não sobrescreve um arquivo de template já existente.
Não copia a construção, os limites ou os jogadores como um modelo de mapa. Não transforma valores apenas herdados em flags locais automaticamente.
O modelo fica em `config/shirokai-guard/templates/`.

## Manutenção e diagnóstico

### `/sg save`

```mcfunction
/sg save
```

Enfileira o salvamento das regiões. A mensagem de enfileiramento não significa que a escrita já terminou: confira `/sg status` e os logs.
Alterações por comandos normalmente já solicitam salvamento; o comando permite solicitá-lo explicitamente.
O arquivo é `config/shirokai-guard/regions/regions.json`, com backup anterior `.bak`.

### `/sg reload`

```mcfunction
/sg reload
```

Valida e recarrega configurações, templates e dados de regiões. Aguarda a fila anterior de salvamentos antes da leitura.
Se os novos dados forem inválidos, preserva o estado válido em memória e informa a falha.
Invalida menus, confirmações e autorizações pendentes do Guard.
Não é um reload de todos os mods nem substitui a reinicialização ao trocar JARs.

### `/sg status`

Mostra quantidade de regiões, métricas de cache e falha de persistência registrada, se houver:

```mcfunction
/sg status
```

### `/sg debug`

Acrescenta ao status a dimensão e as regiões que cobrem a posição da fonte:

```mcfunction
/sg debug
```

Fique no ponto problemático para identificar sobreposições. No console, o resultado considera a posição/contexto da fonte, não a posição de um jogador escolhido automaticamente.

### `/sg bypass`

Alterna seu bypass de sessão do Guard:

```mcfunction
/sg bypass
```

Leia a resposta e confirme se ficou `true` (ativo) ou `false` (inativo).
Não há argumento `on`/`off` específico: o comando alterna o estado.

Pode ser ativado com a permissão `shirokaiguard.bypass` ou sendo OP nível 4. A autorização sozinha não ativa o bypass.
O comando exato `/sg bypass` passa pelas listas `blocked-commands` e `allowed-commands`, permitindo sair de uma região que bloqueia todos os comandos. A exceção não libera outros comandos `/sg`.
O bypass de sessão só continua válido enquanto a autorização continuar válida.
É desativado na desconexão e não ignora proteções independentes do Flan ou de outros mods.
**Para testar uma loja/arena como administrador, deixe o bypass em `false`.**

## Permissões

O prefixo é `shirokaiguard.`. Os níveis de fallback são configuráveis em `permissions.json`; por padrão, os nós abaixo usam OP nível 2.
Negativas explícitas do provedor prevalecem sobre o fallback. Usar `-1` em `fallbackOpLevels` exige permissão explícita.

| Nó completo | Comandos relacionados |
| --- | --- |
| `shirokaiguard.region.create` | `wand`, `pos1`, `pos2`, `sel`, `expand`, `contract`, `define`, `redefine`, `select` |
| `shirokaiguard.region.delete` | `remove` |
| `shirokaiguard.region.flag` | `flag`, `flags`, `entry-command`, `template`; `template save` também exige `admin` |
| `shirokaiguard.region.member` | `addowner`, `removeowner`, `addmember`, `removemember` |
| `shirokaiguard.region.info` | `/sg`, `help`, `info`, `list`, `visualize`, `cancel` |
| `shirokaiguard.admin` | `menu`, `setpriority`, `setparent`, `save`, `reload`, `status`, `debug` |
| `shirokaiguard.bypass` | Alternar e manter o bypass de sessão |

`admin` não é um curinga que ignora negativas de todos os outros nós. Conceda os nós necessários.

Exemplos no **LuckPerms**, se ele estiver instalado:

```mcfunction
/lp user Ana permission set shirokaiguard.region.info true
/lp user Ana permission set shirokaiguard.region.flag true
/lp user Ana permission set shirokaiguard.admin true
```

`/lp` pertence ao LuckPerms. Para um administrador com todas as funções, configure todos os nós pertinentes no usuário ou grupo; os três exemplos acima não concedem automaticamente criação, remoção ou bypass.

## Receitas prontas

### Spawn sem construção e sem PvP

Depois de criar `spawn`:

```mcfunction
/sg flag spawn build deny
/sg flag spawn pvp deny
/sg flag spawn tnt deny
/sg flag spawn creeper-explosion deny
/sg flag spawn other-explosion deny
/sg flag spawn greeting Bem-vindo ao spawn!
```

Acrescente `interact`, `use` e `chest-access` apenas conforme o que deseja permitir no spawn; não são implicitamente negados por `build`.

### Loja com entrada por warp, permitindo compras/interações

```mcfunction
/sg flag loja build deny
/sg flag loja pvp deny
/sg flag loja entry-mode command-only
/sg entry-command loja add warp loja
```

Não aplique `interact deny`/`use deny` indiscriminadamente se o sistema de compras precisa desses eventos.
Teste a interação real do mod de loja. Este exemplo não cadastra produtos nem cria um mod de comércio.

### Arena acessível pela raid

```mcfunction
/sg flag raid_arena entry unset
/sg flag raid_arena exit unset
/sg flag raid_arena teleport unset
/sg flag raid_arena entry-mode authorized-only
/sg flag raid_arena build deny
```

Não copie flags de captura, batalha ou dano de outro exemplo sem avaliar as necessidades da raid.
Confira o destino de entrada no Raids e a proteção de toda a altura da arena.

### Permitir construção aos membros de uma área

```mcfunction
/sg addmember terreno Bruno
/sg flag terreno build deny
/sg flag terreno build allow @members
/sg flag terreno build allow @owners
```

Se não funcionar, confira flags mais específicas (`block-break`/`block-place`), prioridades, pais e outros mods.

## Problemas frequentes

| Sintoma | O que conferir |
| --- | --- |
| Aviso de entrada no login ou após `/spawn`, mas acesso funciona | Atualize para Guard 0.5.1-alpha.1; [correção de sincronização](integrations/TELEPORT_SYNC.md). Não remova a região para silenciar o texto. |
| “Região inexistente nesta dimensão” | ID e dimensão atual; use `/sg list`. |
| “Seleção ausente” ou “Defina pos1 e pos2” | Faça os dois cantos na dimensão correta, ou use `/sg select <região>`. |
| Expandiu, mas a região não mudou | Expansão muda a seleção; finalize com `/sg redefine <região>`. |
| Owner não consegue construir | Owner não tem bypass automático; configure `@owners` e confira as flags específicas. |
| `unset` não liberou | Pode haver regra `@all`, herança, fallback, região sobreposta ou recusa de outro mod. |
| `/raid entrar` foi bloqueado | Raids atualizado, destino dentro da arena, `entry-mode authorized-only` e ausência de negativas conflitantes. |
| `/warp loja` foi bloqueado | Guard/Essentials atualizados, nome exato em `entry-command list`, warp existente, permissões, segurança do destino e outras flags. |
| Outro warp entra quando você já está dentro | Movimento interno na mesma região não é uma nova entrada. Teste começando fora. |
| TPA entra durante o teste administrativo | Confira se o jogador que está sendo teleportado tem bypass ativo e se o destino está de fato na região correta. |
| Jogador ficou sem saída | Confira `exit`, `teleport`, pais e outras regiões; os modos novos não criam autorização para superar essas negativas. |
| Partículas não mostram toda a área | A visualização é amostrada por proximidade e orçamento; veja também a seleção e `selectionParticles`. |
| Dados não foram salvos | Consulte `/sg status` e logs; mensagem de fila não é confirmação de escrita concluída. |
| Comando de outro mod não entra com `command-only` | Cadastrar o texto não cria integração; use o fluxo compatível do Essentials ou implemente adaptador da API. |

Referências: [README](../README.md), [arquitetura](ARCHITECTURE.md), [integrações](INTEGRATIONS.md), [acesso a arenas](integrations/ARENA_ACCESS.md) e [entrada por comando exato](integrations/COMMAND_ENTRY.md).


### Claims do Flan em regiões Guard

Com Flan 1.21.1-1.12.8-fabric instalado, criar ou redimensionar um claim de jogador é negado quando o volume final sobrepõe qualquer região Guard na mesma dimensão. Não é necessário configurar uma flag. Uma seleção iniciada fora também é conferida; um `__global__` impede claims em toda a dimensão.

A enxada de ouro não é proibida. Fora das regiões, o Flan continua aplicando suas próprias regras. Claims existentes não são apagados; esta integração protege as operações `createClaim` e `resizeClaim` de jogadores, sem alterar carregamento de claims, criação administrativa por API ou subclaims de claims antigos.

Administradores podem ativar `/sg bypass` para permitir essas operações no Guard; as regras independentes do Flan continuam valendo. A integração opcional não exige instalar Flan para iniciar o servidor.

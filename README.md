# ✦ Shirokai Guard

Mod **server-side** de proteção de regiões para **Minecraft Java 1.21.1 / Fabric / Java 21**. Implementação própria, sem Bukkit, Paper ou WorldEdit. O Guard inicia sem Cobblemon, Flan ou outros mods Shirokai.

**Estado: 0.2.0-alpha.1.** Proteções, menus administrativos, templates opcionais, visualização e configuração implementados. O conjunto com Flan, Cobblemon, LuckPerms e os módulos Shirokai passou nos cenários de integração documentados. Versão experimental; veja os resultados e limites em [VALIDATION.md](docs/VALIDATION.md).

[Baixar JAR 0.2.0-alpha.1](https://github.com/istwi/Shirokai-Guard/raw/refs/tags/v0.2.0-alpha.1/releases/v0.2.0-alpha.1/shirokai-guard-0.2.0-alpha.1.jar) · [Pré-release](https://github.com/istwi/Shirokai-Guard/releases/tag/v0.2.0-alpha.1)

## Instalação

1. Use Java 21, Fabric Loader **0.17.2+** e Fabric API **0.116.18+1.21.1**.
2. Coloque somente `shirokai-guard-0.2.0-alpha.1.jar` em `mods/` do servidor.
3. Não instale `sources.jar`, o ZIP do projeto ou o mod de GameTests no servidor.
4. LuckPerms é opcional. A Fabric Permissions API 0.3.1 está embutida; negativas explícitas são respeitadas. Sem provedor, os comandos usam nível de operador 2.
5. Para as flags Pokémon, instale **Cobblemon 1.8.3+1.21.1** com suas dependências. Outros módulos Shirokai são opcionais.

Clientes não precisam instalar o Guard. Faça backup do mundo e dos dados antes de experimentar esta versão em uma cópia do servidor.

## Primeira região

```mcfunction
/sg wand
/sg pos1 0 -64 0
/sg pos2 100 319 100
/sg define spawn
/sg flag spawn build deny
/sg flag spawn build allow @owners
/sg flag spawn chest-access deny @nonmembers
/sg flag spawn pvp deny
/sg flag spawn greeting ✦ Bem-vindo ao spawn!
/sg info spawn
```

O machado de madeira define pos1 com clique esquerdo e pos2 com direito depois de `/sg wand`. `/sg sel clear` limpa a seleção e desativa a ferramenta. Posições e regiões pertencem à dimensão atual; trocar de dimensão não mistura seleções. Sem coordenadas, `pos1`/`pos2` usam a posição do jogador. Partículas mostram as bordas próximas da seleção com orçamento limitado a cada segundo.

**Criar uma região não proíbe automaticamente ações.** Flags `unset` permitem ações quando não há configuração herdada. Configure `build deny` para proteger construção. Owners não recebem bypass automático; configure explicitamente os grupos desejados.

## Comandos

| Finalidade | Comandos após `/sg` |
| --- | --- |
| Seleção | `wand`, `pos1 [x y z]`, `pos2 [x y z]`, `sel clear`, `expand <n> <north/south/east/west/up/down>`, `contract <n> <direção>`, `expand vert` |
| Regiões | `define <id>`, `redefine <id>`, `remove <id>`, `info <id>`, `list`, `select <id>` |
| Prioridade/herança | `setpriority <id> <inteiro>`, `setparent <id> <pai ou none>` |
| Pessoas | `addowner`, `removeowner`, `addmember`, `removemember`, seguidos de `<id> <jogador online ou UUID>` |
| Flags | `flag <id> <flag> <valor> [@all/@owners/@members/@nonmembers]`, `flags <id>` |
| Interface | `menu`, `cancel`, `visualize <id/here/off>` |
| Templates | `template list`, `template apply <região> <template>`, `template confirm <código>`, `template save <nome> <região>` |
| Administração | `reload`, `save`, `status`, `debug`, `bypass`, `help` |

IDs aceitam `a-z`, `0-9`, `_` e `-`, até 64 caracteres. `define __global__` cria a região global da dimensão atual sem seleção. Grupos específicos precedem `all`; owners, members e nonmembers são distintos. UUIDs funcionam para jogadores offline.

Resolução: maior prioridade com valor definido; em empate, `deny` vence. Herança não pode formar ciclos. `unset` remove a flag local do grupo e reativa herança/fallback. `block-break` e `block-place` usam `build` como fallback. Veja [arquitetura](docs/ARCHITECTURE.md) para os detalhes.

## Flags implementadas

- Construção/interação: `build`, `block-break`, `block-place`, `interact`, `use`, `chest-access`, `item-frame`, `armor-stand`, `piston`, `hopper`, `farmland-trample`.
- Entidades/combate: `pvp`, `mob-damage`, `mob-spawning`, `entity-damage`, `entity-interact`, `entity-spawning`, `projectile`, `tnt`, `creeper-explosion`, `other-explosion`.
- Ambiente: `fire-spread`, `lava-flow`, `water-flow`, `leaf-decay`, `ice-form`, `ice-melt`, `snow-melt`, `crop-growth`.
- Jogadores: `entry`, `exit`, `greeting`, `farewell`, `invincible`, `fall-damage`, `hunger`, `item-drop`, `item-pickup`, `enderpearl`, `chorus-fruit`, `teleport`.
- Comandos: `blocked-commands`, `allowed-commands`.
- Com Cobblemon instalado: `pokemon-spawn`, `pokemon-capture`, `pokemon-battle`, `pokemon-interact`, `pokemon-damage`, `pokemon-pasture`.

Estados usam `allow`, `deny` ou `unset`. `invincible allow` ativa invulnerabilidade. `hunger deny` congela fome e o processamento de regeneração do HungerManager. `greeting` e `farewell` aceitam texto literal; as listas de comandos usam raízes separadas por vírgula, por exemplo `kill,op`. `allowed-commands` restringe comandos às raízes listadas; uma entrada bloqueada ainda vence em empate. Raízes com namespace não escapam de bloqueios no nome simples. Aliases de outros mods precisam ser listados explicitamente.

`pokemon-breeding` não é oferecida: não foi identificado um contrato de criação/reprodução para implementar essa flag no Cobblemon 1.8.3 original. Não são criados eventos fictícios.

## Permissões

`shirokaiguard.admin`, `shirokaiguard.region.create`, `.region.delete`, `.region.flag`, `.region.member`, `.region.info`, `shirokaiguard.bypass`.

`admin` controla prioridade, herança e manutenção. As outras operações consultam seus nós específicos, sem um fallback geral que ignore negativas. `/sg bypass` é uma opção de sessão, registrada em log, desativada na desconexão e revalidada em cada ação. A permissão sozinha não ativa bypass. Nenhum mod recebe bypass automático.

## Dados e configuração

```text
config/shirokai-guard/
  config.json                  # partículas, orçamento, auditoria, limites e cache
  menus.json                   # título, tamanho das páginas e lore
  permissions.json             # níveis OP de fallback por nó
  flags.json                   # defaults aplicados a NOVAS regiões
  templates/*.json             # modelos opcionais de flags
  messages.json                # mensagens básicas e placeholders %s
  integrations.json            # exceção restrita de interação com Crates
  regions/regions.json         # snapshot versionado
  regions/regions.json.bak     # snapshot anterior
```

`reload` valida tudo antes de trocar o estado. Dados inválidos preservam os arquivos; na inicialização, impedem a abertura desprotegida. `save` enfileira um snapshot; confirme falhas em `status` e no log. A gravação usa arquivo temporário, fsync, backup e rename atômico; não há I/O de persistência nos eventos de proteção. Para restaurar, pare o servidor e recupere manualmente uma cópia validada do backup.

`config.json`: `selectionParticles`, `auditChanges`, `maxRegionsPerDimension` (1..100000), `particleBudget` (24..512) e `queryCacheEntries` (0..65536, zero desativa). `menus.json`: `title`, `pageSize` (9..45) e `lore`. `permissions.json`: `fallbackOpLevels`, com -1 exigindo permissão explícita e 0..4 indicando o nível OP; negativas explícitas do provedor sempre vencem. `flags.json` usa `schema: 1` e `defaults`, no formato `{"build":{"all":"deny"}}`. Defaults não modificam regiões existentes. Arquivos antigos recebem valores padrão para campos novos; personalizações existentes não são substituídas.

## Menus e templates

Abra `/sg menu` como administrador: regiões paginadas, flags por grupo, owners/members, prioridade, herança, seleção/redefinição, exclusão confirmada, templates e configurações. Ícones não podem ser retirados, descartados ou transferidos. Entradas solicitadas no chat são privadas, expiram em 60 segundos e podem ser canceladas com `/sg cancel`. Permissões e dimensão são verificadas novamente ao executar. A seta **VOLTAR AO MENU** executa `/menu` com as permissões normais do jogador; o comando precisa existir no servidor.

Templates são **opcionais**: `spawn`, `arena`, `loja`, `evento` e `ginasio` vêm prontos. `/sg template apply spawn spawn` apenas prepara a confirmação; use o código exibido em até 60 segundos ou confirme pela interface. Alterações posteriores na região/template invalidam a confirmação. Aplicar mescla somente flags listadas, preservando limites, prioridade, pessoas e flags não listadas. Flags Cobblemon são ignoradas quando o mod está ausente. `/sg template save meu_modelo spawn` salva as flags locais, sem sobrescrever modelos existentes.

`/sg visualize here` mostra regiões no local; `/sg visualize spawn` escolhe uma região e `off` desliga. Bordas são amostradas perto do jogador, sem carregar chunks ou percorrer o volume da região. O cache guarda somente consultas espaciais; decisões por UUID/grupo/permissão continuam sendo avaliadas por ação.

## Desenvolvimento e validação

```bash
./gradlew test runGametest build
./gradlew -PwithCobblemon runGametest
./gradlew -PloadTest runGametest
./gradlew projectArchive
```

O JDK 21 é necessário, não apenas o JRE. Gradle 8.12 está fixado pelo wrapper com SHA-256. O plugin Kotlin 2.2.20 habilita o remapeamento dos metadados do Cobblemon; não torna Kotlin uma dependência do Guard no servidor. O lançamento de testes Cobblemon acrescenta uma cópia inalterada do ICU usado pelo motor Graal, pois Loom filtra essa biblioteca das dependências vanilla do servidor.

JUnit cobre regras e persistência. GameTests executam o Minecraft real e os hooks de proteção. Os testes Cobblemon verificam os eventos/API reais, mas não substituem uma sessão multiplayer de captura e batalha completa. Resultados e limitações: [VALIDATION.md](docs/VALIDATION.md). A CI executa as duas variantes e prepara artefatos, sem publicar releases.

Para repetir a validação com o Flan real, use o wrapper separado:

```bash
(cd validation/flan && ./gradlew runGametest)
(cd validation/flan && ./gradlew -PwithCobblemon runGametest)
```

Detalhes dos oito cenários, versões e limitações: [validação Flan](validation/flan/README.md).

## Limites conhecidos

- Mod experimental; os testes não equivalem a uma campanha de produção ou uma raid completa jogada.
- Protege os caminhos vanilla e Cobblemon documentados; não impede um mod de escrever diretamente no mundo fora desses contratos.
- Login/respawn em local proibido procuram uma posição permitida e segura em colunas já carregadas num raio de 16 blocos; sem destino seguro o jogador é desconectado. Movimento do servidor e veículos com jogador também verificam fronteiras.
- `item-drop` protege o descarte voluntário pela interface; não reescreve drops de morte ou entregas de comandos/mods.
- Flags ambientais cobrem os mecanismos indicados, não toda mutação possível de blocos por datapacks/mods.
- O perfil de carga usa 100 jogadores internos ao servidor, sem 100 conexões remotas. Não comprova capacidade para 100 clientes reais.

Consulte a [inspeção das integrações](docs/INTEGRATIONS.md) antes de instalar junto com os outros mods.

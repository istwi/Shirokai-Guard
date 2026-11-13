# Integrações: inspeção e limites

Nenhuma integração concede bypass geral, altera claims ou escreve em configurações dos outros mods. O conjunto completo abaixo foi carregado simultaneamente e passou em 14 GameTests, incluindo os oito cenários base do Flan.

## Cobblemon 1.8.3+1.21.1

Foram consultados os artefatos oficiais `com.cobblemon:fabric` e `com.cobblemon:mod`, fontes e assinaturas compiladas no repositório `https://maven.impactdev.net/repository/development`. O POM identifica o commit `ffe005511dd0d3ba68377fa3f95906b3837d9a0e`. O JAR original foi conferido contra o checksum publicado; nenhuma classe do Cobblemon foi modificada para passar testes.

| Flag | Mecanismo |
| --- | --- |
| pokemon-spawn | `ServerWorld.addEntity`, antes de registrar a entidade Pokémon; também cobre envios ao mundo, não apenas spawn natural |
| pokemon-capture | `CobblemonEvents.THROWN_POKEBALL_HIT`, cancelável antes de começar a captura |
| pokemon-battle | `CobblemonEvents.BATTLE_STARTED_PRE`, consulta participantes e entidades presentes antes do início |
| pokemon-interact | callback Fabric de interação e handler de pacote da roda de interação |
| pokemon-damage | proteção de dano de entidades vivas aplicada à entidade Pokémon |
| pokemon-pasture | interação com o bloco, método `tether` e handlers de adicionar/retirar Pokémon do Pasture |

Não se interceptam HP de batalhas, recompensas, prazos ou lógica do simulador. Cancelamentos anteriores são preservados. `pokemon-breeding` não é registrada: não foi identificado um contrato correspondente no mod original.

Os sete GameTests específicos usam entidades, eventos e o bloco reais do Cobblemon. Verificam rejeição de spawn, captura cancelada, cancelamento anterior preservado, batalha rejeitada antes do início, verificação de interação, dano com controle positivo e rejeição de tether. Não simulam clientes, todos os pacotes, uma captura completa ou uma batalha jogada até o fim.

## Flan

Código inspecionado: [Flemmli97/Flan](https://github.com/Flemmli97/Flan/tree/6625bcd5a8a0c9aab712ccb41695cc9c92a62475), branch `1.21`, versão declarada 1.12.8.

`FlanFabric.applyPriorityListener` registra proteção na fase `flan:events` antes da fase padrão. O Guard registra sua fase depois de `flan:events` e antes da padrão. Os callbacks retornam `PASS` quando permitem; não retornam `SUCCESS` para forçar uma permissão. Quebra e dano usam eventos booleanos que preservam recusas. Não há chamadas para modificar dados de claims nem APIs que liberem ações no Flan.

**Validado com o Flan real 1.21.1-1.12.8-fabric.** O acesso ao Modrinth foi liberado e o JAR original foi conferido contra SHA-512 publicado. Oito GameTests passaram com Guard + Flan e os mesmos oito com Guard + Flan + Cobblemon 1.8.3: quebra negada nos dois sentidos, bypass do Guard preservando a recusa Flan, colocação negada, baús negados nos dois sentidos e controles positivos de quebra e abertura. O JSON do claim também foi comparado antes e depois de operações do Guard.

O Flan pode retornar `PASS` no callback de uso de bloco e ainda bloquear a abertura por flags internas de uso de bloco/item. O teste verifica a abertura efetiva pelo ScreenHandler; `PASS` não foi interpretado como uma permissão. O adaptador `FlanCompat` consulta, somente para a exceção de crates, `ItemUseBlockFlags.flan$allowUseBlocks()` no interaction manager. Uma recusa interna interrompe a interação antes de o Crates abrir sua própria tela. Falha no contrato opcional desativa essa exceção com log.

O JAR instalável do Guard foi carregado em um projeto de teste separado, pois o Flan atual foi compilado com Loom 1.14.10. O projeto principal mantém seu próprio Loom; o adaptador de leitura acima foi acrescentado na versão 0.2. [Procedimento, resultados e limites](../validation/flan/README.md).

## Shirokai Essentials

Código: [a2f4078](https://github.com/istwi/Shirokai-Essentials/tree/a2f4078dc9b39ad55ca39ebee54829bc8160c810).

README, `TeleportService`, comandos e hooks de interação/moderação foram inspecionados. Homes, warps, TPA, RTP e back chegam a `ServerPlayerEntity.teleport`. O hook central do Guard impede o deslocamento proibido sem adaptador. O Essentials registra histórico/cooldown antes de chamar esse método void; portanto pode informar sucesso ou manter cooldown mesmo que o Guard cancele. Não se sobrescreveu seu predicado de moderação para resolver essa diferença. Um contrato de pré-teleporte no Essentials seria necessário para sincronizar esses efeitos com precisão.

O Essentials 0.2.2 foi carregado no teste conjunto: o serviço real `systemTeleport` teve destino proibido bloqueado e controle positivo permitido. Isso não verifica todos os comandos/cooldowns.

## Shirokai Spawn

Código: [ea913c2](https://github.com/istwi/Shirokai-Spawn/tree/ea913c2d0cb6bada6ea92b0247acf62a281d9576).

README, configuração de destinos e método de teleporte foram inspecionados. Teleporte inicial e `/explorar` usam `ServerPlayerEntity.teleport` e conferem a posição resultante. Não há exceção automática para `entry deny`, inclusive em dimensões personalizadas. O Guard não altera o carregamento de chunks realizado pelo Spawn.

Sem adaptador próprio. O Spawn 1.1.0 foi carregado; `/explorar` real foi testado com destino negado e permitido. Para produzir o JAR de validação a partir do commit acima, foi usado um init script externo que seleciona Loom 1.9.2 no lugar de 1.8.13; a combinação original com Gradle 8.12 gerava um JAR remapeado vazio. Os fontes do Spawn não foram alterados. Não equivale a validar um binário publicado diferente.

## Shirokai Raids

Código: [e3f7a95](https://github.com/istwi/Shirokai-Raids/tree/e3f7a959144bbe360d39e8522fabe34014caafc4).

README, `ShirokaiRaids`, `RaidCapture`, eventos de captura/batalha e chamadas de teleporte foram inspecionados. Os eventos Cobblemon e métodos vanilla já permitem recusar ações. Nenhum hook altera HP, dano de movimentos, recompensas ou duração de eventos. Arenas precisam de flags que permitam as ações pretendidas; não há uma liberação automática por um boss ser do Raids.

Sem adaptador próprio. Raids 0.1.18-dev foi carregado com seu comando registrado; a assinatura de teleporte utilizada pelo mod teve retorno falso quando proibida e sucesso após remover a restrição. Uma raid completa, HP, captura e recompensas não foram jogados nesta validação.

## Shirokai Crates

Código: [05b3995](https://github.com/istwi/Shirokai-Crates/tree/05b3995c4d4466ef17c6171ad07d4f0eab512db9), versão 0.2.1.

README, `CrateService`, callbacks e Mixins de proteção foram inspecionados. O Crates registra interação/prévia depois da fase padrão. Uma recusa `interact deny`/`block-break deny` do Guard impediria a abertura/prévia antes de o Crates verificar chaves e autorização. Essa necessidade justifica o único adaptador de outro mod Shirokai nesta versão.

O adaptador consulta a API pública, somente leitura, `ShirokaiCrates.protectedBlock(World, BlockPos)`. Para um bloco registrado, o Guard retorna `PASS` na interação ou tentativa inicial de quebra, permitindo que o Crates autorize a chave ou a prévia. A quebra efetiva continua bloqueada. Explosões e pistões também consultam o registro. A exceção não se estende a blocos vizinhos e não anula Flan ou outros callbacks.

`integrations.json` permite desativar a exceção por `crateInteractions: false`. Se a API não existir ou falhar, a exceção é desativada e o erro é registrado uma vez. O Guard não acessa diretamente dados, chaves, economia ou transações do Crates.

A suíte base mantém uma fixture do contrato. A suíte conjunta também carregou Crates 0.2.1 real, Impactor 5.3.5+1.21.1 e LuckPerms 5.4.140: registrou bloco e chave reais, aguardou os checkpoints de persistência e liberação do estado ocupado no servidor, verificou interface fechada e chave preservada sob recusa Flan e abertura após autorização. Não simula toda a roleta/economia de produção.

## LuckPerms e conjunto real

A negativa explícita `shirokaiguard.region.flag=false` foi testada com LuckPerms Fabric 5.4.140 e jogador OP nível 4: prevalece sobre o fallback OP. Jogadores de conexões internas carregam os dados pelo `UserManager` antes do login, reproduzindo a preparação normal do provedor. Todos os nove mods (Guard, Flan, Cobblemon, LuckPerms, Impactor, Essentials, Spawn, Raids e Crates) foram confirmados carregados; serviços e comandos esperados foram verificados. Nenhum JAR de terceiro é incorporado à distribuição do Guard.

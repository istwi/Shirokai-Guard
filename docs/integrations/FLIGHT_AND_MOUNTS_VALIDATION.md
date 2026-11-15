# Validação — voo e montarias, Guard 0.5.0-alpha.1

Executada em 15/11/2026 sobre Guard `022a4b9`, com JDK 21, Minecraft 1.21.1,
Fabric Loader 0.17.2 e os wrappers fixados. Nenhum fonte de Essentials, Raids,
Cobblemon ou Flan foi alterado nesta atualização.

| Verificação | Resultado |
| --- | --- |
| Testes unitários Guard | 58 aprovados, 0 falhas/erros/ignorados |
| GameTests sem Cobblemon/Raids/Essentials | 58 aprovados |
| GameTests com Cobblemon 1.8.3, Raids 0.1.20-guard.1 e Essentials 0.3.2-guard.1 | 72 aprovados |
| JAR Guard com Flan 1.12.8 real | 8 aprovados |
| Compilação e geração do JAR | concluídas |

Relatórios XML: [`../reports/mobility`](../reports/mobility).

## O que os testes novos verificam

- Resolução das três flags com grupos, pais, prioridades, dimensões e persistência.
- Travessia de uma região estreita, mesmo com destino final fora, e saída permitida.
- Pacote real de ativação do voo recusado dentro e aceito no controle positivo fora,
  mantendo `allowFlying` intacto.
- Teleporte e movimento de quem já está voando recusados, sem desligar o voo na borda.
- Élitros equipados, verificação nativa de início, início direto e teleporte já planando.
- Independência de `fly` e `elytra`; interrupção de um glide já proibido no local.
- Espectador e bypass explícito de sessão; revogação do bypass voltando a aplicar a regra.
- Encerramento de voo já ativo e proteção somente de dano de queda. Dano genérico
  continua funcionando; a proteção expira após o chão e dano de queda volta a funcionar.
- Evento real `RIDE_EVENT_PRE`, montagem nativa forçada recusada e controle positivo
  permitido com Arcanine, Lapras e Charizard, representando configurações LAND/LIQUID/AIR.
- Barco comum não bloqueado por `pokemon-mount`.
- Movimento físico de Charizard com passageiro barrado sem desmontá-lo na borda.
- Montaria existente encerrada quando a região passa a proibi-la, com proteção de queda.
- Método **real** `PokemonEntity.tryRidingPokemon` com Pokémon do jogador e condições
  nativas de montaria válidas: recusa na região e montagem permitida no controle positivo.
- As suítes anteriores de proteção, acesso por comando, warmup real/TPA e API Raids
  continuam incluídas. Os testes Flan preservam recusas independentes e controles positivos.

O teste de dano aguarda o término dos 60 ticks de invulnerabilidade de login do
Minecraft antes dos controles. A primeira tentativa antecipava esse prazo e teve
um controle positivo recusado pela proteção vanilla; o teste foi corrigido para
aguardar, sem desativar proteções de produção.

O perfil de GameTests agora declara a lista efetiva de entrypoints como input do
Gradle. Isso impede reutilizar um manifesto de testes antigo ao adicionar classes
novas; os XML finais confirmam as contagens e a execução dos casos novos.

## Comandos executados

```bash
# Raiz do Guard, JDK 21
./gradlew --no-daemon test runGametest build
./gradlew --no-daemon -PwithCobblemon -PwithArenaIntegrations \
  -ParenaIntegrationMods=/workspace/command-integration-mods test runGametest build
# Em validation/flan, wrapper próprio
./gradlew --no-daemon runGametest
```

O diretório de integração contém os JARs já validados de Raids 0.1.20-guard.1 e
Essentials 0.3.2-guard.1. Nesta máquina, os comandos usaram
`JAVA_HOME=/workspace/toolchains/jdk-21.0.13+9` e
`GRADLE_USER_HOME=/workspace/gradle-home`. O artefato de Flan coincide com a cópia
oficial já conferida por SHA-512; bibliotecas e JARs de terceiros não foram modificados.

## Limites

Não foi jogada uma sessão multiplayer de voo ou uma raid completa. Os testes
exercitam entidades, pacotes, eventos e métodos reais no servidor de GameTests;
não verificam todas as animações ou controles dos clientes. As três espécies foram
montadas pelos métodos testados, sem simular uma jornada real em terra, água e ar.
Dano e pouso são exercitados nas entidades de teste, sem uma campanha de quedas reais
em todos os terrenos. A proteção temporária não cobre lava, fogo, afogamento ou vazio.

Não há promessa de anticheat universal para mods que alterem posições/estados fora
dos hooks implementados. A variante Flan + Cobblemon, LuckPerms real e a suíte
específica de carga com 100 jogadores não foram repetidas nesta rodada.

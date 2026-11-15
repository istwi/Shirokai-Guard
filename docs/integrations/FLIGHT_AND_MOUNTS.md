# Voo, élitros e montaria — Guard 0.5.0-alpha.1

Atualize **somente o Guard** para 0.5.0-alpha.1 e reinicie o servidor.
Não é necessário atualizar novamente Raids ou Essentials. A integração de montaria
usa o Cobblemon **1.8.3+1.21.1** já suportado; sem Cobblemon, `fly` e `elytra`
continuam disponíveis e `pokemon-mount` não é oferecida pelo comando de flags.

## Configurar

Depois de criar uma região que cubra a área e a altura desejadas:

```mcfunction
/sg flag loja fly deny
/sg flag loja elytra deny
/sg flag loja pokemon-mount deny
```

| Flag | O que recusa | O que não altera |
| --- | --- | --- |
| `fly` | Ativação do voo por habilidades do jogador e entrada já nesse modo, incluindo criativo e voo concedido por `/fly` | Permissão de `/fly`, capacidade de voar fora da área, élitros e montaria Pokémon |
| `elytra` | Início do voo com élitros e entrada já planando | Voo de habilidades e montarias |
| `pokemon-mount` | Subir em Pokémon e entrar na região já montado, incluindo espécies terrestres, aquáticas e aéreas | Barcos, cavalos comuns, Pokémon andando/voando sozinhos e Pokémon no ombro do jogador |

As flags são independentes. Para impedir as três atividades, configure as três.
Não bloqueiam automaticamente saltos, quedas, efeitos de levitação ou todo sistema
de locomoção implementado por qualquer mod arbitrário.

`allow` remove a negativa do Guard naquela resolução, mas não concede permissão
para `/fly`, não entrega élitros nem supera condições nativas de propriedade,
assento, espécie ou acesso à montaria do Cobblemon.

## Grupos, prioridades e herança

Usam as mesmas regras das outras flags: valores `allow`, `deny`, `unset`, grupos,
maior prioridade com valor definido e negativa em empate. Exemplo de exceção:

```mcfunction
/sg flag loja fly deny
/sg flag loja fly allow @owners
/sg flag loja pokemon-mount deny
/sg flag loja pokemon-mount allow @members
```

Owners e members são grupos distintos. Se quiser liberar os dois, configure os dois.
`unset` remove o valor local; uma negativa herdada ou de outra região ainda pode valer:

```mcfunction
/sg flag loja fly unset
/sg flag loja elytra unset
/sg flag loja pokemon-mount unset
```

O bypass continua exigindo `shirokaiguard.bypass` e sessão `/sg bypass` ativa.
O modo espectador é exceção **apenas à restrição `fly`**, para preservar seu modo
nativo de movimento; isso não concede bypass das demais flags. O criativo continua
sujeito a `fly deny` quando não há bypass ativo.

## Entrar já voando, planando ou montado

A travessia para o destino restrito é recusada, mantendo o jogador do lado permitido.
A recusa da borda não corta o voo nem desmonta o jogador no ar. A checagem cobre
teleportes, movimento do jogador, pacotes de veículos e movimentos físicos do
servidor já interceptados pelo Guard. Movimentos curtos que atravessam uma região
estreita também verificam as fronteiras, mesmo se terminarem fora dela.

Para entrar, pouse ou desmonte antes. Isso também vale para `/warp loja` ou
`/raid entrar`: a autorização de entrada desses comandos não supera as flags novas.
O pré-teleporte existente do Essentials continua recusando antes do histórico e
cooldown quando o Guard nega o destino.

Uma região limitada somente ao chão pode não cobrir a altura de voo/assento.
Confira seus limites com `/sg info`, `/sg visualize` e, se apropriado, use
`/sg select <região>`, `/sg expand vert`, `/sg redefine <região>`.

## Ativar a negativa onde alguém já está

Se alguém já estiver na região quando a flag for aplicada/recarregada, ou aparecer
nela em login/respawn, o Guard encerra a atividade proibida na verificação do servidor:

- Voo de habilidades: define `flying=false`, mantendo `allowFlying` e as permissões.
- Élitros: encerra o estado de planar.
- Pokémon: desmonta o jogador, sem retirar o Pokémon, alterar sua propriedade,
  stamina, batalha ou dados de participação.

Ao interromper uma atividade já ativa, há proteção temporária **somente contra dano
de queda**, até o primeiro pouso e uma pequena folga de ticks no chão. Ela termina
mais cedo ao retomar voo/élitros permitidos, montar novamente, morrer ou desconectar.
Não é invulnerabilidade geral: lava, fogo, afogamento, vazio e outros danos não são
cancelados por essa proteção. Não há teleporte automático para um local seguro.
Evite ativar novas restrições sobre jogadores suspensos em locais perigosos.

O Guard não reativa o voo automaticamente na saída. A capacidade/permissão original
continua disponível para ativação normal fora da área; no voo por habilidades,
por exemplo, o jogador pode usar novamente o duplo espaço quando tiver essa capacidade.
As flags novas não bloqueiam sair caminhando da região; outras flags como `exit`
continuam independentes.

## Integração real e limites técnicos

A montagem normal usa `CobblemonEvents.RIDE_EVENT_PRE`, evento cancelável de
`tryRidingPokemon`. Um hook adicional no `Entity.startRiding(Entity, boolean)`
impede montagem direta/forçada em Pokémon que não passe por esse evento.
Cancelar nunca concede uma permissão que o Cobblemon ou outro mod já negou.

O voo por habilidades verifica o pacote `UpdatePlayerAbilitiesC2SPacket` no servidor,
depois da troca para o thread correto. Élitros verificam `checkFallFlying` e
`startFallFlying`. Uma verificação a cada tick trata atividades já em andamento.
Não é um sistema anticheat universal contra mods que escrevam coordenadas/estados
por fora dos caminhos interceptados.

Mensagens novas em `messages.json`: `fly-denied`, `elytra-denied` e
`pokemon-mount-denied`. Configurações antigas recebem os defaults em memória sem
substituir personalizações existentes. Os avisos usam o limite de frequência do Guard.

## Conferir no servidor

Com bypass desativado:

1. Fora da região, ative `/fly` por um provedor que conceda voo e tente atravessar a borda.
2. Pouse: entrar andando deve continuar permitido, salvo outra regra de entrada.
3. Dentro da região, tente iniciar voo de habilidades ou de élitros.
4. Tente montar um Pokémon dentro; depois monte fora e tente cruzar montado.
5. Confira que barco/cavalo comum não foi proibido por `pokemon-mount`.
6. Teste novamente fora da região e com a exceção de grupo desejada.

Resultados automatizados: [FLIGHT_AND_MOUNTS_VALIDATION.md](FLIGHT_AND_MOUNTS_VALIDATION.md).

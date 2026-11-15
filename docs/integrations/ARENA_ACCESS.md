# Entrada autorizada por região — 0.3.0-alpha.1

Base: Guard `dcf01a6aa39ac012d689bed871a2ad2d0a61d8eb` (main consultada no Git).
Integrações inspecionadas e alteradas: Raids `1a5e9ab1f0f0372750ff8d7ae4bfdde03da0a92e`
e Essentials `8b930e6016f46f3b3cf3cc9171ffc35ab39ca978`.

## Configurar

```
/sg flag raid_arena entry-mode authorized-only
/sg flag evento_privado entry-mode authorized-only
/sg flag spawn entry-mode normal
/sg flag raid_arena entry-mode unset
```

Sem configuração, o comportamento anterior continua. Não há lista global de comandos
bloqueados: a proteção examina o destino. `/back`, `/home`, `/tpa`, `/warp` e `/tp`
continuam funcionando para destinos comuns. `normal` remove apenas a exigência desta
flag; `deny` recusa entrada mesmo com autorização; `unset` recupera herança/fallback.
A interface permite editar os três valores, inclusive por grupo.

Maior prioridade com `entry-mode` definido vence. Em empate, `deny` vence;
`authorized-only` exige autorização para **todas** as regiões empatadas que estão
sendo adentradas, mesmo que outra região do empate use `normal`. Herança usa a região
filha de destino como identidade da autorização. `normal` em prioridade superior
permite uma exceção local explícita. Dimensões e grupos são independentes.

A autorização não supera `entry deny`, `exit deny`, `teleport deny`, outras flags,
permissões ou recusas de outros mods. Bypass administrativo continua exigindo a
permissão `shirokaiguard.bypass` **e** a sessão `/sg bypass` ativa. Não é automático
para OP, owners, Raids ou Essentials.

Ocupantes podem mover-se dentro da mesma região e sair; esta flag não cria bloqueio
de saída. Uma flag `exit deny` já existente ainda pode impedir a saída: remova-a das
arenas que precisam ser evacuadas. Login/respawn dentro de uma região restrita
utilizam a busca segura existente de até 16 blocos em chunks carregados; sem destino
seguro, o jogador é desconectado com orientação administrativa.

## Instalar as integrações

O Guard inicia sem Raids, Essentials, Cobblemon e Flan. Para `/raid entrar` conceder
acesso, instale também o **Raids atualizado**. O Raids antigo não conhece esta API e
seu teleporte será recusado. Instale o **Essentials atualizado** para recusar antes
de gravar histórico/cooldown. Os patches dos dois repositórios acompanham esta entrega;
nenhum JAR de terceiro é incorporado ao Guard.

Raids valida participação e destino seguro antes de chamar a API; persistência do
retorno permanece anterior ao deslocamento. Recusa remove o retorno provisório e
não registra participação nem cooldown. `/raid sair` invalida autorização e preserva
o retorno se o teleporte falhar. Encerramento e desconexão invalidam autorizações.
Regras de batalha, dano, captura, participação e recompensas não são reescritas.

O Essentials chama o pré-teleporte no momento de executar, após warmup e carregamento
assíncrono e antes do histórico/cooldown. Não substitui predicados de moderação.
Uma recusa posterior de outro mod também impede mensagem de sucesso, mas não há
transação universal de rollback do histórico/cooldown para mods sem pré-teleporte.

## API e segurança

`dev.shirokai.guard.api.GuardEntryApi` expõe uma ABI apenas com tipos JDK, permitindo
chamada opcional por reflexão no Raids que usa mappings Mojang:

```java
boolean teleportRaid(UUID player, String dimension, double x, double y, double z,
                     float yaw, float pitch, String raidId);
boolean canTeleport(UUID player, String dimension, double x, double y, double z);
void invalidatePlayer(UUID player);
void endRaid(String raidId);
```

Chame no thread do servidor. O teleporte é síncrono, retorna sucesso efetivo e não
expõe tokens ao jogador. A autorização tem UUID do jogador, regiões, dimensão,
integração, raid e expiração; é consumida uma única vez antes da operação e revogada
em `finally`. Os hooks aninhados só recebem acesso ao destino exato dessa operação.
Uma falha nunca deixa autorização para `/back`. Não existe autorização baseada no
texto do comando. Mods instalados na mesma JVM são código confiável, não um sandbox:
a API não impede um mod malicioso de alterar posições diretamente.

Cobrem-se os caminhos vanilla de teleporte, mudança dimensional/portais, movimento,
veículos, Ender Pearl, Chorus Fruit e login/respawn já interceptados pelo Guard.
Mods que escrevem diretamente em coordenadas fora desses contratos precisam de
adaptador próprio; não há garantia universal para qualquer mod arbitrário.

## Configuração preservada

Em `config/shirokai-guard/integrations.json`, adicione opcionalmente:

```json
"raidArenaAccess": { "enabled": true, "authorizationTimeoutTicks": 40 }
```

Preserve `crateInteractions` e as demais chaves. Arquivos antigos usam os defaults
sem serem substituídos. Timeout: 1..200 ticks. `enabled: false` desativa a concessão
pela integração; **não** abre regiões `authorized-only`. Invalidação ao sair,
desconectar, encerrar raid, recarregar ou desligar é obrigatória. Não existem opções
para manter tokens reutilizáveis ou desativar a proteção de movimento/teleporte.

Mensagens em `messages.json`: `entry-blocked`, `raid-entry-blocked`,
`teleport-blocked`. Defaults incluem as mensagens solicitadas no documento.
Avisos de entrada têm limite de um por segundo/jogador.

## Reproduzir a compilação

Use JDK 21 e os wrappers de cada repositório. No checkout de Raids/Essentials no
commit indicado no início deste documento, aplique o patch correspondente com
`git apply /caminho/Shirokai-Guard/docs/integrations/patches/Shirokai-Raids.patch`
ou `Shirokai-Essentials.patch`, e execute `./gradlew --no-daemon test build`.
No Guard execute `./gradlew --no-daemon test runGametest build`.

Para testar os serviços reais, coloque somente os dois JARs instaláveis atualizados
de Raids e Essentials em um diretório e execute, na raiz do Guard:

```bash
./gradlew --no-daemon -PwithCobblemon -PwithArenaIntegrations \
  -ParenaIntegrationMods=/caminho/absoluto/dos/dois-jars test runGametest build
```

Os dois testes adicionais chamam o adaptador real do Raids e o serviço real do
Essentials. Não simulam a criação de uma raid completa nem jogam uma batalha.
O perfil expõe ao lançamento de desenvolvimento as bibliotecas Kyori já embutidas
no JAR do Essentials, sem modificá-las ou incorporá-las ao Guard.

Para validar Flan real, use o wrapper de `validation/flan` com `runGametest` e depois
`-PwithCobblemon runGametest`. Relatórios da atualização: [VALIDATION.md](VALIDATION.md).

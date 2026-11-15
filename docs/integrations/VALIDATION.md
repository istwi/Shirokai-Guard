# Validação da atualização de acesso a arenas

Executada em 15/11/2026 com JDK Temurin 21.0.13, Minecraft 1.21.1 e wrappers
fixados de cada repositório. Os relatórios XML desta execução estão em
[`../reports/arena-access`](../reports/arena-access).

| Verificação | Resultado |
| --- | --- |
| Guard: JUnit | 48 aprovados, 0 falhas, 0 ignorados |
| Raids atualizado: JUnit | 125 aprovados, 0 falhas, 0 ignorados |
| Essentials atualizado: JUnit | 47 aprovados, 0 falhas, 0 ignorados |
| Guard sem Raids/Essentials/Cobblemon: GameTests | 46 aprovados |
| Guard + Raids + Essentials + Cobblemon 1.8.3: GameTests | 55 aprovados |
| JAR Guard + Flan 1.12.8 real | 8 aprovados |
| JAR Guard + Flan + Cobblemon 1.8.3 | 8 aprovados |
| Compilação/JARs Guard, Raids e Essentials | concluída |

Comandos executados:

```bash
# Raiz do Guard
./gradlew --no-daemon test runGametest build
./gradlew --no-daemon -PwithCobblemon -PwithArenaIntegrations \
  -ParenaIntegrationMods=/workspace/arena-integration-mods test runGametest build
# Em cada checkout atualizado de Raids e Essentials
./gradlew --no-daemon test build
# Em validation/flan (wrapper próprio)
./gradlew --no-daemon runGametest
./gradlew --no-daemon -PwithCobblemon runGametest
```

Nesta máquina os comandos receberam `JAVA_HOME=/workspace/toolchains/jdk-21.0.13+9`
e `GRADLE_USER_HOME=/workspace/gradle-home`. Proxy e truststore foram configurados
fora dos repositórios; verificação TLS e checksums permaneceu ativa. O Flan obtido
por Maven coincide com o JAR oficial conferido por SHA-512 da API Modrinth.

Os testes novos verificam: validação/persistência da flag; grupos, herança e
prioridades; sobreposições e exceções `normal`; movimento através da região;
saída de ocupantes; autorizações de uso único, expiração, corrida entre consumidores,
UUID/dimensão/região incorretos, invalidação e reinicialização; teleporte autorizado,
saída e retorno comum recusado; autorização nova aceita; métodos boolean/void/rede,
flags relativas do pacote, veículo, alvo de portal, mudança dimensional, Ender Pearl
real com controle positivo; preservação de `entry` e `teleport`; falha sem autorização
remanescente; verificação de login/respawn; destinos comuns e região `normal`.

Os testes com integrações reais carregam os JARs atualizados. Chamam o adaptador de
entrada do Raids e o `TeleportService.systemTeleport` do Essentials. Este último
confirma que uma recusa não move o jogador nem altera `back`/`nextTeleportAt`, e
confirma o teleporte permitido após remover a restrição. Os GameTests anteriores
incluem busca segura de login e regressões de outras proteções. Flan testa que suas
recusas independentes continuam prevalecendo sobre permissões/bypass do Guard.

## Limites

- Não foi jogada uma raid multiplayer completa nem executada a sequência de todos
  os comandos `/home`, `/back`, `/tpa` e `/warp` por clientes. Os caminhos comuns
  de teleporte e as integrações de produção foram exercitados conforme acima.
- A condição de login/respawn e a busca segura são testadas no servidor; não foi
  executada uma campanha de reconexões de clientes, morte ou uso físico de portais.
- Não foi lançada a suíte específica de carga com 100 jogadores nesta atualização.
- LuckPerms real não foi carregado nesta execução. Os testes existentes da Permissions
  API preservam negativas explícitas; a validação histórica com LuckPerms permanece
  documentada separadamente. Nenhuma regra de permissão foi alterada.
- A primeira tentativa do perfil conjunto falhou porque Loom não expõe automaticamente
  bibliotecas Kyori internas do Essentials em desenvolvimento. O perfil foi corrigido
  para carregar cópias inalteradas, e a nova execução completou todos os 55 testes.
- Logs incluem avisos opcionais do Cobblemon, overrides do Flan para mods ausentes e
  consulta de chave Yggdrasil indisponível no processo de testes offline. As suítes
  completaram com os resultados acima; autenticação de produção não foi alterada.

Não há publicação do ambiente, push ou release remota nesta entrega.

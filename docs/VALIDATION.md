# Validação — 0.2.0-alpha.1

Execução em 13/11/2026, Linux x86_64, Temurin JDK 21.0.13, Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric Loader 0.17.2 e Fabric API 0.116.18+1.21.1. Projeto principal: Gradle 8.12/Loom 1.9.2; Flan: projeto separado Gradle 9.2.1/Loom 1.14.10.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Compilação, JAR remapeado e JUnit | 36 testes, zero falhas |
| GameTests sem opcionais | 36 testes, zero falhas |
| GameTests com Cobblemon 1.8.3 | 43 testes, zero falhas |
| Conjunto real com Flan e módulos Shirokai | 14 testes, zero falhas |
| Cliente Minecraft gráfico real | PASS: navegação, templates, configuração e bloqueio de descarte de ícones |
| Perfil de carga | 100 jogadores internos, 80.000 decisões protegidas, 200 ticks medidos |
| 100 clientes remotos / raid completa jogada | Não executados; sem alegação de capacidade ou cobertura desses fluxos |

Comandos na raiz:

```bash
./gradlew test runGametest build
./gradlew -PwithCobblemon runGametest
./gradlew -PloadTest runGametest
./gradlew projectArchive
```

Não execute dois `runGametest` da raiz simultaneamente: o mundo descartável `build/gametest` é recriado. XMLs são separados por perfil: `build/gametest-results-{vanilla,cobblemon,load,visual}.xml`. JUnit: `build/reports/tests/test/index.html` e `build/test-results/test/`. Carga: `build/load-profile.json`. Nos perfis visual/carga concluídos por callbacks externos, o XML gerado pelo Fabric contém a suíte sem entradas `testcase`; o sucesso está confirmado no log do servidor (`All 1 required tests passed`), no código de saída e no resultado específico do perfil. Os trechos correspondentes estão preservados em `docs/reports/*-server-summary.txt`.

## Cobertura

JUnit: limites inclusivos, coordenadas negativas, dimensões/globais/regiões grandes, índice/redefinição, prioridades/deny, fallback de build, herança/ciclos, UUID/grupos, comandos/listas/namespaces, imutabilidade, seleção, persistência/backup, rejeição de corrupção sem sobrescrita, templates/mescla/flags opcionais, cache/invalidação/isolamento por ator e amostragem limitada de bordas enormes.

GameTests executam o Minecraft real: blocos, explosões, pistões e hoppers cruzando limites, fluidos, entidades, projéteis, dano/PVP, fome, gelo/fogo/crescimento, descarte/coleta, comandos, portas multipartes e teleportes. Controles positivos garantem que a mecânica funciona quando permitida. As adições cobrem menus somente leitura, modos de clique, revogação/negativas, grupos, confirmação de template alterado, chat privado, paginação, reload inválido sem alterar estado, movimento pelo servidor e barco com passageiro (negativo e positivo), e busca de destino seguro carregado para login/respawn.

Os sete testes Cobblemon usam o artefato original: spawn, captura prévia, preservação de cancelamento anterior, batalha prévia, interação, dano com controle positivo e Pasture. Não substituem captura ou batalha multiplayer jogada até o fim. `pokemon-breeding` não é exposta sem contrato real no Cobblemon original.

## Mods reais juntos

Guard 0.2.0-alpha.1, Flan 1.12.8, Cobblemon 1.8.3, LuckPerms Fabric 5.4.140, Impactor 5.3.5+1.21.1, Essentials 0.2.2, Spawn 1.1.0, Raids 0.1.18-dev e Crates 0.2.1.

Os oito cenários Flan verificam bloqueios independentes de quebra/colocação/baús, bypass Guard sem anular Flan, controles positivos e claim não alterado. Os seis adicionais verificam inicialização/serviços/comandos, negativa real LuckPerms sobre OP nível 4, teleporte Essentials, `/explorar` real, assinatura de teleporte Raids e crate registrada com chave real: recusa interna Flan mantém tela fechada/chave intacta; autorização abre o menu após checkpoints e liberação do bloqueio interno do serviço.

Relatório: `validation/flan/build/flan-results-shirokai-cobblemon.xml`. Procedimento em [validation/flan](../validation/flan/README.md); commits e limites por módulo em [INTEGRATIONS.md](INTEGRATIONS.md). Os módulos Shirokai foram compilados dos fontes vinculados; Spawn precisou de seleção externa de Loom 1.9.2 para remapeamento válido, sem alterar os fontes. Não se afirma ter validado outro binário publicado nem uma raid/roleta completa.

## Cliente gráfico

Minecraft/Fabric real em Xorg dummy, Mesa software, 1280×720. Um cliente offline local conectou ao servidor GameTest em loopback. O Guard não foi instalado no cliente. A fixture concedeu permissões de teste apenas ao jogador `GuardVisual`.

A automação abriu administração, regiões e flags; tentou descartar um ícone pelo protocolo real; verificou cursor vazio e ícone preservado; abriu prévia/aplicou template; navegou às configurações, alterou partículas e confirmou persistência/atualização da tela. Cliente produziu `PASS` e o GameTest do servidor completou. [Como repetir](../validation/client/README.md).

Capturas nativas: [administração](screenshots/01-admin.png), [regiões](screenshots/02-regions.png), [flags](screenshots/03-flags.png), [template](screenshots/04-template-preview.png) e [configurações](screenshots/05-settings.png).

## Carga e limites

`-PloadTest` cria exatamente 100 `ServerPlayerEntity` com conexões internas, 40 ticks de aquecimento e 200 ticks medidos, com 400 decisões verificadas por tick. O tempo engloba o corpo medido do tick (incluindo as consultas e entidades), não tempo de rede ou espera entre ticks. Nesta execução: média de 2,96 ms, p95 de 5,68 ms e máximo de 118,07 ms. Havia outra JVM de validação ativa na mesma máquina; o pico foi preservado no relatório. Os números estão em [load-profile.json](load-profile.json). Não é benchmark de 100 clientes reais, chunks distribuídos ou conjunto completo de mods; não é SLA de TPS.

O índice mantém regiões muito grandes/globais numa lista por dimensão; consultas custam proporcionalmente à quantidade nessa lista. Cache guarda somente posições e é invalidado nas mutações. Eventos de proteção não fazem persistência de disco nem forçam chunks. Login/respawn procuram destino seguro apenas em colunas já carregadas e desconectam se não houver.

Relatórios preservados no projeto: [docs/reports](reports/), incluindo os XMLs e o resultado do cliente.

## Ambiente e observações

As execuções locais usaram JDK em `/workspace/tools/jdk-21.0.13+9`, cache em `/workspace/gradle-cache`, proxy e truststore TLS do ambiente. Cobblemon foi resolvido por cópia local verificada do mesmo artefato oficial; o projeto usa Maven oficial, sem dependência desse espelho. Flan/LuckPerms/Impactor tiveram checksums conferidos contra API Modrinth.

Cobblemon registra avisos de Mixins opcionais/datafixers e arquivos de jogadores novos inexistentes; os testes usam dados descartáveis, sem contas de produção. O lançamento de desenvolvimento também pode avisar sobre chave Yggdrasil indisponível; isso não valida autenticação externa. A CI está configurada, mas não foi executada no GitHub neste chat.

Nenhum ambiente ou release foi publicado. Esta entrega continua alpha: não é um sandbox contra mods que escrevem diretamente no mundo fora dos contratos protegidos. Use primeiro uma cópia do servidor e dados.

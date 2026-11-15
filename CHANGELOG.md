## 0.5.0-alpha.1

- Flags independentes `fly`, `elytra` e `pokemon-mount`, com grupos, herança e prioridades.
- Bloqueio de ativação e da entrada já voando/montado, incluindo travessia física de áreas estreitas.
- Evento de montaria real do Cobblemon e proteção adicional contra montagem nativa forçada.
- Encerramento de atividades já proibidas no local com proteção temporária de queda e preservação das permissões de voo.
- Atualização do manual e testes com voo, élitros, montarias terrestres/aquáticas/aéreas e regressões das integrações.

## 0.4.0-alpha.1

- Novo modo `command-only` e `/sg entry-command <região> add/remove/list/clear`.
- Correspondência exata de comando e argumentos, com lista local persistida por região.
- API de comando independente da raid, com token de uso único para jogador/região/dimensão/destino.
- Essentials 0.3.2-guard.1 associa a autorização à operação pendente durante warmup/carga de chunks.
- Testes de dispatch/aliases, argumentos, warmup real, TPA e preservação da API Raids.

## 0.3.0-alpha.1

- Adiciona `entry-mode` por região/grupo com valores `normal`, `deny` e `authorized-only`, prioridades e herança.
- API opcional para teleporte de raid com autorização temporária de uso único, restrita ao destino, sem bypass de outras flags.
- Mantém saídas, comandos e destinos comuns; proteção de movimento, veículos, login/respawn e teleportes usa os hooks existentes.
- Integrações reais atualizadas no Raids e Essentials, distribuídas como patches e binários separados.
- Acrescenta testes unitários, GameTests e perfil opcional com os mods reais.

# Changelog

## 0.2.0-alpha.1 — 13/11/2026

- Menus administrativos vanilla, paginação, entrada privada no chat e confirmações de ações.
- Templates opcionais, prévia e confirmação com expiração e rejeição de dados alterados.
- Configurações reais de menus, permissões, flags padrão e templates; gravações atômicas.
- Bordas com partículas limitadas, cache espacial limitado e atualização de metadados sem reindexação completa.
- Proteção de movimento pelo servidor/veículos e destino seguro no login/respawn.
- Compatibilidade de interação Crates com decisão interna de uso de bloco do Flan.
- Testes conjuntos dos mods reais, perfil de carga e cliente Minecraft gráfico.


## 0.1.0-alpha.1 — em desenvolvimento

- Projeto Fabric para Minecraft 1.21.1/Java 21, Gradle wrapper com SHA-256 e validação em CI.
- Cuboides e regiões globais, seleção com machado, expansão, UUIDs, prioridades e herança validada.
- Flags com grupos e resolução determinística; proteção em callbacks e mecanismos vanilla.
- Persistência com snapshots atômicos e backup; configuração básica, logs e bypass explícito.
- Integração opcional Cobblemon 1.8.3 baseada nas APIs reais, sem flag de reprodução fictícia.
- Exceção restrita à interação/prévia de blocos registrados do Shirokai Crates.
- Proteção de partes vinculadas de portas, camas, plantas altas e Pasture.
- Validação isolada do JAR com Flan 1.12.8: oito GameTests, também executados junto de Cobblemon 1.8.3.
- Testes JUnit e GameTests no servidor sem mods opcionais.

Versões experimentais. Os limites de API e validação estão documentados em docs/VALIDATION.md.

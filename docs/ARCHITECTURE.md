# Núcleo

- `core/Region`: dados imutáveis, UUIDs, grupos e flags validadas.
- `core/Bounds`: cuboides inclusivos, coordenadas negativas e operações de seleção.
- `core/RegionManager`: validação transacional da árvore de herança e índice por dimensão/chunk. Regiões acima de 4.096 chunks e globais ficam em uma lista separada por dimensão; consultas a muitas regiões enormes custam proporcionalmente a essa lista.
- `core/RegionStore`: snapshot JSON com schema 1, validação integral, fsync do arquivo temporário, backup do snapshot anterior e rename atômico obrigatório. Dados corrompidos impedem a inicialização em vez de iniciar desprotegido. O backup nunca é carregado silenciosamente.
- `ShiroGuard`: ciclo de vida, Permissions API e fila serial de gravação. Os eventos consultam somente memória na thread principal. O comando `save` enfileira um snapshot; falhas aparecem em `status` e nos logs. `reload` aguarda a fila e valida os novos dados antes de trocar o estado.
- `Protection` e `mixin/`: callbacks Fabric e validações nos mecanismos vanilla, sem Bukkit ou WorldEdit.

## Resolução

Dimensões são identificadas pelo resource location completo (`namespace:path`). Um ID de região é único por dimensão. `__global__` tem limites nulos e participa da mesma resolução de prioridades; não recebe precedência especial.

Consulta: prioridade decrescente, depois ID lexicográfico. Somente regiões com valor definido para a ação participam. A maior prioridade que define a ação vence; em empate `deny` vence. Para textos, vence o primeiro ID nessa prioridade. Listas de comandos na mesma prioridade acumulam restrições. `block-break` e `block-place` usam `build` como fallback quando não têm valor específico.

Cada região consulta sua flag, depois seus pais. Grupo específico precede `all`. Owners e members são herdados; owners, members e nonmembers são grupos distintos. Ser owner **não concede bypass**. Para liberar owners, configure uma flag em `@owners`. Um pai deve existir na mesma dimensão, ciclos são rejeitados e remover um pai com filhos é proibido.

`unset` remove apenas a configuração local do grupo, reativando herança/fallback. Ausência de configuração permite a ação. A única flag de estado com semântica de ativação é `invincible`: `allow` ativa invulnerabilidade. `hunger deny` congela o processamento de fome (também a regeneração natural feita pelo HungerManager).

## Limites da versão alpha

Proteção é aplicada aos caminhos vanilla documentados. Não é um sandbox contra mods que escrevem diretamente no mundo, no inventário ou na posição de entidades. Crescimento é verificado em CropBlock, StemBlock, NetherWartBlock, CocoaBlock, SweetBerryBushBlock, SugarCaneBlock, BambooBlock, CactusBlock e PitcherCropBlock. Plantas de outros mods com lógica própria precisam de hooks adicionais. Entrada/saída verifica a posição de destino em teleportes e interseções do segmento físico de movimento com os cuboides, impedindo saltos através de regiões estreitas. `EntrySafety` verifica login e respawn e procura um destino permitido em colunas já carregadas até 16 blocos; falha sem destino desconecta o jogador. `PhysicalMovementMixin` também cobre movimento causado pelo servidor e entidades com passageiros jogadores.

Não há garantia de capacidade para 100 jogadores. O teste sintético de índice não simula ticks, outras máquinas, dimensões ativas ou mods de terceiros.

## Interface e configuração

`GuardMenus` usa containers vanilla somente leitura; cliques válidos executam ações do servidor. Sessões são vinculadas a jogador, dimensão e geração da configuração. Reload invalida telas e entradas pendentes. Chat privado temporário revalida autorização antes de executar, sem elevar permissões. `TemplateActions` guarda planos de 60 segundos e compara região e modelo antes da aplicação.

`AtomicJson` e `GuardConfig` validam snapshots completos antes de instalar a configuração. Escritas usam temporário, fsync, rename atômico e backup; menus de opções persistem antes de recarregar. Templates mesclam apenas flags explicitadas.

O cache LRU limitado guarda resultados espaciais por coordenada e dimensão, nunca decisões de autorização. Toda mutação o invalida. Mudanças sem alteração geométrica atualizam as referências nas listas de chunks afetadas sem reconstruir o índice inteiro; criação/redefinição/remoção reconstroem o índice validado. Partículas usam amostragem de bordas próximas com orçamento limitado.

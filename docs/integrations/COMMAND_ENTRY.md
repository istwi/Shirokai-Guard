# Entrada somente por comando — Guard 0.4.0-alpha.1

Atualize **Guard para 0.4.0-alpha.1** e **Essentials para 0.3.2-guard.1**.
O Raids 0.1.20-guard.1 continua usando a mesma API, sem precisar de atualização.

## Configurar uma loja

Crie a região normalmente e configure:

```mcfunction
/sg flag loja entry-mode command-only
/sg entry-command loja add warp loja
```

Somente o teleporte iniciado por `/warp loja` recebe autorização para entrar nesta
região. `/warp spawn`, `/warp outra`, `/warp loja extra`, `/back`, `/home` e TPA
não herdam essa autorização. Aceitar TPA dentro da loja não autoriza o visitante.
Entradas caminhando, em veículos e teleportes sem autorização permanecem bloqueadas.
Ocupantes podem mover-se e sair; a flag não acrescenta restrição de saída.

Se `/loja` for um alias que encaminha para esse teleporte pelo Essentials, cadastre
também o comando que o jogador realmente digita:

```mcfunction
/sg entry-command loja add loja
/sg entry-command loja list
/sg entry-command loja remove warp loja
/sg entry-command loja clear
```

`clear` deixa uma região `command-only` sem entradas autorizadas. Para voltar ao
comportamento comum, use `/sg flag loja entry-mode normal` ou `unset` para herdar.
Permissão de administração da lista: `shirokaiguard.region.flag`.

A correspondência é do **comando completo**, sem curingas nem prefixos. Espaços
separadores são normalizados; espaços e maiúsculas dentro dos argumentos são
preservados. Argumentos distinguem maiúsculas/minúsculas. `/warp loja` não cadastra
automaticamente `/essentials:warp loja`, outro alias, nem `/warp Loja`.
Até 32 comandos e 2048 caracteres no total por região. O nome da região usa a
dimensão do administrador ao executar `/sg entry-command`.

## Separação da raid e precedência

A arena continua assim:

```mcfunction
/sg flag raid_arena entry-mode authorized-only
```

`authorized-only` recebe apenas a autorização da API de raids. `command-only`
recebe apenas a autorização do comando configurado. Não há mudança automática de
regiões existentes, nem bloqueio global de comandos.

Prioridades e herança do modo continuam válidas. Em empate, todas as exigências
restritivas do destino precisam ser atendidas; `deny` prevalece. Evite sobrepor
regiões de modos distintos na mesma prioridade: uma autorização de raid não abre
uma loja, nem uma autorização de loja abre uma raid. Uma região `normal` de maior
prioridade cria uma exceção local explícita. Sair dessa exceção para a área restrita
exige autorização novamente.

A lista de comandos é **local à região**; herdar `command-only` de um pai não copia
a lista do pai. Configure os comandos também no filho. As listas são persistidas
com os dados da região na flag `entry-commands`, sem substituir outras flags.

`entry deny`, `exit deny`, `teleport deny`, flags de comandos e recusas de outros
mods continuam válidas. Remova as negativas indesejadas da arena/loja e de seus pais.
O bypass administrativo continua exigindo a permissão e `/sg bypass` ativo.

## Teleportes com espera e segurança

O Guard registra o comando enquanto o servidor o executa, com limpeza em `finally`.
Aliases aninhados preservam o comando externo: um alias não cadastrado não herda
a regra do comando interno. O Essentials captura uma autorização opaca para uma
operação específica, vinculada ao jogador, regiões, dimensão, coordenadas exatas e
comando. A autorização acompanha **o objeto do teleporte pendente**, incluindo carga
de chunks; não fica em uma janela de liberação associada ao jogador.

Ao executar, o Guard consome a autorização antes de permitir efeitos e revalida a
lista e todas as flags. Não pode ser reutilizada, transferida a outro jogador/destino,
nem aproveitada por um teleporte de sistema enquanto o warmup está pendente.
Cancelamento, desconexão, reload, parada e falhas invalidam a autorização.
O limite defensivo é de 12000 ticks, superior ao warmup máximo suportado pelo Essentials.
O histórico/cooldown de uma entrada recusada pelo Guard permanece preservado.

## Outros mods e API

A integração pronta atende aos teleportes de destino fixo do **Essentials atualizado**
e aos aliases que encaminham para esse fluxo. Um `/loja` de outro mod que teleporta
diretamente, ou um teleporte diferido/dinâmico de outro sistema, precisa de adaptador.
Somente cadastrar o texto não autoriza esses métodos: o comportamento é recusar.
Não se promete suporte universal a qualquer plugin ou mod pela simples digitação.
RTP com destino dinâmico calculado depois do comando não ganha autorização automática.

A ABI opcional, somente com tipos JDK, acrescenta a `GuardEntryApi`:

```java
UUID captureCommand(UUID player, String dimension, double x, double y, double z);
int runCommandTeleport(UUID token, UUID player, String dimension,
                       double x, double y, double z, IntSupplier operation);
void revokeCommand(UUID token);
```

Capture durante a execução real do comando no thread do servidor, depois das
validações do mod de origem. Guarde o token somente na operação correspondente.
Na execução, passe a mesma identidade/destino e um callback que verifique o
pré-teleporte, persista os efeitos e teleporte, nessa ordem. Revogue sempre em caso
de cancelamento/falha. Fora de uma execução de comando de jogador, a captura retorna
null. `runCommandTeleport` remove o token mesmo numa tentativa com destino incorreto.
Mods na mesma JVM são código confiável, não um sandbox contra mods maliciosos.

Resultados desta atualização: [COMMAND_ENTRY_VALIDATION.md](COMMAND_ENTRY_VALIDATION.md).

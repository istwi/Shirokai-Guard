# Correção do aviso indevido após login/teleporte — 0.5.1-alpha.1

## Sintoma

O jogador consegue entrar, sair e usar `/spawn` ou outros teleportes normalmente,
mas aparece no chat `Você não pode entrar nesta região.` após o login ou teleporte,
inclusive num spawn onde somente construção ou outras ações estão restringidas.

## Causa reproduzida

O Minecraft mantém uma posição de teleporte pendente até o cliente confirmar o ID
recebido. Enquanto isso, pacotes de movimento ainda podem carregar coordenadas
anteriores. O próprio Minecraft descarta esses pacotes sem aplicá-los.

O Guard verificava esses dados **antes** da checagem de confirmação nativa. Assim,
um pacote que não representava movimento efetivo podia gerar um aviso de região
ou de movimento excessivo, mesmo com o jogador permanecendo no destino permitido.
O mesmo problema existia nos pacotes de veículos.

## Correção

Os dois handlers de movimento agora deixam a checagem nativa de teleporte pendente
acontecer antes de tentar avaliar esses pacotes no Guard. A rotina original continua
executando: o Guard não cancela a confirmação, nem muda seu ID para corrigir um
pacote que o Minecraft já descartaria.

Não há janela temporária de bypass. Um pacote pendente continua sem mover o jogador.
Após a confirmação, a proteção de movimento volta a verificar a entrada normalmente.
Teleportes do servidor, comandos, autorizações, movimento físico, flags de entrada,
voo e montaria continuam usando os mesmos controles.

**Não é necessário remover a região nem mudar as flags do spawn.** Substitua somente
o JAR do Guard por `shirokai-guard-0.5.1-alpha.1.jar`, remova a versão anterior e
reinicie o servidor. Raids e Essentials não precisam ser atualizados novamente.
O texto de `entry-blocked` continua existindo para recusas reais.

## Reprodução e validação

Três testes nativos reproduziram as falhas antes da alteração e passaram após ela:

1. Teleporte permitido para um spawn com apenas `build deny`, seguido de um pacote
   com a posição anterior. O personagem não se move, não recebe aviso indevido e
   o ID pendente é preservado.
2. Pacote de jogador durante uma confirmação pendente não gera aviso nem entra na
   região. Depois da confirmação, uma entrada efetivamente proibida é recusada e
   gera a mensagem. Um teleporte direto proibido também continua recusado enquanto
   há confirmação pendente.
3. Pacote de veículo pendente não gera aviso nem desloca o barco. Após confirmação,
   uma tentativa real de entrar na região restrita continua sendo barrada e avisada.

Os testes usam handlers e pacotes reais, confirmação nativa e observação da mensagem
enviada ao jogador. A fixture padrão dos demais testes mantém a criação original;
a captura de mensagens é uma opção usada somente nos testes de regressão.

| Perfil final | Resultado |
| --- | --- |
| JUnit Guard | 58 aprovados |
| Regressões específicas, perfil isolado | 3 aprovadas |
| GameTests sem Cobblemon/Raids/Essentials | 61 aprovados |
| GameTests com Cobblemon, Raids e Essentials | 75 aprovados |
| JAR instalável com Flan real | 8 aprovados |

Relatórios: [`../reports/teleport-sync`](../reports/teleport-sync).

```bash
# Apenas os três casos de regressão, na raiz
./gradlew --no-daemon -PteleportSyncTest runGametest
# Suíte base e build
./gradlew --no-daemon test runGametest build
# Suíte conjunta, com os dois JARs no diretório indicado
./gradlew --no-daemon -PwithCobblemon -PwithArenaIntegrations \
  -ParenaIntegrationMods=/caminho/absoluto/jars test runGametest build
# Wrapper próprio em validation/flan
./gradlew --no-daemon runGametest
```

Validação em 15/11/2026 com JDK 21 e Minecraft 1.21.1. Base: `d7c342f`.
A reprodução cobre o erro do Guard em pacotes pendentes; não é uma inspeção da
configuração privada do servidor nem uma garantia sobre sistemas de teleporte que
alterem coordenadas fora dos caminhos nativos. Uma mensagem que continuar aparecendo
numa tentativa real deve ser investigada pelas regras aplicáveis, sem apagar a
mensagem globalmente ou liberar uma região restrita.

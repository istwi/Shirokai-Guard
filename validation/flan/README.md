# Validação do Guard com Flan real

Este projeto carrega o **JAR instalável** do Shirokai Guard 0.5.1-alpha.1 e o Flan **1.21.1-1.12.8-fabric** do Modrinth. Não altera o código de produção do Guard, as bibliotecas do Flan ou dados de um servidor existente. Os claims de teste são criados apenas no mundo descartável em `build/gametest/` deste projeto.

O SHA-512 do Flan foi conferido com o publicado pela API Modrinth (versão `1SJYtmxm`). O JAR resolvido pelo Maven foi comparado ao mesmo arquivo verificado. Lingua Bibliotheca e SnakeYAML são extraídos, sem modificações, dos JARs internos do Flan para o lançamento de desenvolvimento. Nenhum deles é redistribuído dentro do Guard.

O Flan foi compilado com Loom 1.14.10, incompatível com o Loom 1.9.2 do projeto principal. Por isso esta validação usa um projeto separado com Gradle 9.2.1 e wrapper com SHA-256, Loom 1.14.10, Kotlin 2.3.20, JDK 21, Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric Loader 0.17.2 e Fabric API 0.116.18+1.21.1. A compilação principal do Guard continua com suas versões anteriores.

## Executar

Primeiro compile o Guard na raiz. Depois use o wrapper **deste diretório**:

```bash
# Na raiz do repositório
./gradlew build

cd validation/flan
./gradlew runGametest
./gradlew -PwithCobblemon runGametest
```

A segunda variante acrescenta Cobblemon 1.8.3+1.21.1 com suas dependências oficiais e o ICU necessário ao Graal. Todos os oito cenários são repetidos com os três mods instalados juntos. Isso não repete os sete testes específicos das APIs Cobblemon da suíte principal.

## Cenários

1. Guard permite, Flan proíbe: quebra bloqueada e bloco preservado.
2. Bypass do Guard efetivamente habilitado: a proibição independente do Flan continua bloqueando quebra.
3. Flan permite, Guard proíbe: quebra bloqueada.
4. Ambos permitem: quebra realmente remove o bloco (controle positivo).
5. Guard permite baús, Flan proíbe: a interface do baú não abre.
6. Guard permite construção, Flan proíbe: uma tentativa de colocação pelo `ServerPlayerInteractionManager` não coloca o bloco.
7. Ambos permitem baús: a interface realmente abre (controle positivo).
8. Flan permite baús, Guard proíbe: a interface não abre.

As precondições verificam as decisões dos dois mods antes de tentar as ações. O primeiro cenário também compara o JSON do claim antes e depois das operações, confirmando que o Guard não o modificou. O teste de bypass concede somente `shirokaiguard.bypass` por um provedor da Permissions API de teste; não concede permissões de bypass do Flan nem afirma testar o LuckPerms real.

O teste inicial de baú foi corrigido: `PASS` no callback Fabric do Flan significa continuar o processamento. O Flan usa flags internas separadas para uso de bloco e item e pode impedir a abertura mais adiante. Os testes verificam o `ScreenHandler` resultante, não usam o retorno desse callback como prova de autorização.

## Conjunto Shirokai completo

Depois de compilar o JAR principal:

```bash
./gradlew -PwithCobblemon -PwithShirokai -PintegrationMods=/caminho/absoluto/mods runGametest
```

O diretório precisa conter exatamente os seis JARs: `LuckPerms-Fabric-5.4.140.jar`, `Impactor-Fabric-5.3.5+1.21.1.jar`, `shirokai-essentials-0.2.2.jar`, `shirokai-spawn-1.1.0.jar`, `Shirokai-Raids-0.1.18-dev-fabric-1.21.1.jar` e `shirokai-crates-0.2.1.jar`. O padrão local desta máquina é `/workspace/guard-test-mods/integrations`. Não há redistribuição desses binários no ZIP do projeto.

LuckPerms e Impactor foram obtidos do Modrinth e conferidos contra SHA-512 da API. Os módulos Shirokai foram compilados dos commits vinculados em [INTEGRATIONS.md](../../docs/INTEGRATIONS.md); dependem de seus próprios builds. O Spawn exigiu selecionar Loom 1.9.2 por init script externo para gerar um JAR não vazio com Gradle 8.12. Os JARs remapeados de produção são copiados ao diretório de mods do mundo descartável; não se extraem dependências duplicadas desses seis mods.

Para recompilar os quatro módulos, faça checkout dos commits vinculados em `docs/INTEGRATIONS.md` em diretórios separados. Use Java 21 e Gradle 8.12 instalado (caminho absoluto), executando `gradle --no-daemon assemble` em Essentials, Raids e Crates. No checkout Spawn use:

```bash
/caminho/gradle-8.12/bin/gradle -I /caminho/Shirokai-Guard/validation/flan/spawn-loom.init.gradle assemble
```

Copie de cada `build/libs/` apenas o JAR remapeado instalável, não `sources`/`dev`. Confira que cada arquivo contém `fabric.mod.json` e classes antes de executar a suíte. LuckPerms e Impactor devem ser as variantes Fabric das versões acima; preserve seus nomes esperados. O próprio build de cada módulo pode resolver dependências adicionais por seus repositórios oficiais.

## Resultados e limites

Em 13/11/2026, **14 testes passaram** no conjunto Guard 0.2.0-alpha.1 + Flan 1.12.8 + Cobblemon 1.8.3 + os seis JARs acima. Relatório `build/flan-results-shirokai-cobblemon.xml`.

Além dos oito cenários base, verificam: conjunto/serviços/comandos carregados, negativa real do LuckPerms sobre OP, teleporte pelo serviço Essentials, comando Spawn `/explorar`, assinatura de teleporte utilizada pelo Raids e interação de crate real com chave sob decisão interna do Flan (recusa e controle positivo). Os dados LuckPerms de jogadores internos são carregados antes do login; checkpoints e conclusão no servidor do Crates são aguardados antes de ambas as interações. A fixture observa somente leitura o campo interno `busy` para não confundir espera de persistência com recusa de proteção.

As variantes base de oito testes também foram aprovadas anteriormente na versão 0.1.0-alpha.1, com e sem Cobblemon. A execução completa atual utiliza o JAR instalável 0.2.0-alpha.1.

O mundo é recriado a cada execução. Não use este perfil num servidor existente. Estes testes não jogam uma raid completa nem validam todas as transações econômicas. A falha na busca de chave pública Yggdrasil durante o lançamento de desenvolvimento não impediu as conexões de teste offline. A configuração de produção deve manter a autenticação normal.

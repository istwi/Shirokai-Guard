# Cliente Minecraft de validação

Fixture gráfica separada, nunca distribuída no mod Guard. Usa Minecraft 1.21.1/Fabric real, sem Guard no cliente. Precisa de Java 21, Gradle 8.12, display X11 e OpenGL (Mesa software é suficiente).

Na raiz, em um terminal:

```bash
./gradlew -PvisualTest runGametest
```

O mundo descartável abre somente `127.0.0.1:25567`, sem autenticação externa, aceita apenas o nome `GuardVisual` e concede permissões Guard por um provedor de teste. Essa configuração pertence exclusivamente à fixture, nunca ao lançamento de produção. Quando aparecer `Visual test ready`, em outro terminal:

```bash
DISPLAY=:98 LIBGL_ALWAYS_SOFTWARE=true ./gradlew -p validation/client runClient
```

Use seu display real se diferente de `:98`. O teste automatiza conexão, menu administrativo, lista de regiões, flags, rejeição de descarte de ícones, navegação, prévia/confirmação de template e persistência de configuração. Ao terminar envia `/sg visualdone` e encerra ambos os jogos. O resultado obrigatório é `PASS` em `validation/client/build/client-result.txt`, além de `All 1 required tests passed` no log do servidor e saída Gradle com sucesso; o código de saída do cliente sozinho não prova sucesso. O XML `build/gametest-results-visual.xml` emitido pelo Fabric nesse perfil contém apenas a suíte, sem `testcase` para a conclusão pelo callback externo; use as evidências anteriores. Capturas nativas ficam em `validation/client/build/client/screenshots/`.

As imagens desta execução são copiadas para `docs/screenshots/`. O teste usa um cliente local offline; não mede autenticação de produção, latência externa nem 100 clientes conectados.

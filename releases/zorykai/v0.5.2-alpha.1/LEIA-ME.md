# Zorykai Guard 0.5.2-alpha.1

Minecraft 1.21.1, Fabric, Java 21. Substitua apenas o JAR Guard e reinicie.

- `/sg bypass` funciona mesmo com comandos bloqueados, exigindo autorização para `shirokaiguard.bypass` ou OP nível 4. Outros comandos não recebem essa exceção.
- A criação e o redimensionamento de claims de jogadores pelo Flan são negados quando sobrepõem regiões Guard. A seleção pode começar fora; o volume final inteiro é conferido. Não exige flag adicional.
- A enxada de ouro não é proibida. Claims fora das regiões seguem as regras normais do Flan; claims antigos não são removidos. Criação administrativa por API e subclaims de claims antigos não são alterados.
- Bypass autorizado e ativado permite a operação no Guard; não ignora permissões independentes do Flan.
- IDs técnicos e configurações são compatíveis. Os quatro textos visuais da marca usam Zorykai/ZORYKAI, preservando as cores e formatações.

Validação: 58 testes unitários, 65 GameTests do Guard sem Flan e 10 GameTests com Flan 1.21.1-1.12.8-fabric real. O JAR visual foi executado nos testes com Flan. Consulte `runtime-validation.json`, `flan-results.xml` e `audit.json`.

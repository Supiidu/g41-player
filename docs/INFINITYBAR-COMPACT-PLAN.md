# Próxima etapa — InfinityBar compacta (Moto G41 / Infinity X)

> Branch isolada de pesquisa e implementação, criada sobre a v0.9.0-beta4.1 do G41 Glass Player. O player expandido aprovado não será modificado nessa etapa.

## Situação verificada

- O módulo atual intercepta `AxDynamicBarKeyguardExpansion.expand()` em `G41Hook.java` somente para eventos `IslandEvent$Media`.
- O painel expandido é desenhado separadamente em `GlassOverlay.java`; ele já preserva Monet, posição inferior, onda CAVA HD e fechamento na pausa.
- A InfinityBar **compacta** é renderizada por Jetpack Compose (a análise anterior do SystemUI identificou `AxDynamicBarKeyguardChipKt.java`). Os toques são processados por um callback que chama `AxDynamicBarKeyguardExpansion.expand()` ou `collapse()` conforme o estado.
- `CompactBarVisibility.java` só oculta uma View identificada com segurança. Pode não alcançar o chip quando ele é um nó dentro de uma `AndroidComposeView` em tela cheia; **nunca** ocultar o Compose root inteiro só para esconder o chip.
- O comportamento anterior que precisamos revisar: o chip compacto pode continuar visível após abrir nosso player expandido.

## Critérios de segurança

1. Limitar alterações à mídia; preservar timers, notificações, chamadas e outros tipos de `IslandEvent`.
2. Preservar o `expand()` original caso o módulo não consiga abrir seu overlay.
3. Não modificar/ocultar a root da lockscreen ou a `AndroidComposeView` de tela cheia.
4. Restaurar o estado e a visibilidade do chip ao fechar o overlay; não deixar resíduos nem quebrar o toque.
5. Fazer a mudança opt-in por `Settings.Global`, com desativação via ADB, antes de evoluir para um visual novo.
6. Manter backup e recuperação do Vector/Magisk; modificações no SystemUI podem causar bootloop.

## Passos de implementação (antes do novo visual)

1. Levantar a estrutura atual da ROM: classes Compose, `AxDynamicBarChipViewModel` e os fluxos de estado de mídia (sem supor nomes de métodos de outras versões).
2. Instrumentar apenas logs para eventos de mídia compacta e transições de expansão, com rate limit. Confirmar no G41 a sequência: tocar chip → abrir overlay → chip some → fechar overlay → chip retorna.
3. Implementar um mecanismo direcionado ao nó/estado do chip, **não** ao AndroidComposeView inteiro. Conservar fallback e chave para desligar.
4. Aplicar o visual compacto definido pelo dono do projeto e validar layout, toque, mídia, pausa e troca de faixa.

## Identificação visual pendente

O visual final do chip compacto ainda precisa ser definido: tamanho, formato, uso da capa/Monet, título, animação e efeito ao toque. Não adivinhar o design e não aplicar hooks visuais de alto risco sem validar esses requisitos.

## Código atual envolvido

- `app/src/main/java/dev/g41/glassplayer/G41Hook.java`
- `app/src/main/java/dev/g41/glassplayer/CompactBarVisibility.java`
- `app/src/main/java/dev/g41/glassplayer/GlassOverlay.java`

**Estado:** plano e branch criados; não foram aplicados hooks novos à InfinityBar compacta nesta etapa.

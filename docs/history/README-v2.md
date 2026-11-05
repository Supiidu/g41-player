# G41 Glass Player v0.2 - Shape Test

Objetivo: mudar apenas o shape do `Surface` do card de controles musicais dentro de `KeyguardMediaPanel()` de 28dp para 12dp, mantendo os callbacks e controles originais. O painel externo pode continuar com arredondamento de 32dp; este teste nao muda o painel inteiro.

Metodo: o hook de `KeyguardMediaPanel` marca a thread (ThreadLocal) durante a composicao sincrona. O hook de `SurfaceKt` procura o overload com assinatura estrita, e substitui `args[1]` (Shape) apenas se a thread estiver compondo o player e se o shape for o objeto `ShapeCard` original. A constante global nunca e alterada.

LIMITACOES: a abordagem e experimental e ainda intercepta um metodo Compose compartilhado. O hook pode nao atingir a chamada (por exemplo, devido a compilacao/inlining), ou pode causar instabilidade/perda de desempenho. Nao e o glassmorphism final. Se o overload nao for identificado de forma unica, a alteracao visual e desativada e so o diagnostico permanece.

Aplicacao: extrair estes arquivos dentro da pasta `G41GlassPlayer`, executar `bash apply-v2.sh`, `adb install -r app/build/outputs/apk/debug/app-debug.apk`, reiniciar. Logcat: `adb logcat -v time -s G41GlassPlayer:V '*:S'`. Testar primeiro por poucos segundos.

Reversao: substituir o G41Hook.java pelo G41Hook.java.backup-v1, recompilar e instalar; ou desativar o modulo no Vector. Recuperacao mais forte (se ADB estiver acessivel): `adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"` seguido de reboot, o que desativa o Vector inteiro ate remover o arquivo `disable`.

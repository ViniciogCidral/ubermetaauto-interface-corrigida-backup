# VerificaÃ§Ã£o desta entrega

Esta versÃ£o foi validada localmente em 14/08/2026 com JDK 17 e Gradle 8.9.

Comando executado:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Resultado: `BUILD SUCCESSFUL`.

Os 6 testes de regras de negÃ³cio passaram sem falhas. Eles cobrem o cÃ¡lculo de
ciclos descontando pausa, validaÃ§Ã£o de pausas, indicadores da oferta, decisÃ£o,
recusa explÃ­cita e projeÃ§Ã£o sem dados suficientes.

O APK gerado por essa validaÃ§Ã£o estÃ¡ em `apk-debug/app-debug.apk`. Para compilar
de novo no AndroidIDE, use `bash ./compilar-androidide.sh` na raiz do projeto.

Ainda Ã© necessÃ¡rio testar no celular as permissÃµes do Android e os textos reais
mostrados pela versÃ£o instalada do Uber Driver; isso depende do aparelho e da
interface que o Uber disponibiliza no momento do teste.

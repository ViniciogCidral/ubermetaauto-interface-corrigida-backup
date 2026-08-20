# Como compilar no AndroidIDE

Este projeto inclui o Gradle Wrapper 8.9 e um comando preparado para o AndroidIDE.

No terminal aberto dentro da pasta do projeto, execute:

```bash
bash compilar-androidide.sh
```

O script:

1. procura e usa o JDK 17 já instalado no AndroidIDE;
2. usa o Gradle 8.9 definido pelo projeto, em vez do Gradle global do terminal;
3. isola o cache do projeto para não carregar o `androidide-kmp-node.gradle` antigo que causou o erro `ReflectionCache`;
4. usa o `aapt2` fornecido pelo AndroidIDE quando ele estiver disponível.

Na primeira execução, o Wrapper pode baixar o Gradle 8.9. Ao terminar com sucesso, o APK estará em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Se o script informar que não encontrou JDK 17, rode estes comandos e envie a saída:

```bash
pkg list-installed | grep openjdk
ls -1 $PREFIX/lib/jvm
```

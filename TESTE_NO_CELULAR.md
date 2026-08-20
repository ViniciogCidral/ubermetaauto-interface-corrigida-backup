# UberMetaAuto — roteiro de teste no celular

## Antes de compilar

1. Extraia o ZIP e abra a pasta que contém `settings.gradle`, `build.gradle`, `gradlew` e `app` no AndroidIDE.
2. Use JDK 17 e instale o SDK/API 35 caso o AndroidIDE peça.
3. Compile e instale o APK. A primeira compilação pode baixar dependências do Gradle e do ML Kit.

## Configuração inicial

1. Abra o app e toque no menu `☰` > `Planejar turno`.
2. Para validar o cálculo, escolha 06:00–22:00 e pausa 10:00–14:00. A tela deve indicar **12 ciclos efetivos** (16 horas menos 4 horas de pausa).
3. Em `Permissões`, habilite sobreposição, acessibilidade e notificações.
4. Toque em `INICIAR` e aceite a autorização de captura do Android.

## Fluxo de trabalho

- Antes de uma corrida ser aceita, a tela e a notificação devem indicar que o ciclo aguarda a primeira corrida aceita.
- Abra a Uber Driver. Uma oferta reconhecida deve abrir o overlay preto e branco com a decisão colorida (`PEGAR`, `TALVEZ` ou `PASSAR`), valor, tempo, distância, R$/km e, quando a tela da Uber mostrar de forma legível, a nota do passageiro.
- Arraste o overlay até **encostar de verdade** na borda direita: ele deve virar a etiqueta compacta, sem botões nem dados extras. Ao arrastá-lo para longe, os dados retornam.
- Minimize o overlay: a bolinha `UMA` deve continuar arrastável, manter a cor da última decisão e voltar ao cartão completo ao tocar nela.
- Feche o overlay pelo `×`: o turno fica pausado. O botão `RETOMAR` dentro do app deve reabrir o overlay.
- Aceite uma corrida real: somente então o ciclo deve começar. Durante a corrida ativa não deve aparecer uma segunda oferta.
- Conclua a corrida: ela deve ir para `Última corrida` e para `Histórico`.
- Teste uma recusa pelo botão visível da Uber e outra oferta deixando o tempo acabar. O histórico deve distinguir `RECUSADA` de `EXPIRADA` se a Uber expuser o texto correspondente à acessibilidade.

## Verificações de segurança

- Saia da Uber Driver e abra a Galeria ou outro app com imagens de corridas. Não deve surgir overlay nem uma nova oferta no histórico.
- O aviso permanente do Android deve usar o formato padrão de notificação e resumir o estado do turno/ciclo. Ele não é uma cópia visual da interface interna.
- A pausa planejada deve suspender novas ofertas no horário definido; se houver corrida ativa, ela termina primeiro e a pausa só passa a valer no restante da janela planejada.

## Observações para o primeiro teste

- A duração de uma corrida concluída é registrada a partir dos sinais de aceite e conclusão. A distância exibida no histórico ainda é a estimativa da oferta, porque a tela final real da Uber precisa ser validada antes de virar fonte de distância real.
- A nota do passageiro é lida de modo conservador para não confundir tempo/distância com avaliação. Se ela não aparecer em uma oferta real, envie uma captura dessa tela para ajustarmos o reconhecimento.
- O app apenas lê a tela e mostra recomendações; não toca em aceitar, recusar ou qualquer outro comando da Uber.

## Limite desta revisão local

Os XMLs, referências de recursos e ligações da interface foram revisados neste computador. A compilação do APK não pôde ser executada aqui porque não há JDK/SDK Android instalado; por isso o build no AndroidIDE é a validação final obrigatória.

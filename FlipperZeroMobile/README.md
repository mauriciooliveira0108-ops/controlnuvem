# Flipper Zero Mobile

Multiferramenta Android de diagnóstico de hardware com estética retrô (laranja/preto, fonte monoespaçada, pixel art). Usa **apenas o hardware embutido do celular** e as **APIs públicas do Android**. Não precisa de dispositivo externo.

> Projeto independente, inspirado visualmente em multiferramentas de hardware. Não é afiliado à Flipper Devices Inc. O mascote (golfinho) e os ícones são pixel art originais.

## Funcionalidades

| Módulo | O que faz | APIs |
|---|---|---|
| **Início** | Painel de status em tempo real (NFC, Bluetooth, Wi-Fi, IR, bateria, permissões) e menu em cards adaptativo | `BroadcastReceiver`, `ConnectivityManager` |
| **NFC Tools** | Modo leitor (NfcA/B/F/V, ISO-DEP, Barcode); UID; tecnologias; ATQA/SAK; tipo (NTAG213/215/216, Ultralight, MIFARE Classic*, FeliCa, ISO 15693); NDEF (texto, URI, MIME, AAR, externo) em hex e UTF-8; dump de memória Ultralight/NTAG sem senha; salvar, histórico e exportação (TXT/JSON) | `NfcAdapter.enableReaderMode`, `android.nfc.tech.*` |
| **Bluetooth Scanner** | Varredura BLE com timeout configurável; nome, endereço, RSSI, TX power, conectável, UUIDs, dados de fabricante, estruturas AD; filtro/ordenação; conexão GATT **somente por ação do usuário** para listar serviços e características; salvar e exportar CSV | `BluetoothManager`, `BluetoothLeScanner`, `BluetoothGatt` |
| **Wi-Fi Analyzer** | Conexão atual: SSID/BSSID, RSSI, frequência, banda, canal, padrão (Wi-Fi 4–7), velocidades, IPv4/IPv6, gateway, DNS, interface, validação, tarifação; varredura de redes visíveis com segurança; avisos de throttling e localização; snapshot e relatório | `ConnectivityManager.NetworkCallback`, `NetworkCapabilities`, `LinkProperties`, `WifiManager` |
| **Infrared Remote** | Detecta emissor via `ConsumerIrManager`; mostra faixas de portadora; controle com Power, Volume, Canal, D-pad, Menu, Input, Voltar; protocolos NEC, NEC estendido, Samsung32 e Sony SIRC-12; predefinições (LG, Samsung, Sony) e controles personalizados salvos; envio de código manual. Sem emissor: exibe **"Emissor infravermelho indisponível neste aparelho"** | `ConsumerIrManager` |
| **Device Information** | Fabricante, modelo, Android/API, patch, SoC, ABIs, núcleos, frequência, RAM, armazenamento, bateria (nível, saúde, temperatura, tensão, corrente), sensores, NFC/HCE/MIFARE, Bluetooth/BLE 5, Wi-Fi Direct/Aware/RTT, IR, UWB, USB host — com indicadores de compatibilidade | `Build`, `ActivityManager`, `BatteryManager`, `SensorManager`, `PackageManager` |
| **Activity Terminal** | Logs reais do app persistidos (NFC, BLE, Wi-Fi, IR, permissões, armazenamento), timestamps, busca, filtro por nível/categoria, rolagem automática, limpar, exportar; comandos internos (`help`, `status`, `find`, `clear`, `export`, `version`, `echo`). **Não** executa shell nem finge root | Room |
| **Saved Data** | Abas NFC / BLE / Wi-Fi / IR com detalhes, renomear, excluir, apagar tudo e exportar | Room |
| **Settings** | Feedback tátil, efeito CRT, timeout BLE, filtro padrão BLE, retenção de logs, logs de depuração | DataStore |

\* MIFARE Classic só é identificado em aparelhos com controlador NXP; o app não tenta autenticar setores.

## Arquitetura

MVVM + Repository, Kotlin, Jetpack Compose, Coroutines + StateFlow, injeção de dependência manual (`AppContainer`).

```
app/src/main/java/com/flippermobile/
├── MainActivity.kt, FlipperApp.kt
├── core/        di, logging (ActivityLogger), export (FileProvider), haptics, permissions, util (hex, formatação)
├── data/        db (Room: entidades, DAOs), prefs (DataStore), repository
├── domain/      lógica pura e testável: modelos, NDEF, codificadores IR, BLE (AD/UUIDs/filtro),
│                Wi-Fi (banda/canal/segurança), permissões por versão, exportação, filtro de logs, comandos do terminal
├── hardware/    wrappers das APIs Android: nfc, ble, wifi, ir, device, status
├── features/    home, nfc, bluetooth, wifi, infrared, device, terminal, saved, settings (Screen + ViewModel)
└── ui/          theme, components (cards, pixel art, mascote, barras), navigation
```

A camada `domain/` não importa nada de Android, por isso é coberta por testes JVM. A camada `hardware/` isola o acesso ao hardware das telas.

## Pré-requisitos e versões

| Ferramenta | Versão |
|---|---|
| JDK | 17+ (testado em 17 no CI; 21 também funciona) |
| Gradle (wrapper) | 8.14.3 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (plugin Compose Compiler 2.0.21) |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 (Material 3) |
| Room | 2.6.1 · DataStore 1.1.1 · Navigation Compose 2.8.5 · Lifecycle 2.8.7 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0+) |

Necessário: Android SDK Platform 35 e acesso a `google()` e `mavenCentral()`.

## Como abrir no Android Studio

1. *File → Open* e selecione a pasta `FlipperZeroMobile/`.
2. Aguarde a sincronização do Gradle (o Studio cria `local.properties` com `sdk.dir`).
3. Execute a configuração `app` em um aparelho físico (emuladores não têm NFC/BLE/IR reais).

## Como compilar

```bash
cd FlipperZeroMobile
chmod +x gradlew
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK gerado: `FlipperZeroMobile/app/build/outputs/apk/debug/app-debug.apk`

O workflow `.github/workflows/android.yml` executa testes + build a cada push e publica o APK como artefato **flipper-zero-mobile-debug-apk**.

## Como instalar o APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou copie o APK para o aparelho e abra-o (permita "instalar apps desconhecidos" para o gerenciador de arquivos).

## Permissões

| Permissão | Quando | Por quê |
|---|---|---|
| `NFC` | instalação | Ler tags |
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT` | runtime, Android 12+ | Varredura BLE, nomes e GATT |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | instalação, até Android 11 | API Bluetooth legada |
| `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE_LOCATION` no 12+) | runtime | Varredura BLE até Android 11; SSID/BSSID e varredura Wi-Fi |
| `NEARBY_WIFI_DEVICES` | runtime, Android 13+ | Varredura Wi-Fi |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE` | instalação | Estado do Wi-Fi, iniciar varredura, rede atual |
| `TRANSMIT_IR` | instalação | Usar o emissor infravermelho |
| `VIBRATE` | instalação | Feedback tátil |

**Não** há permissão `INTERNET`: o app funciona totalmente offline e os dados ficam no armazenamento privado. Cada tela explica por que precisa de uma permissão antes de pedi-la e oferece um atalho para as Configurações se ela for negada.

## Compatibilidade por hardware

Todos os recursos são `required="false"`: o app instala em qualquer aparelho e informa o que falta.

- **NFC**: precisa de controlador NFC. MIFARE Classic só com chip NXP.
- **BLE**: praticamente todos os aparelhos Android 8+.
- **Wi-Fi**: universal; Wi-Fi 6E/7 e RTT dependem do chip/driver.
- **Infravermelho**: poucos aparelhos têm emissor (ex.: alguns Xiaomi/Redmi/POCO, Huawei e Samsung antigos). Sem emissor, a transmissão é desativada. Tela, flash ou câmera **não** substituem um emissor IR.

## Limitações das APIs Android

- **Wi-Fi**: a partir do Android 9, no máximo 4 varreduras a cada 2 minutos em primeiro plano; `startScan()` pode ser recusado e o app mostra o cache do sistema. SSID/BSSID exigem localização precisa **e** localização do sistema ligada. Apps não podem capturar tráfego nem fazer varreduras de rede de baixo nível.
- **BLE**: o Android limita 5 inícios de varredura a cada 30 s (o excesso falha silenciosamente ou com erro 6). Endereços de muitos dispositivos são aleatórios (privacidade). A flag `neverForLocation` pode filtrar alguns beacons.
- **NFC**: o modo leitor só funciona com o app em primeiro plano. UIDs de cartões ISO-DEP podem ser aleatórios. Setores protegidos por senha/chave não são lidos. Emulação de tags e escrita não fazem parte desta versão.
- **IR**: só transmite; nenhum celular Android recebe IR pela API pública. O padrão total precisa ter menos de 2 s.
- **Terminal**: mostra apenas eventos do próprio app; o Android não permite ler logs de outros apps nem executar comandos privilegiados.

## Testes

```bash
./gradlew testDebugUnitTest          # testes JVM (domain/core)
./gradlew connectedDebugAndroidTest  # testes de UI instrumentados (requer aparelho/emulador)
```

Testes unitários (48): hex/UTF-8/hexdump, formatação pt-BR, parser NDEF (texto, URI, MIME, AAR), codec de persistência, codificadores IR (estrutura NEC/NEC-ext/Samsung32/SIRC, decodificação de volta, limites, < 2 s), parser de códigos IR, permissões por versão de Android, banda/canal/sinal/segurança Wi-Fi, estruturas AD BLE, nomes GATT, filtro/ordenação BLE, filtro de logs, comandos do terminal, escape JSON/CSV e exportação.

Testes instrumentados: navegação Início → Device Information → voltar, e abertura do terminal.

Veja o estado real da compilação em [BUILD_REPORT.md](BUILD_REPORT.md).

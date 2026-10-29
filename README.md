# Gblas AimBot — Free Fire Android

Aimbot sem root para Free Fire usando Accessibility Service + MediaProjection.

## Como usar

1. Baixe o APK em **Actions → gblas-aimbot-debug**
2. Instale no celular (habilite fontes desconhecidas)
3. Abra o app → **Ativar Acessibilidade** → ative "Gblas AimBot" na lista
4. Volte → **Iniciar AimBot** → autorize captura de tela
5. Abra o Free Fire

## Calibração

Em `AimBotAccessibilityService.kt`:
- `sensitivity = 0.04f` — aumenta para mira mais agressiva
- `aimJoyX/Y` — ajuste se usar layout invertido

Em `AimBotService.kt`:
- `fireCooldownMs = 200L` — intervalo mínimo entre disparos (ms)

## Build local

```bash
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

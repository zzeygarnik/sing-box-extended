<div align="center">

# ZGRNK

**Одно Android-приложение. Четыре транспорта. Переключение в одно касание.**

Своя сборка [sing-box for Android](https://github.com/SagerNet/sing-box-for-android) на ядре
[sing-box-extended](https://github.com/shtorm-7/sing-box-extended) — AmneziaWG, VLESS + Reality (TCP и XHTTP)
и NaiveProxy рядом в одном профиле, с автоматическим выбором самого быстрого пути.

[![release](https://img.shields.io/github/v/release/zzeygarnik/sing-box-extended?label=release&color=2ea44f)](https://github.com/zzeygarnik/sing-box-extended/releases/latest)
[![build](https://img.shields.io/github/actions/workflow/status/zzeygarnik/sing-box-extended/zgrnk-android.yml?branch=zgrnk&label=build)](https://github.com/zzeygarnik/sing-box-extended/actions/workflows/zgrnk-android.yml)
[![android](https://img.shields.io/badge/android-7.0%2B-3DDC84?logo=android&logoColor=white)](#установка)
[![abi](https://img.shields.io/badge/abi-arm64--v8a-555)](#установка)
[![license](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

[English](README.md) · **Русский**

[Скачать APK](https://github.com/zzeygarnik/sing-box-extended/releases/latest) ·
[Шаблоны профилей](zgrnk/examples) ·
[Отличия от upstream](#отличия-от-upstream)

</div>

<!--
Скриншоты — сюда, когда появятся в docs/screenshots/:

<p align="center">
  <img src="docs/screenshots/dashboard.png" width="240">
  <img src="docs/screenshots/profiles.png" width="240">
  <img src="docs/screenshots/groups.png" width="240">
</p>
-->

---

## Транспорты

| | Транспорт | Поверх чего | Что даёт |
|---|---|---|---|
| 🛡️ | **AmneziaWG 1.5** | UDP | WireGuard с junk-пакетами, паддингом рукопожатий (S1–S4), своими заголовками сообщений (H1–H4), сигнатурными пакетами `I1` и random trailers. Баг рукопожатия из upstream в этой сборке исправлен. |
| ⚡ | **VLESS + Reality · TCP** | TCP / TLS 1.3 | Чистый TCP с flow `xtls-rprx-vision` и uTLS-отпечатком Chrome. Минимальные накладные расходы среди TLS-вариантов. |
| 🌊 | **VLESS + Reality · XHTTP** | TCP / TLS 1.3, HTTP/2 | XHTTP-транспорт Xray (`stream-one`) со случайным паддингом запросов. Выглядит как обычный долгоживущий HTTP/2-поток. |
| 🧭 | **NaiveProxy** | TCP / TLS, HTTP/2 | Туннель через HTTP `CONNECT` на сетевом стеке Chromium — TLS-рукопожатие настоящее браузерное. |

Все четыре живут в одном профиле. Группа `urltest` раз в 3 минуты проверяет их и пускает трафик через самый быстрый,
а `selector` позволяет вручную закрепить любой транспорт на вкладке **Groups**.

```mermaid
flowchart LR
    apps[Приложения на телефоне] --> tun[TUN]
    tun --> proxy{{"proxy · selector"}}
    proxy --> auto{{"auto · urltest"}}
    auto --> awg[AmneziaWG 1.5]
    auto --> rtcp[Reality · TCP]
    auto --> rxh[Reality · XHTTP]
    auto --> naive[NaiveProxy]
    proxy -. ручной выбор .-> rtcp
    tun -- локальные сети --> direct[direct]
```

## Возможности

- **Всё в одном приложении** — не нужно держать отдельные клиенты для WireGuard, Xray и Naive.
- **Автоматический failover** — если транспорт перестал отвечать, `urltest` сам переводит трафик на следующий.
- **Раздельное туннелирование по приложениям** — пустить в туннель только выбранные приложения или исключить те, что должны идти напрямую.
- **DNS over HTTPS через туннель** — системный резолвер используется только чтобы найти сами серверы.
- **Локальная сеть остаётся локальной** — приватные диапазоны адресов идут мимо туннеля.
- **Ставится рядом с обычным SFA** — свой package id (`io.nekohasekai.sfa.zgrnk`), не заменяет и не конфликтует с другими сборками sing-box.
- **Воспроизводимые сборки** — каждый APK собирается публичным [GitHub Actions workflow](.github/workflows/zgrnk-android.yml), подпись проверяется перед выгрузкой.

## Установка

1. Скачай `SFA-*-zgrnk.*-arm64-v8a.apk` из [последнего релиза](https://github.com/zzeygarnik/sing-box-extended/releases/latest).
2. Открой файл на телефоне и разреши установку из неизвестных источников, когда система спросит.
3. Требования: Android 7.0 или новее, 64-битный ARM (`arm64-v8a`) — это практически любой телефон последних лет.

Обновления ставятся поверх предыдущей версии, профили сохраняются.

## Настройка профиля

Приложение принимает нативные **JSON-профили sing-box** (не ссылки `vless://` и не `.conf`-файлы WireGuard).

1. Возьми шаблон из [`zgrnk/examples`](zgrnk/examples):

   | Файл | Что внутри |
   |---|---|
   | [`all-in-one.json`](zgrnk/examples/all-in-one.json) | Все четыре транспорта + автовыбор. Начинать с него. |
   | [`amneziawg.json`](zgrnk/examples/amneziawg.json) | Только AmneziaWG 1.5 |
   | [`vless-reality-tcp.json`](zgrnk/examples/vless-reality-tcp.json) | VLESS + Reality поверх чистого TCP (Vision) |
   | [`vless-reality-xhttp.json`](zgrnk/examples/vless-reality-xhttp.json) | VLESS + Reality поверх XHTTP |
   | [`naiveproxy.json`](zgrnk/examples/naiveproxy.json) | Только NaiveProxy |

2. Замени все `<плейсхолдеры>`, хосты `example.com` и порты на значения своего сервера.
   Транспорты, которых у тебя нет, удали из `all-in-one.json` — и из обоих списков групп тоже.
3. В приложении: **Profiles → New Profile → Import**, выбери файл. Затем выбери профиль и нажми **Start**.

### Что стоит знать

<details>
<summary><b>AmneziaWG</b> — параметры должны точно совпадать с сервером</summary>

- `jc`, `jmin`, `jmax`, `s1`–`s4`, `h1`–`h4` и `i1` в шаблоне — **примерные значения**. Реальные бери с сервера
  (`awg show` или секция `[Interface]` его конфига). Одно несовпадение — и рукопожатие молча не завершится.
- Оставь `"random_trailers": true`, если на сервере `RandomTrailers = on`.
- WireGuard в sing-box ≥ 1.11 — это **endpoint**, а не outbound. В группах и маршрутах на него ссылаются по тегу, как на обычный outbound.
- `mtu: 1280` — безопасное значение для мобильных сетей.
- Один ключ WireGuard = одно активное устройство. Если два клиента одновременно используют одного пира, побеждает последнее рукопожатие, второй отваливается.

</details>

<details>
<summary><b>XHTTP</b> — <code>x_padding_bytes</code> обязателен</summary>

Ядро отклоняет XHTTP-транспорт без `x_padding_bytes` (`x_padding_bytes cannot be disabled`). Значение — диапазон
на стороне клиента, например `"100-1000"`; на сервере ничего согласовывать не нужно. `mode: stream-one` работает
с Xray-сервером в режиме `auto`.

</details>

<details>
<summary><b>NaiveProxy</b> — зачем блокируется UDP/443</summary>

NaiveProxy передаёт только TCP. Шаблон отклоняет UDP на порт 443, чтобы приложения, которые сначала пробуют QUIC,
сразу переходили на TCP, а не ждали таймаута.

</details>

<details>
<summary><b>Раздельное туннелирование</b> — где настройка</summary>

**Settings → Profile Override → Per-App Proxy.** Режим *Include* — туннель только для выбранных приложений,
*Exclude* — для всех, кроме выбранных. Список общий для всех профилей. На Xiaomi / HyperOS сначала разреши приложению
читать список установленных приложений, иначе выбор будет пустым. В JSON то же самое — `tun.include_package` / `tun.exclude_package`.

</details>

### Серверная часть

Подходит любой стандартный серверный стек, ничего особого не нужно:

| Транспорт | Сервер |
|---|---|
| AmneziaWG 1.5 | [amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go) / amneziawg-tools с S3/S4, I1 и RandomTrailers |
| VLESS + Reality (TCP, XHTTP) | [Xray-core](https://github.com/XTLS/Xray-core), VLESS inbound с `realitySettings` |
| NaiveProxy | [Caddy](https://caddyserver.com), собранный с [forwardproxy@naive](https://github.com/klzgrad/forwardproxy) |

## Отличия от upstream

Ветка `zgrnk` основана на теге `v1.14.0-extended-2.7.1` из `shtorm-7/sing-box-extended`.

| Изменение | Где |
|---|---|
| **`random_trailers` доступен в конфиге.** Движок его поддерживал, но в обвязке sing-box не было JSON-поля, так что включить его было невозможно. Без него клиент не может работать с сервером, где random trailers включены. | [`3478e3d6`](https://github.com/zzeygarnik/sing-box-extended/commit/3478e3d6) · `option/wireguard.go`, `transport/wireguard/`, `protocol/wireguard/` |
| **Исправление рукопожатия AmneziaWG.** С включёнными random trailers движок upstream брал срез буфера отправки длиннее фиксированного размера сообщения, `marshal` падал на проверке длины, а ошибка игнорировалась — initiation, response и cookie reply уходили с телом из одних нулей. Исправлено в отдельном форке движка, тег [`v0.0.5-extended-1.6.1-zgrnk.1`](https://github.com/zzeygarnik/wireguard-go/tree/v0.0.5-extended-1.6.1-zgrnk.1). | [`d72dcbbd`](https://github.com/zzeygarnik/sing-box-extended/commit/d72dcbbd) · `go.mod` → [zzeygarnik/wireguard-go](https://github.com/zzeygarnik/wireguard-go) |
| **Приложение закреплено на SFA 1.14.0** (`5d5479d`) под ядро 1.14, плюс заглушка для единственного платформенного метода, который добавляет extended-ядро. | [`193e129b`](https://github.com/zzeygarnik/sing-box-extended/commit/193e129b) · [`zgrnk/sfa-patches`](zgrnk/sfa-patches) |
| **Свой package id и название** (`io.nekohasekai.sfa.zgrnk`, «ZGRNK») — ставится рядом с другими сборками. | [`zgrnk/sfa-patches`](zgrnk/sfa-patches) |
| **CI-сборка** подписанного arm64 APK на каждый push в `zgrnk`. | [`.github/workflows/zgrnk-android.yml`](.github/workflows/zgrnk-android.yml) |

Остальное ядро не тронуто, так что все протоколы sing-box-extended доступны и в профилях, написанных вручную.

<details>
<summary>Что ещё есть в ядре (из sing-box-extended)</summary>

WARP, MASQUE, MTProxy, Mieru, TrustTunnel, Sudoku, SSH, VPN, Bond, Fallback, Failover · SDNS (DNSCrypt), DNS Fallback ·
mKCP, XHTTP, Rmux · Amnezia 3.0, VLESS encryption · провайдеры outbound'ов и парсер share-ссылок.
Полный список и примеры: [shtorm-7/sing-box-extended](https://github.com/shtorm-7/sing-box-extended#-features).

</details>

## Сборка из исходников

Эталон — [workflow](.github/workflows/zgrnk-android.yml). Локально, с Go 1.26.7, Android NDK r28 и JDK 17:

```bash
# 1. ядро -> libbox.aar
make lib_install
go run ./cmd/internal/build_libbox -target android -platform android/arm64

# 2. приложение на закреплённом коммите SFA с патчами ZGRNK
git clone https://github.com/SagerNet/sing-box-for-android sfa
git -C sfa checkout 5d5479d8bb60f7eb45e86402a8aa3a6131dd9ba3
for p in zgrnk/sfa-patches/*.patch; do git -C sfa apply "../$p"; done
mkdir -p sfa/app/libs && cp libbox.aar libbox-legacy.aar sfa/app/libs/

# 3. APK (нужен свой keystore, см. настройку подписи в SFA)
cd sfa && ./gradlew :app:assembleOtherRelease
```

Собирай flavor `other` — именно в нём есть NaiveProxy.

## Благодарности

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box) и [sing-box for Android](https://github.com/SagerNet/sing-box-for-android) — платформа и приложение
- [shtorm-7/sing-box-extended](https://github.com/shtorm-7/sing-box-extended) — расширенное ядро, на котором основана сборка
- [Amnezia VPN](https://github.com/amnezia-vpn), [XTLS/Xray-core](https://github.com/XTLS/Xray-core), [klzgrad/naiveproxy](https://github.com/klzgrad/naiveproxy) — протоколы

Неофициальная сборка, не связана с SagerNet и авторами sing-box-extended.

## Лицензия

[GPL-3.0](LICENSE), унаследована от sing-box.

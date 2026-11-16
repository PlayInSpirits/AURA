<div align="center">

<img src="app/src/main/res/drawable-nodpi/aura_logo.png" width="120" alt="AURA">

# AURA

**Android-клиент для домашнего музыкального сервера HomeMusic**

[![Сборка](https://github.com/PlayInSpirits/AURA-fork-HomeMusic/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PlayInSpirits/AURA-fork-HomeMusic/actions/workflows/build-apk.yml)
[![Последний релиз](https://img.shields.io/github/v/release/PlayInSpirits/AURA-fork-HomeMusic?include_prereleases&label=%D1%80%D0%B5%D0%BB%D0%B8%D0%B7)](https://github.com/PlayInSpirits/AURA-fork-HomeMusic/releases)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
[![Лицензия GPL-3.0](https://img.shields.io/badge/%D0%BB%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-GPL--3.0-blue)](LICENSE)

[Скачать APK](https://github.com/PlayInSpirits/AURA-fork-HomeMusic/releases) ·
[Возможности](#возможности) ·
[Установка](#установка) ·
[Сборка](#сборка) ·
[Лицензия](#лицензия)

</div>

---

AURA играет вашу музыкальную библиотеку с домашнего сервера [HomeMusic](https://github.com/justman-1/HomeMusic-public)
(Navidrome и хаб HomeMusic). Вся тяжёлая работа идёт на сервере: поиск, скачивание, рекомендации и ИИ-ассистент.
Приложение входит в аккаунт, проигрывает музыку и показывает хаб.

Основа приложения — открытый клиент Subsonic/Navidrome [Tempus](https://github.com/eddyizm/tempus).

> [!NOTE]
> Проект в стадии **бета**. Релизы публикуются как pre-release; что-то может работать не так, как задумано.

## Возможности

### 🌊 Моя волна
- Весь первый экран — живой фон волны, как в Яндекс Музыке: он уходит под прозрачную верхнюю панель и строку состояния.
- Стеклянная полупрозрачная кнопка: пауза/продолжить, если волна уже играет; новая волна — только когда играет другое.
- Остальное содержимое главной («Открытия» и подборки) ждёт ниже: потяните экран вверх — он пружинисто докрутится.

### 🔍 Поиск
- Поиск хаба прямо в приложении: каталог (исполнители, альбомы, треки), «Файлы» и «Без цензуры», история запросов.
- Страницы исполнителей и альбомов; песни из библиотеки играют сразу, остальное — одной кнопкой в загрузки хаба.

### 🎧 Плеер
- Фон из обложки: размытая обложка и её основной цвет, плавно меняются со сменой трека.
- Большая белая кнопка воспроизведения, крупная шкала, стеклянная панель с таймером, текстом, плейлистом и очередью.

### 🏠 Хаб
- Открывается отдельным окном со своей нижней панелью и кнопкой «Назад».
- «Микс дня» и «Моя волна» убраны из хаба (волна на главной), ссылка на Navidrome ведёт на домашний сервер.

### 🎨 Оформление
- Фиолетовая палитра AURA и чёрная AMOLED-тема по умолчанию, новая иконка.
- Три вкладки: Главная, Поиск, Хаб. «Выйти» — внизу настроек.

## Установка

1. Откройте [Releases](https://github.com/PlayInSpirits/AURA-fork-HomeMusic/releases) и скачайте
   `AURA-v….apk` из последнего релиза.
2. Установите APK на телефон (разрешите установку из неизвестных источников).
3. Введите адрес хаба. По умолчанию подставлен `http://192.168.1.129:4533/`.
4. Войдите под своим логином и паролем Navidrome (это же аккаунт хаба) или зарегистрируйтесь по коду приглашения.

Приложение ставится как `app.aura2.debug` и не мешает ни Tempus, ни оригинальному клиенту HomeMusic.
Все релизы подписаны одним ключом AURA, поэтому новые версии ставятся поверх старых без удаления.

### Адрес сервера

- Можно ввести адрес Navidrome (`http://192.168.1.129:4533/`) или адрес хаба (`http://192.168.1.129:8080/`).
  Приложение само найдёт хаб рядом (порты 4533 → 8080 и 8444 → 8445 стандартной схемы HomeMusic).
- Если хаб не найден, работает только плеер: «Моя волна», вкладка «Хаб» и регистрация по коду недоступны.
- Адрес домашнего хаба можно поменять позже: **Настройки → AURA → Адрес локального хаба**.
- Адреса `http://` разрешены, чтобы приложение работало в домашней сети.

## Сборка

### Через GitHub Actions

Workflow [`build-apk.yml`](.github/workflows/build-apk.yml):

- собирает `assembleAuraDebug`, запускает юнит-тесты и lint;
- подписывает APK ключом AURA из секретов репозитория (`AURA_KEYSTORE_*`); без них сборка останавливается;
- на ветке `main` публикует APK как pre-release `aura2-v<versionName>`.

Запускается на каждый push в `main` или вручную: **Actions → Build AURA APK → Run workflow**.
Изменения только в документации (`*.md`) сборку не запускают.

### Локально

Нужны JDK 21 и Android SDK (проще всего через Android Studio).

```bash
./gradlew assembleAuraDebug
```

APK появится в `app/build/outputs/apk/aura/debug/`. Локальная сборка подписывается отладочным ключом и не
установится поверх релизной версии.

### Структура

| Что | Где |
| --- | --- |
| Вариант сборки `aura`, пакет `app.aura2`, версия | `app/build.gradle`, `app/src/aura/` |
| Клиент API хаба и «Моя волна» | `app/src/main/java/com/eddyizm/tempus/hub/` |
| Экран входа в два шага | `ui/login/AuraConnectActivity.kt` |
| Вкладка «Хаб» | `ui/fragment/HubFragment.kt`, `hub/HubWebHost.kt` |
| Блок «Моя волна» и его живой фон | `ui/fragment/HomeTabMusicFragment.java`, `ui/view/WaveGlowView.kt` |
| Плитка «AURA» в настройках | `res/xml/global_preferences.xml`, `ui/fragment/SettingsContainerFragment.java` |

Приложение общается с HomeMusic только через HTTP API хаба
([`hub/API.md`](https://github.com/justman-1/HomeMusic-public/blob/main/hub/API.md)).

## Благодарности

- [Tempus](https://github.com/eddyizm/tempus) от eddyizm и [Tempo](https://github.com/CappielloAntonio/tempo)
  от CappielloAntonio — основа приложения.
- [HomeMusic](https://github.com/justman-1/HomeMusic-public) — сервер, хаб и шейдер фона «Моей волны».
- [Navidrome](https://www.navidrome.org/) — музыкальный сервер.

## Лицензия

[GNU GPL v3](LICENSE), как и у Tempus. Подробности и происхождение кода — в [NOTICE.md](NOTICE.md).

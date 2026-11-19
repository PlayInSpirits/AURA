<div align="center">

<img src="app/src/main/res/drawable-nodpi/aura_logo.png" width="120" alt="AURA">

# AURA

**Android-клиент для домашнего музыкального сервера HomeMusic**

[![Сборка](https://github.com/PlayInSpirits/AURA/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PlayInSpirits/AURA/actions)
[![Последний релиз](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fapi.github.com%2Frepos%2FPlayInSpirits%2FAURA%2Freleases%2Flatest&search=%22tag_name%22%5Cs*%3A%5Cs*%22%28%3F%3Aaura2-%29%3F%28v%5B%5E%22%5D%2B%29%22&replace=AURA-%241&label=%D0%9F%D0%BE%D1%81%D0%BB%D0%B5%D0%B4%D0%BD%D0%B8%D0%B9%20%D1%80%D0%B5%D0%BB%D0%B8%D0%B7&color=blue)](https://github.com/PlayInSpirits/AURA/releases)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
[![Лицензия GPL-3.0](https://img.shields.io/badge/%D0%BB%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-GPL--3.0-blue)](LICENSE)

[Скачать APK](https://github.com/PlayInSpirits/AURA/releases) ·
[Возможности](#возможности) ·
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

Приложение общается с HomeMusic только через HTTP API хаба
([`hub/API.md`](https://github.com/justman-1/HomeMusic-public/blob/main/hub/API.md)).

<div align="center">
  
# Лицензия

**[GNU GPL v3](LICENSE), самоподписанный AURA.**

</div>

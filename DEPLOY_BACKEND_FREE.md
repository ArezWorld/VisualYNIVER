# Бесплатный деплой backend для AOT

Ниже готовая схема, чтобы логин/регистрация работали с телефона без локального ПК.

## 1. Где бесплатно

Рекомендуемый вариант:
- Backend: Render (web service, free tier)
- База данных: Neon PostgreSQL (free tier)

## 2. Подготовка Google входа

В Google Cloud Console нужен `Web client ID` для backend-проверки `id_token`.
Этот ID нужно будет вставить в:
- Render env: `GOOGLE_WEB_CLIENT_ID`
- GitHub Actions secret: `GOOGLE_WEB_CLIENT_ID`

## 3. Деплой backend (Render)

1. Открой Render и подключи репозиторий `ArezWorld/VisualYNIVER`.
2. Render увидит файл `render.yaml` и предложит создать сервис `aot-backend`.
3. В переменную `DATABASE_URL` вставь строку подключения к Neon.
4. Дождись статуса `Live`.
5. Проверь: `https://<your-render-domain>/health` должен вернуть `{"status":"ok"}`.

## 4. Подключение Android к серверу

В GitHub репозитории открой:
- `Settings -> Secrets and variables -> Actions -> Variables`
- Добавь переменную `API_BASE_URL` со значением:
  `https://<your-render-domain>`

И в `Secrets` добавь:
- `GOOGLE_WEB_CLIENT_ID`

После этого новые APK из workflow будут собираться с рабочим серверным URL.

## 5. Что хранится в БД и как это защищено

- Пользователи и задачи хранятся в серверной БД (PostgreSQL).
- Пароли не хранятся в открытом виде: только хэши `werkzeug.generate_password_hash`.
- После входа сервер выдает JWT токен, приложение использует его в API запросах.
- Для Google входа backend валидирует `id_token` через Google API.

## 6. Важные замечания

- `10.0.2.2` работает только в эмуляторе Android.
- Для телефона нужен публичный HTTPS URL backend.
- На бесплатных тарифах сервис может "засыпать", первый запрос после простоя бывает медленнее.

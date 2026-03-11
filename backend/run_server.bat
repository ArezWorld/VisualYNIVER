@echo off
echo Установка зависимостей...
pip install -r requirements.txt

echo.
echo Запуск сервера AOT API...
echo Сервер будет доступен по адресу: http://localhost:8000
echo Документация API: http://localhost:8000/docs
echo.

python main.py

pause

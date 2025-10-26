# План архитектуры

## 1. Общая архитектура системы

**HabrBot — Telegram-бот для чтения статей Habr**

Бот получает апдейты через Telegram Bot API, обрабатывает команды и колбэки в `HabrBot`, парсит список и содержимое статей модулями `HabrParser` и `ArticleContentParser`, форматирует текст в MarkdownV2 (`HtmlToMarkdownV2`, `Markdown`) и безопасно делит длинные сообщения (`MessageSplitter`). Состояние (страница ленты, одноразовые токены) хранится в памяти процесса.

```mermaid
graph TB
    TG[Пользователь Telegram] --> TB[Telegram Bot API]
    TB --> BM[HabrBot - логика бота, колбеки, отправка]
    
    BM --> HR[Модуль парсинга статей]
    BM --> SR[Служебные модули и инфраструктура]
    
    HR --> SH[HabrParser - список статей и ArticleCard]
    HR --> GH[ArticleContentParser - полный текст и изображения]
    HR --> AH[HtmlToMarkdownV2 - HTML в MarkdownV2]
    HR --> LH[Markdown - экранирование MarkdownV2 и URL]
    
    SR --> GM[Bot Main и TelegramClient - инициализация]
    SR --> AI[ThreadPool workers - фоновая обработка]
    SR --> DB[In-Memory Storage - страницы и токены]
    
    AI --> LS[Error и Fallback Handler - перехват и деградация]
    AI --> OF[MessageSplitter - деление по лимитам TG]
    
    GM --> PE[EnvLoader - чтение token из ENV]
    
    DB --> SQL[ConcurrentHashMap - runtime данные без БД]
    
    LS --> QW[Logger - stdout]
    
    style AI fill:#e1f5fe
    style GM fill:#f3e5f5
    style DB fill:#e8f5e8

```

*Документация подготовлена: Артём Карелин (https://github.com/Smaug05)*
# План архитектуры

## 1. Общая архитектура системы

**HabrBot — Telegram-бот для чтения статей Habr**

Бот получает апдейты через Telegram Bot API, обрабатывает команды и колбэки в `HabrBot`, парсит список и содержимое статей модулями `HabrParser` и `ArticleContentParser`, форматирует текст в MarkdownV2 (`HtmlToMarkdownV2`, `Markdown`) и безопасно делит длинные сообщения (`MessageSplitter`). Состояние (страница ленты, одноразовые токены) хранится в памяти процесса.

Пример графа (адаптирован под ваш код)
```mermaid
graph TB
    TG[Пользователь Telegram] --> TB[Telegram Bot API]
    TB --> BM[HabrBot<br/>(логика бота, колбэки, отправка)]
    
    BM --> HR[Модуль парсинга статей]
    BM --> SR[Служебные модули и инфраструктура]
    
    HR --> SH[HabrParser<br/>(список статей → ArticleCard)]
    HR --> GH[ArticleContentParser<br/>(полный текст + изображения)]
    HR --> AH[HtmlToMarkdownV2<br/>(HTML → MarkdownV2)]
    HR --> LH[Markdown<br/>(экранирование MarkdownV2/URL)]
    
    SR --> GM[Bot (Main)<br/>+ TelegramClient<br/>(инициализация)]
    SR --> AI[ThreadPool (workers)<br/>(фоновая обработка)]
    SR --> DB[In-Memory Storage<br/>(страницы, токены)]
    
    AI --> LS[Error/Fallback Handler<br/>(перехват и деградация)]
    AI --> OF[MessageSplitter<br/>(деление по лимитам TG)]
    
    GM --> PE[EnvLoader<br/>(чтение token из ENV)]
    
    DB --> SQL[ConcurrentHashMap<br/>(без БД, runtime-данные)]
    
    LS --> QW[Logger<br/>(stdout)]
    
    style AI fill:#e1f5fe
    style GM fill:#f3e5f5
    style DB fill:#e8f5e8

```
*Документация подготовлена: Артём Карелин (https://github.com/Smaug05)*
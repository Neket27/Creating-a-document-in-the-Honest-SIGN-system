# CrptApi

Java-класс для работы с API Честного Знака (CRPT.ISMP) в одном файле.

## Описание

`CrptApi` позволяет создавать документы для ввода товаров в оборот на территории РФ.  
Класс полностью **thread-safe** и поддерживает ограничение количества запросов к API (rate limiting) с помощью семафора.

Особенности реализации:

- Метод `createDocument(Document document, String signature)` создаёт документ с подписью.
- Документы и их параметры передаются через внутренние классы `Document`, `DocumentType`, `DocumentGroup`, `DocumentFormat`.
- JSON-сериализация выполняется с помощью Jackson (`ObjectMapper`).
- HTTP-запросы выполняются через `HttpClient` (Java 17).
- Ограничение запросов реализовано через внутренний класс `RateLimiter`, блокирующий потоки при превышении лимита и автоматически сбрасывающий разрешения через заданный интервал.
- Простое расширение функционала: можно добавлять новые типы документов или группы, не меняя основной логики.

## Пример использования

```java
CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

CrptApi.Document document = new CrptApi.Document.DocumentBuilder()
        .setDocumentFormat(CrptApi.Document.DocumentFormat.XML)
        .setProductDocument("<xml>...</xml>")
        .setDocumentGroup(CrptApi.Document.DocumentGroup.CLOTHES)
        .setType(CrptApi.Document.DocumentType.AGGREGATION_DOCUMENT)
        .build();

CrptApi.CreateDocumentResponse response = api.createDocument(document, "подпись");
System.out.println(response);

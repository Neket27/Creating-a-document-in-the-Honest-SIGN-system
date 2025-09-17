package app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Класс для взаимодействия с API CRPT.ISMP.
 */
public class CrptApi {

    private static final String BASE_API_URL = "https://ismp.crpt.ru/api/v3";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String token = "TOKEN";

    private final RateLimiter rateLimite;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.rateLimite = new RateLimiter(timeUnit, requestLimit);
    }

    public CreateDocumentResponse createDocument(Document document, String signature) {
        Objects.requireNonNull(document, "Document cannot be null");
        Objects.requireNonNull(signature, "Signature cannot be null");

        document.validate();

        CreateDocumentRequest request = CreateDocumentRequest.builder()
                .documentFormat(document.getDocumentFormat().toString())
                .productDocument(toBase64(document.getProductDocument()))
                .productGroup(document.getDocumentGroup().getApiValue())
                .signature(toBase64(signature))
                .type(document.getType().toString())
                .build();

        try {
//          Пока лимит не исчерпан → acquire() пропускает поток сразу.
//          Когда лимит исчерпан → acquire() блокирует поток до сброса разрешений.
//          Таймер обновляет семафор, и заблокированные потоки начинают выполняться.
            rateLimite.waitForRequestPermission();

            return sendCreateRequest(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrptApiException("Request interrupted", e);
        } catch (IOException e) {
            throw new CrptApiException("JSON serialization/Deserialization error", e);
        } catch (Exception e) {
            throw new CrptApiException("An unknown error occurred while creating the document.", e);
        }
    }


    private CreateDocumentResponse sendCreateRequest(CreateDocumentRequest request) throws IOException, InterruptedException {
        Objects.requireNonNull(token, "Token cannot be null");

        String jsonBody = objectMapper.writeValueAsString(request);

        URI uri = URI.create(BASE_API_URL + "/lk/documents/create" + "?pg=" + request.getProductGroup());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));



        //TODO Заглушка через рефлексию, в vm options разрешить пакет для рефлексии: --add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED
//        Field bodyField = response.getClass().getDeclaredField("body");
//        Field statusCodeField = response.getClass().getDeclaredField("responseCode");
//        bodyField.setAccessible(true);
//        statusCodeField.setAccessible(true);
//
//        String fakeJson = """
//                {
//                  "value": "123456789",
//                  "code": "200",
//                  "error_message": "",
//                  "description": "Документ успешно создан"
//                }
//                """;
//
//        bodyField.set(response, fakeJson);
//        statusCodeField.set(response, 200);

        if (response.statusCode() == 401) {
            throw new CrptApiException("Error auth: wrong or expired token", null);
        } else if (response.statusCode() != 200) {
            throw new CrptApiException("HTTP " + response.statusCode() + ": " + response.body(), null);
        }

        return objectMapper.readValue(response.body(), CreateDocumentResponse.class);
    }

    // Utils
    public static String toBase64(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String base64Data) {
        return new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
    }


    // DTO
    public static class CreateDocumentRequest {

        @JsonProperty("document_format")
        private final String documentFormat;

        @JsonProperty("product_document")
        private final String productDocument;

        @JsonProperty("product_group")
        private final String productGroup;

        @JsonProperty("signature")
        private final String signature;

        @JsonProperty("type")
        private final String type;

        private CreateDocumentRequest(Builder builder) {
            this.documentFormat = builder.documentFormat;
            this.productDocument = builder.productDocument;
            this.productGroup = builder.productGroup;
            this.signature = builder.signature;
            this.type = builder.type;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String documentFormat;
            private String productDocument;
            private String productGroup;
            private String signature;
            private String type;

            public Builder documentFormat(String documentFormat) {
                this.documentFormat = documentFormat;
                return this;
            }

            public Builder productDocument(String productDocument) {
                this.productDocument = productDocument;
                return this;
            }

            public Builder productGroup(String productGroup) {
                this.productGroup = productGroup;
                return this;
            }

            public Builder signature(String signature) {
                this.signature = signature;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public CreateDocumentRequest build() {
                return new CreateDocumentRequest(this);
            }
        }

        public String getDocumentFormat() { return documentFormat; }
        public String getProductDocument() { return productDocument; }
        public String getProductGroup() { return productGroup; }
        public String getSignature() { return signature; }
        public String getType() { return type; }
    }

    public static class CreateDocumentResponse {

        @JsonProperty("value")
        private String value;

        @JsonProperty("code")
        private String code;

        @JsonProperty("error_message")
        private String errorMessage;

        @JsonProperty("description")
        private String description;

        public CreateDocumentResponse() {}

        public CreateDocumentResponse(String value, String code, String errorMessage, String description) {
            this.value = value;
            this.code = code;
            this.errorMessage = errorMessage;
            this.description = description;
        }

        public String getValue() { return value; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }
        public String getDescription() { return description; }

        public boolean isSuccess() {
            return "200".equals(code);
        }

        @Override
        public String toString() {
            return "CreateDocumentResponse{" +
                    "value='" + value + '\'' +
                    ", code='" + code + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    public static class Document {

        private DocumentFormat documentFormat;
        private String productDocument;
        private DocumentGroup documentGroup;
        private DocumentType type;

        public void validate() {
            if (documentFormat == null) {
                throw new IllegalArgumentException("DocumentFormat must not be null");
            }
            if (productDocument == null || productDocument.isBlank()) {
                throw new IllegalArgumentException("ProductDocument must not be null or empty");
            }
            if (documentGroup == null) {
                throw new IllegalArgumentException("DocumentGroup must not be null");
            }
            if (type == null) {
                throw new IllegalArgumentException("Type must not be null");
            }
        }

        public DocumentFormat getDocumentFormat() {
            return documentFormat;
        }

        public void setDocumentFormat(DocumentFormat documentFormat) {
            this.documentFormat = documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public void setProductDocument(String productDocument) {
            this.productDocument = productDocument;
        }

        public DocumentGroup getDocumentGroup() {
            return documentGroup;
        }

        public void setDocumentGroup(DocumentGroup documentGroup) {
            this.documentGroup = documentGroup;
        }

        public DocumentType getType() {
            return type;
        }

        public void setType(DocumentType type) {
            this.type = type;
        }

        public static class Builder {
            private final Document doc = new Document();

            public Builder setDocumentFormat(DocumentFormat format) {
                doc.setDocumentFormat(format);
                return this;
            }

            public Builder setProductDocument(String content) {
                doc.setProductDocument(content);
                return this;
            }

            public Builder setDocumentGroup(DocumentGroup group) {
                doc.setDocumentGroup(group);
                return this;
            }

            public Builder setType(DocumentType type) {
                doc.setType(type);
                return this;
            }

            public Document build() {
                return doc;
            }
        }

        public enum DocumentFormat {
            MANUAL, XML, CSV
        }

        public enum DocumentGroup {
            CLOTHES, SHOES, TOBACCO, PERFUMERY, TIRES, ELECTRONICS, PHARMA, MILK, BICYCLE, WHEELCHAIRS;

            public String getApiValue() {
                return this.name().toLowerCase();
            }
        }

        public enum DocumentType {
            AGGREGATION_DOCUMENT,
            AGGREGATION_DOCUMENT_CSV,
            AGGREGATION_DOCUMENT_XML,
            DISAGGREGATION_DOCUMENT,
            DISAGGREGATION_DOCUMENT_CSV,
            DISAGGREGATION_DOCUMENT_XML,
            REAGGREGATION_DOCUMENT,
            REAGGREGATION_DOCUMENT_CSV,
            REAGGREGATION_DOCUMENT_XML,
            LP_INTRODUCE_GOODS,
            LP_SHIP_GOODS,
            LP_SHIP_GOODS_CSV,
            LP_SHIP_GOODS_XML,
            LP_ACCEPT_GOODS,
            LP_ACCEPT_GOODS_XML,
            LK_REMARK,
            LK_REMARK_CSV,
            LK_REMARK_XML,
            LK_RECEIPT,
            LK_RECEIPT_XML,
            LK_RECEIPT_CSV,
            LP_GOODS_IMPORT,
            LP_GOODS_IMPORT_CSV,
            LP_GOODS_IMPORT_XML,
            LP_CANCEL_SHIPMENT,
            LP_CANCEL_SHIPMENT_CSV,
            LP_CANCEL_SHIPMENT_XML,
            LK_KM_CANCELLATION,
            LK_KM_CANCELLATION_CSV,
            LK_KM_CANCELLATION_XML,
            LK_APPLIED_KM_CANCELLATION,
            LK_APPLIED_KM_CANCELLATION_CSV,
            LK_APPLIED_KM_CANCELLATION_XML,
            LK_CONTRACT_COMMISSIONING,
            LK_CONTRACT_COMMISSIONING_CSV,
            LK_CONTRACT_COMMISSIONING_XML,
            LK_INDI_COMMISSIONING,
            LK_INDI_COMMISSIONING_CSV,
            LK_INDI_COMMISSIONING_XML,
            LP_SHIP_RECEIPT,
            LP_SHIP_RECEIPT_CSV,
            LP_SHIP_RECEIPT_XML,
            OST_DESCRIPTION,
            OST_DESCRIPTION_CSV,
            OST_DESCRIPTION_XML,
            CROSSBORDER,
            CROSSBORDER_CSV,
            CROSSBORDER_XML,
            LP_INTRODUCE_OST,
            LP_INTRODUCE_OST_CSV,
            LP_INTRODUCE_OST_XML,
            LP_RETURN,
            LP_RETURN_CSV,
            LP_RETURN_XML,
            LP_SHIP_GOODS_CROSSBORDER,
            LP_SHIP_GOODS_CROSSBORDER_CSV,
            LP_SHIP_GOODS_CROSSBORDER_XML,
            LP_CANCEL_SHIPMENT_CROSSBORDER
        }
    }


    // Rate Limiter
    public static class RateLimiter {

        private final Semaphore semaphore;
        private final long intervalMillis;

//        public RateLimiter(TimeUnit timeUnit, int requestLimit) {
//            this.semaphore = new Semaphore(requestLimit, true);
//            this.intervalMillis = timeUnit.toMillis(1);
//
//            // Поток-сброс семафора
//            Thread refillThread = new Thread(() -> {
//                while (true) {
//                    try {
//                        Thread.sleep(intervalMillis);
//                        synchronized (semaphore) {
//                            int permitsToRelease = requestLimit - semaphore.availablePermits();
//                            if (permitsToRelease > 0) {
//                                semaphore.release(permitsToRelease);
//                            }
//                        }
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        break;
//                    }
//                }
//            });
//            refillThread.setDaemon(true);
//            refillThread.start();
//        }

        public RateLimiter(TimeUnit timeUnit, int requestLimit) {
            this.semaphore = new Semaphore(requestLimit, true);
            this.intervalMillis = timeUnit.toMillis(1);

            Thread refillThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(intervalMillis);
                        // Сброс семафора: всегда восстанавливаем полный лимит
                        semaphore.release(requestLimit);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            refillThread.setDaemon(true);
            refillThread.start();
        }


        public void waitForRequestPermission() throws InterruptedException {
            semaphore.acquire();
        }
    }

    // Exception
    public static class CrptApiException extends RuntimeException {
        public CrptApiException(String message, Throwable cause) { super(message, cause); }
        public CrptApiException(String message) { super(message); }
    }

}

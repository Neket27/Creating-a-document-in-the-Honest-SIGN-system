package app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
    CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 3);

        CrptApi.Document doc = new CrptApi.Document.DocumentBuilder()
                .setDocumentFormat(CrptApi.Document.DocumentFormat.MANUAL)
                .setProductDocument("{\"goods\": [{\"gtin\":\"46070123456789\", \"serialNumber\":\"SN123\"}]}")
                .setDocumentGroup(CrptApi.Document.DocumentGroup.MILK)
                .setType(CrptApi.Document.DocumentType.LP_INTRODUCE_GOODS)
                .build();

        String signature = "your-p7s-signature-content";

        Instant now = Instant.now();
        while (true) {
            CrptApi.CreateDocumentResponse document = crptApi.createDocument(doc, signature);
            Instant time = now.plusSeconds(Instant.now().getEpochSecond());
            System.out.println(document.toString()+" | "+ time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//            System.out.println(" ");
//            System.out.println("MAIN");
        }



    }
}
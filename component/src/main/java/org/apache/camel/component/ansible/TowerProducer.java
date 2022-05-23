package org.apache.camel.component.ansible;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *
 */
public class TowerProducer implements Producer {

    private final TowerEndpoint endpoint;
    private final String remaining;
    private final String template;
    private final String basicAuth;

    public TowerProducer(TowerEndpoint endpoint, String remaining, String template, String basicAuth) {
        this.endpoint = endpoint;
        this.remaining = remaining;
        this.template = template;
        this.basicAuth = basicAuth;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public boolean isSingleton() {
        return true;  // TODO: Customise this generated block
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // This is where the fun happens

        String host = remaining;
        System.out.println("==> Using host " + host);

        Object body1 =  exchange.getIn().getBody();

        JsonObject bodyAsCloudEvent;

        if (body1 instanceof String) {
            String tmp = (String) body1;
            bodyAsCloudEvent = Jsoner.deserialize(tmp, new JsonObject());
        }
        else if (body1 instanceof Message) {
            String tmp  = (String) ((Message)body1).getBody();
            bodyAsCloudEvent = Jsoner.deserialize(tmp, new JsonObject());
        }
        else if (body1 instanceof JsonObject) {
            bodyAsCloudEvent = (JsonObject) body1;
        }
        else {
            throw new IllegalStateException("No suitable body given " + body1);
        }

        X509TrustManager trustAllCerts = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }
            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };


        // TODO make configurable
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());

        JsonObject ceData = (JsonObject) bodyAsCloudEvent.get("data");

        String basicAuthHeader = "Basic " + basicAuth;

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.of(1, ChronoUnit.SECONDS))
                .build();

        String templatePayload = createPayload(ceData);

        System.out.println("Payload is now " + templatePayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + "/api/v2/job_templates/" + template + "/launch/"))
                .header("Authorization", basicAuthHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(templatePayload))
                .build();

        HttpResponse<String> response = null;

        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
            exchange.setRollbackOnly(true);
            exchange.setException(e);
            Map<String,Object> outcome = new HashMap<>();
            outcome.put("success", false);
            outcome.put("status", "Fail");
            outcome.put("message", e.getMessage());
            outcome.put("template", template);
            JsonObject jo = new JsonObject(outcome);
            exchange.getIn().setBody(jo.toJson());
        }

        if (response != null) {
            if (response.statusCode() == 201) { // created
                // slurp the body and obtain the created job
                // We could also just read the location header and extract the id from there.
                String body = response.body();
                JsonObject json = Jsoner.deserialize(body, new JsonObject());
                int jobId = json.getInteger("job");

                Optional<String> oJobUrl = response.headers().firstValue("Location");
                String jobUrl = oJobUrl.orElse("/api/v2/jobs/" + jobId + "/");

                JobStatus status = getJobOutcome(client, host, jobUrl, basicAuthHeader);

                Map<String, Object> outcome = new HashMap<>(status.asMap());
                outcome.put("template", template);
                outcome.put("job", String.valueOf(jobId));
                JsonObject jo = new JsonObject(outcome);
                exchange.getIn().setBody(jo.toJson());

            } else if (response.statusCode() / 100 == 4) {
                // We could flag to retry
                exchange.setException(new IOException("Call returned code " + response.statusCode() + " and : " + response.body()));
            } else {
                exchange.setException(new IllegalStateException("Unknown return code " + response.statusCode()));
            }
        }
    }

     static String createPayload(JsonObject ceData) {
        JsonObject payloadData = new JsonObject();
        payloadData.put("extra_vars", (ceData.toJson()));
        String templatePayload = payloadData.toJson();
        return templatePayload;
    }

    @Override
    public void start() {
        // TODO don't try this at home kids :)  Seriously: this should not be enabled by default
        System.getProperties().setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

    }

    @Override
    public void stop() {

    }



    private JobStatus getJobOutcome(HttpClient client, String host, String jobUrl, String basicAuthHeader) throws Exception {

        int count = 0;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + jobUrl))
                .header("Authorization", basicAuthHeader)
                .GET()
                .build();

        while (true) { // We wait until we get a result
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 ) {

                String body = response.body();
                JsonObject json = Jsoner.deserialize(body, new JsonObject());
                String fNode =  json.getString("finished");
                String statusNode = json.getString("status");
                System.out.printf("==> current job status for job %s is >%s<%n" , jobUrl, statusNode);
                if (fNode!=null && !fNode.isEmpty()) {
                    boolean isFail = json.getBoolean("failed");

                    String explanation = json.getString("job_explanation");
                    JobStatus js = new JobStatus(!isFail, statusNode, explanation);
                    return js;
                }
            }

            count++;
            Thread.sleep(150L * (23+count)); // Wait a bit, TODO make configurable, exp backoff?
        }
    }

}

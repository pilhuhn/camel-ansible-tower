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

        String theBody;

        if (body1 instanceof String) {
            theBody = (String) body1;
        }
        else if (body1 instanceof Message) {
            theBody = (String) ((Message)body1).getBody();
        }
        else if (body1 instanceof JsonObject) {
            theBody = ((JsonObject)body1).toJson();
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


        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());


        String basicAuthHeader = "Basic " + basicAuth;

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.of(1, ChronoUnit.SECONDS))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + "/api/v2/job_templates/" + template + "/launch/"))
                .header("Authorization", basicAuthHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(theBody))
                .build();

        HttpResponse<String> response = null;

        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();  // TODO: Customise this generated block
            exchange.setRollbackOnly(true);
            exchange.setException(e);
            Map<String,String> outcome = new HashMap<>();
            outcome.put("status", "Fail");
            outcome.put("message", e.getMessage());
            org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject(outcome);
            exchange.getIn().setBody(jo.toJson());
        }

        if (response != null) {
            if (response.statusCode() == 201) { // created
                // slurp the body and obtain the created job
                // We could also just read the location header and extract the id from there.
                String body = response.body();
                JsonObject json = Jsoner.deserialize(body, new JsonObject());
                int jobId = json.getInteger("job"); // Probably json.getInteger()

                Optional<String> oJobUrl = response.headers().firstValue("Location");
                String jobUrl = oJobUrl.orElse("/api/v2/job/" + jobId + "/");

                JobStatus status = getJobOutcome(client, host, jobUrl, basicAuth);

                Map<String, String> outcome = new HashMap<>();
                if (status.status == JobStatus.Status.OK) {
                    outcome.put("status", "Success");
                } else {
                    outcome.put("status", "Fail");
                }
                outcome.put("template", template);
                outcome.put("job", String.valueOf(jobId));
                org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject(outcome);
                exchange.getIn().setBody(jo.toJson());

            } else if (response.statusCode() / 100 == 4) {
                // We could flag to retry
                exchange.setException(new IOException("Call returned code " + response.statusCode() + " and : " + response.body()));
            } else {
                exchange.setException(new IllegalStateException("Unknown return code " + response.statusCode()));
            }
        }
    }

    @Override
    public void start() {
        // TODO don't try this at home kids :)  Seriously: this should not be enabled by default
        System.getProperties().setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

    }

    @Override
    public void stop() {

    }


    /**
     * Populate the extra_vars for Tower. this is a json object with a nested
     *  object 'extra_vars'.
     *  The inner variables need to be a string in json, with proper escaping
     *  E.g. <pre>"extra_vars": "{\"a\":2,\"b\":\"hello\",\"c\":\"lilalu\"}"</pre>.
     * @param bodyMap Body of the incoming payload
     * @return A string of json of the vars object.
     */
    private String fillExtraVars(Map<String, Object> bodyMap) {
        Map<String, String> outer = new HashMap<>(1);
        Map<String, String> extras = new HashMap<>();
        Map<String, Object> payload = (Map<String, Object>) bodyMap.get("payload");

        copy(extras, payload, "bundle");
        copy(extras, payload, "application");
        copy(extras, payload, "event_type");
        copy(extras, payload, "account_id");

        // TODO copy events[]->payload[]

        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject(extras);
        outer.put("extra_vars", jo.toJson());
        jo = new org.apache.camel.util.json.JsonObject(outer);
        return jo.toJson();
    }

    private void copy(Map<String, String> to, Map<String, Object> from, String what) {
        if (from == null) {
            to.put(what,"-no payload map provided-");
            return;
        }
        String val = (String) from.getOrDefault(what, "-unset-");
        to.put(what, val);
    }

    private JobStatus getJobOutcome(HttpClient client, String host, String jobUrl, String userPass) throws Exception {

        int count = 0;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + jobUrl))
                .header("Authorization", userPass)
                .GET()
                .build();

        while (count < 10) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 ) {

                String body = response.body();
                JsonObject json = Jsoner.deserialize(body, new JsonObject());
                String fNode =  json.getString("finished");
                if (!fNode.isEmpty()) {
                    boolean isFail = json.getBoolean("failed");
                    String statusNode = json.getString("status");
                    JobStatus js = new JobStatus(!isFail, statusNode);
                    return js;
                }
            }

            count++;
            Thread.sleep(150L * (19+count)); // Wait a bit, TODO make configurable, exp backoff?
        }

        return new JobStatus(false, "Did not get a reply in time");
    }

}

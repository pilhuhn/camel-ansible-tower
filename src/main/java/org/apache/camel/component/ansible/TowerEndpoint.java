package org.apache.camel.component.ansible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultEndpoint;

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
 * Component that interacts with Ansible Tower
 */
@ManagedResource(description = "Managed Ansible Tower endpoint")
@UriEndpoint(scheme = "tower", title = "Ansible Tower", syntax = "tower:address[?options]",
            firstVersion = "0.0.1",
            category = Category.MANAGEMENT)
public class TowerEndpoint extends DefaultEndpoint implements Endpoint {
    @UriPath
    @Metadata(required = true, description = "The (IP) address of the tower instance to talk to")
    private final String uri;
    @UriParam( description = "BasicAuth")
    private String basicAuth;

    private final String remaining;
    private final Map<String, Object> parameters;
    private CamelContext context;
    private int option;

    public TowerEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        this.uri = uri;
        this.remaining = remaining;
        this.parameters = parameters;
        this.basicAuth = (String) parameters.get("basicAuth");
    }

    @Override
    public String getEndpointUri() {
        return uri;
    }

    @Override
    public String getEndpointKey() {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public Exchange createExchange() {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public void configureExchange(Exchange exchange) {
        // TODO: Customise this generated block
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public Producer createProducer() throws Exception {

        Producer p = new Producer() {

            ObjectMapper mapper;

            @Override
            public Endpoint getEndpoint() {
                return TowerEndpoint.this;
            }

            @Override
            public boolean isSingleton() {
                return true;  // TODO: Customise this generated block
            }

            @Override
            public void process(Exchange exchange) throws Exception {
                // This is where the fun happens

                String host = remaining;

                String template = (String) parameters.get("template");

                if (template==null || template.isBlank()) {
                    // No parameter? Check the header
                    Object tmp = exchange.getIn().getHeader("template");
                    if (tmp != null) {
                        template = String.valueOf(tmp);
                    }
                }
                if (template==null || template.isBlank()) {
                    Map<String, Object> bodyMap = (Map<String, Object>) exchange.getIn().getBody();
                    Map<String, Object> metaMap = (Map<String, Object>) bodyMap.get("meta");
                    String extras = (String) metaMap.get("extras");

                    JsonObject jo = new JsonObject(extras);
                    String tmpl = jo.getString("template");

                    template = tmpl;
                }
                if (template == null || template.isBlank()) {
                    throw new IllegalArgumentException("No template passed");
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



                String basicAuth = "Basic " + getBasicAuth();

                HttpClient client = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .connectTimeout(Duration.of(1, ChronoUnit.SECONDS))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://" + host + "/api/v2/job_templates/" + template + "/launch/"))
                        .header("Authorization", basicAuth)
                        .POST(HttpRequest.BodyPublishers.ofString(""))
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
                    outcome.put("template", template);
                    outcome.put("message", e.getMessage());
                    org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject(outcome);
                    exchange.getIn().setBody(jo.toJson());
                }

                if (response != null) {
                    if (response.statusCode() == 201) { // created
                        // slurp the body and obtain the created job
                        // We could also just read the location header and extract the id from there.
                        String body = response.body();
                        JsonNode json = mapper.readTree(body);
                        JsonNode job = json.get("job");
                        int jobId = job.asInt();

                        Optional<String> oJobUrl = response.headers().firstValue("Location");
                        String jobUrl = oJobUrl.orElse("/api/v2/job/" + jobId + "/");

                        JobStatus status = getJobOutcome(client, host, jobUrl, basicAuth, mapper);

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
                        exchange.setException(new IOException("Call returned code " + response.statusCode()));
                    } else {
                        exchange.setException(new IllegalStateException("Unknown return code " + response.statusCode()));
                    }
                }
            }

            @Override
            public void start() {
                // TODO don't try this at home kids :)  Seriously: this should not be enabled by default
                System.getProperties().setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

                mapper = new ObjectMapper();
            }

            @Override
            public void stop() {

            }
        };

        return p;

    }

    private JobStatus getJobOutcome(HttpClient client, String host, String jobUrl, String userPass, ObjectMapper mapper) throws Exception {

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
                JsonNode json = mapper.readTree(body);
                JsonNode fNode = json.get("finished");
                if (!fNode.isNull()) {
                    JsonNode failNode = json.get("failed");
                    boolean isFail = failNode.asBoolean();
                    JsonNode statusNode = json.get("status");
                    JobStatus js = new JobStatus(!isFail, statusNode.asText());
                    return js;
                }
            }

            count++;
            Thread.sleep(150L * (19+count)); // Wait a bit, TODO make configurable, exp backoff?
        }

        return new JobStatus(false, "Did not get a reply in time");
    }

    @Override
    public AsyncProducer createAsyncProducer() throws Exception {
        return AsyncProcessorConverterHelper.convert(createProducer());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        // TODO: Customise this generated block
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

    @Override
    public boolean isLenientProperties() {
        return true; // Should perhaps be set to false later to verify params.
    }

    @Override
    public boolean isSingleton() {
        return false;  // TODO: Customise this generated block
    }

    @Override
    public void start() {
        // TODO: Customise this generated block
    }

    @Override
    public void stop() {
        // TODO: Customise this generated block
    }

    public <T> void setOption(int option) {
        this.option = option;
    }

    public int getOption() {
        return option;
    }

    public String getBasicAuth() {
        return basicAuth;
    }

    public void setBasicAuth(String basicAuth) {
        this.basicAuth = basicAuth;
    }
}

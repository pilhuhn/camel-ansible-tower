package org.apache.camel.component.ansible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class TowerComponentTest extends CamelTestSupport {

    private static int serverPort;
    private static ClientAndServer cas;;

    @BeforeClass
    public static void startMockServer() throws IOException {
        serverPort = AvailablePortFinder.getNextAvailable();

        cas = ClientAndServer.startClientAndServer(serverPort);


        cas.when(request()
                        .withPath("/api/v2/job_templates/7/launch/")
                        .withMethod("POST")
        ).respond(response(fileBody("tower_return1.json"))
                .withStatusCode(201)
                .withHeader("Location","/api/v2/job/20/")
        );
        cas.when(request()
                .withMethod("GET")
        ).respond(response(fileBody("tower_return2.json"))
                .withStatusCode(200));


    }

    @After
    public void stopMockServer()  {
        // Helper to debug mock server issues
//        System.err.println(cas.retrieveLogMessages(request()));
//        System.err.println(cas.retrieveRecordedRequests(request()));

        cas.stop();
    }

    @Test
    public void testTower() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        // Trigger events to subscribers
        mock.await(10, TimeUnit.SECONDS);
        // See if we got something back
        mock.assertIsSatisfied();
        String jsonBody = (String) mock.getReceivedExchanges().get(0).getIn().getBody();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(jsonBody);
        assertEquals("7", json.get("template").asText());
        assertEquals("20", json.get("job").asText());
    }

    @Override
    protected RouteBuilder createRouteBuilder()  {

        Map<String,Object> metaMap = new HashMap<>();
        metaMap.put("extras", "{\"template\":\"20\"}");

        Map<String,Object> body = new HashMap<>();
        body.put("meta", metaMap);

        Map<String,Object> payloadMap = new HashMap<>();
        payloadMap.put("application","policies");
        payloadMap.put("bundle","rhel");
        payloadMap.put("event_type","et1");

        body.put("payload",payloadMap);

        JsonObject o = JsonObject.mapFrom(body);

        BeanExpression se = new BeanExpression(new BodyBuilder(body),"get");

        return new RouteBuilder() {
            public void configure() {
                from("timer:bla?delay=-1&repeatCount=1")
                        .setBody(se)
                        .setHeader("basicAuth", new SimpleExpression("bla-bla"))
                        .setHeader("targetUrl", new SimpleExpression("https://localhost:12345"))
                        .setHeader("template", new SimpleExpression("20"))
                  .to("tower://localhost:" + serverPort + "?basicAuth=bla-bla&template=7")
                  .to("mock:result");
            }
        };
    }

    private static String fileBody(String fileName) throws IOException {
        String fName = "/" + fileName;
        try (InputStreamReader isr = new InputStreamReader(TowerComponentTest.class.getResourceAsStream(fName)) ) {
            char[] target = new char[8192];
            int numRead = isr.read(target);
            return String.valueOf(target,0,numRead);
        }
    }


    private class BodyBuilder {

        private final Map<String, Object> body  ;

        public BodyBuilder(Map<String, Object> body) {
            this.body = body;
        }

        public Map<String, Object> get() {
            return this.body;
        }
    }
}

package org.apache.camel.component.ansible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testTower() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Map<String,Object> payloadMap = new HashMap<>();
        payloadMap.put("application","policies");
        payloadMap.put("bundle","rhel");
        payloadMap.put("event_type","et1");

        JsonObject ce = new JsonObject();
        ce.put("data", payloadMap);
        ce.put("id", UUID.randomUUID().toString());
        ce.put("type", "my.type");


        template.sendBody(ce.toJson());

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


        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                  .to("tower://localhost:" + serverPort + "?basicAuth=bla-bla&template=7")
                  .to("mock:result");
            }
        };
    }

    private static String fileBody(String fileName) throws IOException {
        String fName =  fileName;
        try (InputStreamReader isr = new InputStreamReader(TowerComponentTest.class.getResourceAsStream(fName)) ) {
            char[] target = new char[8192];
            int numRead = isr.read(target);
            return String.valueOf(target,0,numRead);
        }
    }

}

package org.apache.camel.component.ansible;

import io.vertx.core.json.JsonObject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TowerComponentTest extends CamelTestSupport {


    @Test
    public void testTower() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        // Trigger events to subscribers

        mock.await(1, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        Map<String,Object> metaMap = new HashMap<>();
        metaMap.put("extras", "{\"template\":\"20\"}");

        Map<String,Object> body = new HashMap<>();
        body.put("meta", metaMap);

        JsonObject o = JsonObject.mapFrom(body);

        BeanExpression se = new BeanExpression(new BodyBuilder(body),"get");

        return new RouteBuilder() {
            public void configure() {
                from("timer:bla?delay=-1&repeatCount=1")
                        .setBody(se)
                        .setHeader("basicAuth", new SimpleExpression("bla-bla"))
                        .setHeader("targetUrl", new SimpleExpression("https://localhost:12345"))
                  .to("tower://localhost:12345?basicAuth=bla-bla")
                  .to("mock:result");
            }
        };
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

// camel-K: mvn:com.github.pilhuhn:camel-ansible-tower:16b26edbd5

package de.bsd.camel.tower;

import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class Main extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("kafka:{{component.topic}}")
                .log("Received ${body}")
                .toD("tower:{{tower.host}}?template={{tower.template}}&basicAuth={{tower.basicAuth}}")
                .log("Outcome was ${body}")
        ;
    }
}



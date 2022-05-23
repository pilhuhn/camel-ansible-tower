// camel-k: language=java
//    Using the github: dependency in the next line builds the dep on the fly with jitpack.io
// camel-k: dependency=github:pilhuhn:camel-ansible-tower:648193e
// camel-k: property=component.topic=mytopic

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



package org.apache.camel.component.ansible;



import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 */
public class VarEncodingTest extends CamelTestSupport {

    // Take in a CloudEvent , get the data and encode it as extra_vars
    // for launching a template.
    @Test
    public void testExtraVars() throws Exception {

        String cloudEvent = fileBody("ce1.json");
        String result = fileBody("ce1_result.json").trim();

        JsonObject ce = Jsoner.deserialize(cloudEvent, new JsonObject());
        JsonObject data = (JsonObject) ce.get("data");


        String payload = TowerProducer.createPayload(data);

        Assertions.assertEquals(result, payload);

    }


    private static String fileBody(String fileName) throws IOException {
        String fName =  fileName;
        try (InputStreamReader isr = new InputStreamReader(VarEncodingTest.class.getResourceAsStream(fName)) ) {
            char[] target = new char[8192];
            int numRead = isr.read(target);
            return String.valueOf(target,0,numRead);
        }
    }
}

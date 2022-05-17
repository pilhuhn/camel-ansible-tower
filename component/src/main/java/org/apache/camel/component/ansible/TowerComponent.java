package org.apache.camel.component.ansible;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

/**
 * The tower component is for interacting with Ansible Tower
 */
@org.apache.camel.spi.annotations.Component("tower")
public class TowerComponent extends DefaultComponent  {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        System.out.println("component, create endpoint " + remaining);

        TowerEndpoint towerEndpoint = new TowerEndpoint(uri, remaining, parameters);
        return towerEndpoint;

    }

}

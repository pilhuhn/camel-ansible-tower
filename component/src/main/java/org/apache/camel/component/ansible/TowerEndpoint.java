package org.apache.camel.component.ansible;

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
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultEndpoint;

import java.util.Map;


/**
 * Component that interacts with Ansible Tower
 */
@ManagedResource(description = "Managed Ansible Tower endpoint")
@UriEndpoint(scheme = "tower", title = "Ansible Tower", syntax = "tower:address[?options]",
            firstVersion = "0.0.1",
            category = Category.MANAGEMENT)
@Configurer
public class TowerEndpoint extends DefaultEndpoint implements Endpoint {

    @UriPath
    @Metadata(required = true, description = "The (IP) address of the tower instance to talk to")
    private final String uri;

    @UriParam(name = "basicAuth", description = "BasicAuth to authenticate against the Tower instance")
    private String basicAuth;

    @Metadata(required = true)
    @UriParam(name = "template", description = "The id of the template to trigger")
    private String template;

    private final String remaining;
    private CamelContext context;
    private int option;

    public TowerEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        this.uri = uri;
        this.remaining = remaining;
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

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public Producer createProducer() throws Exception {

        Producer p = new TowerProducer(TowerEndpoint.this, remaining, template, basicAuth);
        System.out.println(p);
        return p;

    }


    @Override
    public AsyncProducer createAsyncProducer() throws Exception {
        return AsyncProcessorConverterHelper.convert(createProducer());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null; // We only produce
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return null;  // We only produce
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            if (entry.getKey().equals("template")) {
                setTemplate((String) entry.getValue());
            }
            if (entry.getKey().equals("basicAuth")) {
                setBasicAuth((String) entry.getValue());
            }
        }

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

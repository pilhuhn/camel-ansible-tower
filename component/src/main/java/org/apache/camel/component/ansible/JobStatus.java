package org.apache.camel.component.ansible;

import org.apache.camel.util.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class JobStatus {

    String status;
    boolean success;
    private String explanation;

    public JobStatus(boolean wasSuccess, String status, String explanation) {
        this.explanation = explanation;
        this.status = status;
        this.success = wasSuccess;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("status", status);
        result.put("explanation", explanation);
        return result;
    }

    boolean isSuccess() {
        return success;
    }
}

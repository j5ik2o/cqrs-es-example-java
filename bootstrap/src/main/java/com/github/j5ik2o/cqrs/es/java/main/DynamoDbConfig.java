package com.github.j5ik2o.cqrs.es.java.main;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DynamoDbConfig {
    private String region;

    private String endpointUrl;

    private String accessKey;

    private String secretAccessKey;

    public boolean hasConfig() {
        return region != null && endpointUrl != null && accessKey != null && secretAccessKey != null;
    }
}

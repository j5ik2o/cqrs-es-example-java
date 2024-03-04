package com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql;

import org.springframework.stereotype.Component;

@Component
public class QueryResolverImpl implements QueryResolver {
    @Override
    public String healthCheck() throws Exception {
        return "OK";
    }
}

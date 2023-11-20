package com.github.andreatp.kiota.serialization.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonMapper {

    private JsonMapper() {}

    // We use a public mapper so that ppl can hack it around if needed.
    public final static ObjectMapper mapper = new ObjectMapper();
}

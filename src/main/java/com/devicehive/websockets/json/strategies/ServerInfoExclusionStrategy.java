package com.devicehive.websockets.json.strategies;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: jkulagina
 * Date: 19.06.13
 * Time: 16:33
 */
public class ServerInfoExclusionStrategy implements ExclusionStrategy {
    private static final Set<String> FIELD_NAMES_TO_INCLUDE = new HashSet<String>(){
        {
            add("apiVersion");
            add("serverTimestamp");
            add("webSocketServerUrl");
        }
    };

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
        return !FIELD_NAMES_TO_INCLUDE.contains(fieldAttributes.getName());
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
        return false;
    }
}

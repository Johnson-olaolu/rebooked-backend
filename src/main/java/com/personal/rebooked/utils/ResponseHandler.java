package com.personal.rebooked.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResponseHandler {
    public static ResponseEntity
            <Object> generateResponse(Object responseObj, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("status", HttpStatus.OK.value() );
        map.put("data", responseObj);

        return new ResponseEntity<Object>(map, HttpStatus.OK);
    }

    public static ResponseEntity
            <Object> generateResponse( Object responseObj, String message, HttpStatus status) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("status", status.value() );
        map.put("data", responseObj);

        return new ResponseEntity<Object>(map, status);
    }

    public static Map<String, Object> errorBody(HttpStatus status, String message, Map<String, String> errors) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status.value());
        map.put("message", message);
        if (errors != null && !errors.isEmpty()) {
            map.put("errors", errors);
        }
        return map;
    }

    public static ResponseEntity<Object> generateError(HttpStatus status, String message, Map<String, String> errors) {
        return new ResponseEntity<>(errorBody(status, message, errors), status);
    }

}

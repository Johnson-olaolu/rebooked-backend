package com.personal.rebooked.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
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

}

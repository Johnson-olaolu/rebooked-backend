package com.personal.rebooked.service;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder-name}")
    private String folderName;

    public Map<String, Object> upload (MultipartFile file){
       try {
           Map<String, Object> options = new HashMap<>();
           options.put("folder", folderName);
           return cloudinary.uploader().upload(file.getBytes(), options );
       } catch (Exception e) {
           throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
       }
    }

    public  boolean delete (String cloudinaryPublicId){
        try {
            Map result = cloudinary.uploader().destroy(cloudinaryPublicId, new HashMap());
            return "ok".equals(result.get("result"));
        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

    }
}

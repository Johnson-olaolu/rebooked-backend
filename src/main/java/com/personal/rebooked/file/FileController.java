package com.personal.rebooked.file;

import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    @GetMapping("/{id}")
    public ResponseEntity<Object> getFile(@PathVariable String id) {
        File file = fileService.getFile(id);
        return ResponseHandler.generateResponse(file, "File fetched successfully");
    }

    @PostMapping("")
    public ResponseEntity<Object> uploadFile(@RequestParam("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        if (file.isEmpty()) {
            throw  new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        File uploadedFile = fileService.upload(file, currentUser);
        return ResponseHandler.generateResponse(uploadedFile, "File successfully uploaded");
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<Object> getUserFiles(@PathVariable String id) {
        List<File> files = fileService.getUserFiles(id);
        return ResponseHandler.generateResponse(files, "File fetched successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteFile(@PathVariable String id) {
        fileService.deleteFile(id);
        return ResponseHandler.generateResponse(true, "File deleted successfully");
    }
}

package com.personal.rebooked.file;

import com.personal.rebooked.file.models.File;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.ResponseHandler;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/file")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;

    @GetMapping("/{id}")
    public ResponseEntity<Object> getFile(@PathVariable String id) {
        File file = fileService.getFileById(id);
        return ResponseHandler.generateResponse(file, "File fetched successfully");
    }

    @PostMapping("")
    public ResponseEntity<Object> uploadFile(@RequestParam(value = "file", required = false) MultipartFile file) {
        User currentUser = CurrentUser.require();
        FileService.validateImage(file);
        File uploadedFile = fileService.upload(file, currentUser);
        return ResponseHandler.generateResponse(uploadedFile, "File successfully uploaded");
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<Object> getUserFiles(@PathVariable String id) {
        List<File> files = fileService.getUserFiles(id);
        return ResponseHandler.generateResponse(files, "File fetched successfully");
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Object> updateFile(@PathVariable String id, @RequestParam(value = "file", required = false) MultipartFile file) {
        User currentUser = CurrentUser.require();
        FileService.validateImage(file);
        File uploadedFile = fileService.updateFile(id, file, currentUser);
        return ResponseHandler.generateResponse(uploadedFile, "File successfully updated");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteFile(@PathVariable String id) {
        fileService.deleteOwnedFile(id, CurrentUser.require());
        return ResponseHandler.generateResponse(true, "File deleted successfully");
    }
}

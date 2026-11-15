package com.personal.rebooked.file;

import com.personal.rebooked.file.models.File;
import com.personal.rebooked.file.respositories.FileRepository;
import com.personal.rebooked.service.CloudinaryService;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Misc;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class FileService {

    private final FileRepository fileRepository;
    private final CloudinaryService cloudinaryService;


    public File upload(MultipartFile file, User user) {
        Map<String, Object> metadata = cloudinaryService.upload(file);
        File newFile = new File();
        newFile.setSize(Misc.getStringSizeLengthFile(file.getSize()));
        newFile.setName(file.getOriginalFilename());
        newFile.setMimetype(file.getContentType());
        newFile.setUrl((String) metadata.get("secure_url"));
        newFile.setCloudinaryId((String) metadata.get("public_id"));
        newFile.setUser(user);
        return fileRepository.save(newFile);
    }

    public File getFileById(String fileId) {
        return fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("File with id %s not found", fileId)));
    }

    public List<File> getUserFiles(String userId) {
        return fileRepository.findFilesByUserId(userId);
    }

    /** True if the file was uploaded by the given user. */
    public static boolean isOwnedBy(File file, String userId) {
        return file != null && file.getUser() != null && userId != null && userId.equals(file.getUser().getId());
    }

    private static void requireOwner(File file, User caller) {
        if (caller == null || !isOwnedBy(file, caller.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only change your own files");
        }
    }

    /** Accept images only; anything else is a 400 on the "file" field. */
    public static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("file", "Choose a photo to upload"));
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!Constants.ALLOWED_IMAGE_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("file", "Photos must be JPEG, PNG, WebP or HEIC"));
        }
    }

    public File updateFile(String id, MultipartFile file, User user) {
        File fileData = getFileById(id);
        requireOwner(fileData, user);
        cloudinaryService.delete(fileData.getCloudinaryId());
        Map<String, Object> metadata = cloudinaryService.upload(file);
        fileData.setSize(Misc.getStringSizeLengthFile(file.getSize()));
        fileData.setName(file.getOriginalFilename());
        fileData.setMimetype(file.getContentType());
        fileData.setUrl((String) metadata.get("secure_url"));
        fileData.setCloudinaryId((String) metadata.get("public_id"));
        fileData.setUser(user);

        return fileRepository.save(fileData);
    }

    public List<File> getAllFiles() {
        return fileRepository.findAll();
    }

    /** DELETE /file/{id}: only the uploader may delete. */
    public void deleteOwnedFile(String fileId, User caller) {
        requireOwner(getFileById(fileId), caller);
        deleteFile(fileId);
    }

    /** Internal: no ownership check (callers must have checked). */
    public void deleteFile(String fileId) {
        File file = getFileById(fileId);
        cloudinaryService.delete(file.getCloudinaryId());
        fileRepository.deleteById(fileId);
    }
}

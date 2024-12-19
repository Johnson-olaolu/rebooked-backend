package com.personal.rebooked.utils;

import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.role.dto.CreateRoleDTO;
import java.util.List;


public class Constants {

   public static List< CreateRoleDTO> userRoleList = List.of(
            new CreateRoleDTO("user", "USER"),
            new CreateRoleDTO("admin", "ADMIN"),
            new CreateRoleDTO("superAdmin", "SUPER ADMIN")
    );

   public static CreateUserDto superAdminDetails = new CreateUserDto(
           "johnsonolaolu@gmail.com",
           "Super Admin",
           "Admin_123",
           true,
           "superAdmin"
    );

   public enum RegistrationType {
       GOOGLE,
       FACEBOOK,
       EMAIL
   }
}

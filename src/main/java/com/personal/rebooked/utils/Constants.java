package com.personal.rebooked.utils;

import com.personal.rebooked.category.dto.CreateCategoryDTO;
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
           "super_admin@rebooked.com",
           "Super Admin",
           "Admin_123",
           true,
           "superAdmin",
           null
    );

    public static List<CreateCategoryDTO> categoryList = List.of(
            new CreateCategoryDTO("Classic Literature"),
            new CreateCategoryDTO("Contemporary Fiction"),
            new CreateCategoryDTO("Mystery & Thriller"),
            new CreateCategoryDTO("Science Fiction"),
            new CreateCategoryDTO("Fantasy"),
            new CreateCategoryDTO("Romance"),
            new CreateCategoryDTO("Historical Fiction"),
            new CreateCategoryDTO("Horror"),
            new CreateCategoryDTO("Biographies & Memoirs"),
            new CreateCategoryDTO("Self-Help"),
            new CreateCategoryDTO("Business & Economics"),
            new CreateCategoryDTO("Science & Nature"),
            new CreateCategoryDTO("History"),
            new CreateCategoryDTO("Travel"),
            new CreateCategoryDTO("True Crime"),
            new CreateCategoryDTO("Philosophy"),
            new CreateCategoryDTO("Textbooks"),
            new CreateCategoryDTO("Reference Books"),
            new CreateCategoryDTO("Study Guides"),
            new CreateCategoryDTO("Essays & Academic Papers"),
            new CreateCategoryDTO("Picture Books"),
            new CreateCategoryDTO("Middle Grade Fiction"),
            new CreateCategoryDTO("Young Adult Fantasy"),
            new CreateCategoryDTO("Young Adult Contemporary"),
            new CreateCategoryDTO("Cookbooks & Food"),
            new CreateCategoryDTO("Gardening"),
            new CreateCategoryDTO("Crafts & DIY"),
            new CreateCategoryDTO("Health & Fitness"),
            new CreateCategoryDTO("Sports & Outdoors"),
            new CreateCategoryDTO("Photography"),
            new CreateCategoryDTO("Art & Design"),
            new CreateCategoryDTO("Music & Performing Arts"),
            new CreateCategoryDTO("Film & TV"),
            new CreateCategoryDTO("Spiritual Growth"),
            new CreateCategoryDTO("Religious Texts"),
            new CreateCategoryDTO("Meditation & Mindfulness"),
            new CreateCategoryDTO("Poetry"),
            new CreateCategoryDTO("Comics & Graphic Novels"),
            new CreateCategoryDTO("Short Story Collections"),
            new CreateCategoryDTO("Rare & Collectible Editions")
    );

   public enum RegistrationType {
       GOOGLE,
       FACEBOOK,
       EMAIL
   }

   public enum BookStatus {
       ACTIVE,
       INACTIVE,
       SOLD
   }

   public enum TimeQuery {
       LAST_WEEK,
       LAST_TWO_WEEKS,
       LAST_MONTH,
       LAST_SIX_MONTHS,
       LAST_YEAR
   }

}

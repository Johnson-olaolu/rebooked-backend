package com.personal.rebooked.utils;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Misc {

    public  static  String getToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            token.append(random.nextInt(10));
        }
        return token.toString();
    }

    public static boolean isBcryptHash(String password) {
        String regex = "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(password);
        return matcher.matches();
    }

    public static String getStringSizeLengthFile(long size) {

        DecimalFormat df = new DecimalFormat("0.00");

        float sizeKb = 1024.0f;
        float sizeMb = sizeKb * sizeKb;
        float sizeGb = sizeMb * sizeKb;
        float sizeTerra = sizeGb * sizeKb;


        if(size < sizeMb)
            return df.format(size / sizeKb)+ " Kb";
        else if(size < sizeGb)
            return df.format(size / sizeMb) + " Mb";
        else if(size < sizeTerra)
            return df.format(size / sizeGb) + " Gb";

        return "";
    }

    public static Date calculateStartDate(Constants.TimeQuery timeQuery) {
        Calendar calendar = Calendar.getInstance();

        switch (timeQuery) {
            case LAST_WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case LAST_TWO_WEEKS:
                calendar.add(Calendar.WEEK_OF_YEAR, -2);
                break;
            case LAST_MONTH:
                calendar.add(Calendar.MONTH, -1);
                break;
            case LAST_SIX_MONTHS:
                calendar.add(Calendar.MONTH, -6);
                break;
            case LAST_YEAR:
                calendar.add(Calendar.YEAR, -1);
                break;
            default:
                throw new IllegalArgumentException("Invalid time range: " + timeQuery);
        }

        return calendar.getTime();
    }

    public  static String generateUUID () {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }
}

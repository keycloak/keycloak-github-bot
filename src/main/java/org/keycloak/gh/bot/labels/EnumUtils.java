package org.keycloak.gh.bot.labels;

import java.util.Arrays;
import java.util.Locale;

 class EnumUtils {

     public static <T extends Enum<T>> boolean isInstance(String label, Class<T> enumClass) {
         String[] split = label.split("/");
         if (split.length != 2) {
             return false;
         }
         String group = classNameToCategory(enumClass);
         if (!group.equals(split[0])) {
             return false;
         }
         String enumName = valueToEnumName(split[1]);
         return Arrays.stream(enumClass.getEnumConstants()).anyMatch(e -> e.name().equals(enumName));
     }

    public static <T extends Enum<T>> String toLabel(Enum<T> e) {
        String group = classNameToCategory(e.getClass());
        String value = enumNameToValue(e);
        return group + "/" + value;
    }

    public static <T extends Enum<T>> T fromLabel(String label, Class<T> enumClass) {
        String[] split = label.split("/");
        String group = classNameToCategory(enumClass);
        if (!group.equals(split[0])) {
            throw new IllegalArgumentException("Invalid group " + split[0] + " for label " + label);
        }
        String value = valueToEnumName(split[1]);
        return Enum.valueOf(enumClass, value);
    }

    public static <T extends Enum<T>> T fromValue(String value, Class<T> enumClass) {
         return Enum.valueOf(enumClass, valueToEnumName(value));
    }

    private static String valueToEnumName(String name) {
         return name.toUpperCase(Locale.ENGLISH).replace('-', '_');
    }

    private static <T extends Enum<T>> String enumNameToValue(Enum<T> e) {
         return e.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
    }

    private static String classNameToCategory(Class<?> enumClass) {
         return enumClass.getSimpleName().toLowerCase(Locale.ENGLISH);
    }

}

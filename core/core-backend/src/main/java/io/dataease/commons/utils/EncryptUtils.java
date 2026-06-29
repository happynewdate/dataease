package io.dataease.commons.utils;

import io.dataease.utils.BeanUtils;

import java.util.List;
import java.util.stream.Collectors;

public class EncryptUtils extends CodingUtil {

    private static final String secretKey = "www.fit2cloud.co";
    private static final String iv = "1234567890123456";


    public static Object aesEncrypt(Object o) {
        if (o == null) {
            return null;
        }
        return aesEncrypt(o.toString(), secretKey, iv);
    }


    public static void main(String[] args) {
        String ret = new String(aesDecrypt("a4xuvIEt9VQXMP6sDr9+KCpEouoazZaiOhYhJDaBVBxYTu1zKOXfuFY5h1fYpL+WjxK5JWRfviJcYOacurGunnnrhkLw4nV0SrMTnxepspIAqB59b6wv2ChFao/IQy0kp1Mdcx+vJhaiLQMjVqdhNU+4pVr5lkdKkxUvEmYgpd2liymh0lPQrP7/izYs+5ulz89SZRbNwlBna1MjfPhswOIB+vpngqJbmiMoNpBxcBSum8PW/KhJvp1nqRaLKmUMkKY3XK0KXWWDj5fzTT5gbE7OF7pmTImKDkUDN4F3QTa8WeB2mEtsgu8np/49XhRUzv3XlQ1dsa6i0AjFoBKganK8xqm8aOSJ0Hz+rwJ8xTwkotq25W5mJao91aPlPQyv").toString());
        System.out.println(ret);

    }

    public static Object aesDecrypt(Object o) {
        if (o == null) {
            return null;
        }
        return aesDecrypt(o.toString(), secretKey, iv);
    }

    public static <T> Object aesDecrypt(List<T> o, String attrName) {
        if (o == null) {
            return null;
        }
        return o.stream()
                .filter(element -> BeanUtils.getFieldValueByName(attrName, element) != null)
                .peek(element -> BeanUtils.setFieldValueByName(element, attrName, aesDecrypt(BeanUtils.getFieldValueByName(attrName, element).toString(), secretKey, iv), String.class))
                .collect(Collectors.toList());
    }

    public static Object md5Encrypt(Object o) {
        if (o == null) {
            return null;
        }
        return md5(o.toString());
    }
}

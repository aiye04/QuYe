package com.dp.utils;

public class SystemConstants {
    // 图片上传目录：指向前端静态资源目录下的 imgs（注意结尾的 imgs\）
    // 可用环境变量 QUYE_IMAGE_UPLOAD_DIR 覆盖，方便部署到不同机器
    public static final String IMAGE_UPLOAD_DIR =
            System.getenv().getOrDefault("QUYE_IMAGE_UPLOAD_DIR",
                    "F:/CODE/Redis/quye-web/nginx-1.18.0/html/web/imgs/");
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}

package com.witliving.utils;

public class SystemConstants {
    // 默认从项目根目录启动；部署时可通过环境变量指定 Nginx 可访问的图片目录。
    public static final String IMAGE_UPLOAD_DIR = System.getenv("IMAGE_UPLOAD_DIR") == null
            ? new java.io.File("frontend/imgs").getAbsolutePath() : System.getenv("IMAGE_UPLOAD_DIR");
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}

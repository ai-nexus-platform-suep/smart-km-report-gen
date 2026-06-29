package com.km.storage;

import java.io.InputStream;

/**
 * 文件存储抽象层，支持本地文件系统或 MinIO 对象存储
 */
public interface FileStorageService {

    /**
     * 存储文件
     * @param objectName  存储路径，格式 {kbId}/{docId}/{filename}
     * @param stream      文件输入流
     * @param size        文件大小
     * @param contentType 媒体类型
     */
    void store(String objectName, InputStream stream, long size, String contentType);

    /**
     * 获取文件输入流
     */
    InputStream retrieve(String objectName);

    /**
     * 删除文件
     */
    void delete(String objectName);

    /**
     * 判断存储是否可用
     */
    boolean isAvailable();
}

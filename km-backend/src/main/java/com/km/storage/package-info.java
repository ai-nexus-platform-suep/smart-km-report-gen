/**
 * 文件存储抽象层，支持本地文件系统（开发环境）和 MinIO（生产环境）。
 *
 * <p>通过配置 {@code km.storage.type} 切换实现：
 * <ul>
 *   <li>{@code local}（默认）- {@link com.km.storage.LocalFileStorageService}</li>
 *   <li>{@code minio} - {@link com.km.storage.MinioFileStorageService}</li>
 * </ul>
 */
package com.km.storage;

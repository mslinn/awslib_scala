package com.micronautics.aws;

/**
 * @author Mike Slinn
 */
public class S3Model {
    public static final int s3FileDoesNotExist = -2;
    public static final int s3FileIsOlderThanLocal = -1;
    public static final int s3FileSameAgeAsLocal = 0;
    public static final int s3FileNewerThanLocal = 1;
    public static final int s3FileDoesNotExistLocally = 2;

    public static Credentials credentials;
    public static S3 s3;
}

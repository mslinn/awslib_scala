package com.micronautics.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.StringInputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static com.micronautics.aws.Util.latestFileTime;
import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS;

/**
 * When uploading, any leading slashes for keys are removed because when AWS S3 is enabled for a web site, S3 adds a leading slash.
 *
 * Keys of assets that were uploaded by other clients might start with leading slashes, or a dit; those assets can
 * not be fetched by web browsers.
 *
 * AWS does not respect the last-modified metadata provided when uploading; it uses the upload timestamp instead.
 * After uploading, the last-modified timestamp of the uploaded file is read and applied to the local copy of the file
 * so the timestamps match.
 *
 * Java on Windows does not handle last-modified properly, so the creation date is set to the last-modified date for files (Windows only).
 */
public class S3 {
    public AmazonS3Client s3;
    public Exception exception;
    public AWSCredentials awsCredentials;

    public S3() {
        this.awsCredentials = new AWSCredentials() {
            public String getAWSAccessKeyId() {
                return System.getenv("accessKey");
            }

            public String getAWSSecretKey() {
                return System.getenv("secretKey");
            }
        };
        try {
            if (awsCredentials.getAWSAccessKeyId()!=null && awsCredentials.getAWSSecretKey()!=null) {
                s3 = new AmazonS3Client(awsCredentials);
            } else {
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("AwsCredentials.properties");
                awsCredentials = new PropertiesCredentials(inputStream);
                s3 = new AmazonS3Client(awsCredentials);
            }
        } catch (Exception ex) {
            exception = ex;
        }
    }

    public S3(final String key, final String secret) {
        awsCredentials = new BasicAWSCredentials(key, secret);
        s3 = new AmazonS3Client(awsCredentials);
    }

    // todo create policy for intranets
    protected String bucketPolicy(String bucketName) {
        return "{\n" +
                "\t\"Version\": \"2008-10-17\",\n" +
                "\t\"Statement\": [\n" +
                "\t\t{\n" +
                "\t\t\t\"Sid\": \"AddPerm\",\n" +
                "\t\t\t\"Effect\": \"Allow\",\n" +
                "\t\t\t\"Principal\": {\n" +
                "\t\t\t\t\"AWS\": \"*\"\n" +
                "\t\t\t},\n" +
                "\t\t\t\"Action\": \"s3:GetObject\",\n" +
                "\t\t\t\"Resource\": \"arn:com.micronautics.aws:s3:::" + bucketName + "/*\"\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}";
    }

    /** Create a new S3 bucket.
     * If the bucket name starts with "www.", make it publicly viewable and enable it as a web site.
     * Amazon S3 bucket names are globally unique, so once a bucket repoName has been
     * taken by any user, you can't create another bucket with that same repoName.
     * You can optionally specify a location for your bucket if you want to keep your data closer to your applications or users. */
    public Bucket createBucket(String bucketName) {
        String bnSanitized = bucketName.toLowerCase().replaceAll("[^A-Za-z0-9.]", ""); // remove non-alpha chars
        if (bucketName.compareTo(bnSanitized)!=0) {
            System.out.println("Invalid characters removed from bucket name; modified to " + bnSanitized);
            System.out.println("Press any key to continue, Control-C to stop.");
            InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isr);
            try {
                br.readLine();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }
        }
        if (bucketExists(bnSanitized)) {
            System.err.println("Bucket '" + bnSanitized + "' exists, aborting.");
            System.exit(-2);
        }
        Bucket bucket = s3.createBucket(bnSanitized);
        s3.setBucketPolicy(bnSanitized, bucketPolicy(bnSanitized));
        if (bnSanitized.startsWith("www."))
           enableWebsite(bnSanitized);
        return bucket;
    }

    public boolean bucketExists(String bucketName) {
        List<Bucket> buckets = s3.listBuckets();
        for (Bucket bucket : buckets)
          if (bucket.getName().compareTo(bucketName)==0)
              return true;
        return false;
    }

    /** Requires property com.amazonaws.sdk.disableCertChecking to have a value (any value will do) */
    public boolean isWebsiteEnabled(String bucketName) {
        try {
            return s3.getBucketWebsiteConfiguration(bucketName)!=null;
        } catch (Exception e) {
            return false;
        }
    }

    public void enableWebsite(String bucketName) {
        BucketWebsiteConfiguration configuration = new BucketWebsiteConfiguration("index.html");
        s3.setBucketWebsiteConfiguration(bucketName, configuration);
    }

    public void enableWebsite(String bucketName, String errorPage) {
        BucketWebsiteConfiguration configuration = new BucketWebsiteConfiguration("index.html", errorPage);
        s3.setBucketWebsiteConfiguration(bucketName, configuration);
    }

    public String getBucketLocation(String bucketName) {
        return s3.getBucketLocation(bucketName);
    }

    /** List the buckets in the account */
    public String[] listBuckets() {
        LinkedList<String> result = new LinkedList<String>();
        for (Bucket bucket : s3.listBuckets())
            result.add(bucket.getName());
        return result.toArray(new String[result.size()]);
    }

    /** Uploads a file to the specified bucket. The file's last-modified date is applied to the uploaded file.
     * AWS does not respect the last-modified metadata, and Java on Windows does not handle last-modified properly either.
     * If the key has leading slashes, they are removed for consistency.
     *
     * AWS does not respect the last-modified metadata provided when uploading; it uses the upload timestamp instead.
     * After uploading, the last-modified timestamp of the uploaded file is read and applied to the local copy of the file
     * so the timestamps match.
     *
     * Java on Windows does not handle last-modified properly, so the creation date is set to the last-modified date (Windows only).
     *
     * To list the last modified date with seconds in bash with: <code>ls -lc --time-style=full-iso</code>
     * To list the creation date with seconds in bash with: <code>ls -l --time-style=full-iso</code> */
    public PutObjectResult uploadFile(String bucketName, String key, File file) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setLastModified(new Date(latestFileTime(file))); // ignored by S3
//        System.out.println("File latest time=" + lastestFileTime(file) + "metadata.lastModified=" + metadata.getLastModified().getTime());
        metadata.setContentEncoding("utf-8");
        // content length is set by s3.putObject()
        setContentType(key, metadata);

        while (key.startsWith("/"))
            key = key.substring(1);
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);
            putObjectRequest.setMetadata(metadata);
            putObjectRequest.setProgressListener(new ProgressListener() {
                int bytesTransferred = 0;
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                    bytesTransferred += progressEvent.getBytesTransfered();
                    if (progressEvent.getEventCode()==ProgressEvent.COMPLETED_EVENT_CODE)
                        System.out.print(" " + bytesTransferred + " bytes; ");
                    else
                        System.out.print(".");
                }
            });
            PutObjectResult result = s3.putObject(putObjectRequest);

            // compensate for AWS S3 not storing last modified time properly
            ObjectMetadata m2 = s3.getObjectMetadata(bucketName, key);
            FileTime time = FileTime.fromMillis(m2.getLastModified().getTime());
            //System.out.println("m2 time=" + m2.getLastModified());
            Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class)
                .setTimes(time, null, IS_OS_WINDOWS ? time : time);
            return result;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new PutObjectResult();
        }
    }

    public PutObjectResult uploadString(String bucketName, String key, String contents) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setLastModified(new Date());
        metadata.setContentEncoding("utf-8");
        metadata.setContentLength(contents.length());
        setContentType(key, metadata);
        try {
            InputStream inputStream = new StringInputStream(contents);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);
            return s3.putObject(putObjectRequest);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new PutObjectResult();
        }
    }

    /** @param key if the key has any leading slashes, they are removed
     *  @see <a href="http://docs.amazonwebservices.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/ObjectMetadata.html">ObjectMetadata</a> */
    public void uploadStream(String bucketName, String key, InputStream stream, int length) {
        while (key.startsWith("/"))
            key = key.substring(1);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(length);
        metadata.setContentEncoding("utf-8");
        setContentType(key, metadata);
        //metadata.setCacheControl("cacheControl");
        s3.putObject(new PutObjectRequest(bucketName, key, stream, metadata));
    }

    private void setContentType(String key, ObjectMetadata metadata) {
        String keyLC = key.toLowerCase().trim();
        if (keyLC.endsWith(".css"))
            metadata.setContentType("text/css");
        else if (keyLC.endsWith(".csv"))
            metadata.setContentType("application/csv");
        else if (keyLC.endsWith(".doc") || keyLC.endsWith(".dot") || keyLC.endsWith(".docx"))
            metadata.setContentType("application/vnd.ms-word");
        else if (keyLC.endsWith(".dtd"))
            metadata.setContentType("application/xml-dtd");
        else if (keyLC.endsWith(".flv"))
            metadata.setContentType("video/x-flv");
        else if (keyLC.endsWith(".gif"))
            metadata.setContentType("image/gif");
        else if (keyLC.endsWith(".gzip") || keyLC.endsWith(".gz"))
            metadata.setContentType("application/gzip");
        else if (keyLC.endsWith(".html") || keyLC.endsWith(".htm")  || keyLC.endsWith(".shtml") || keyLC.endsWith(".jsp") || keyLC.endsWith(".php"))
            metadata.setContentType("text/html");
        else if (keyLC.endsWith(".ico"))
            metadata.setContentType("image/vnd.microsoft.icon");
        else if (keyLC.endsWith(".jpg"))
            metadata.setContentType("image/jpeg");
        else if (keyLC.endsWith(".js"))
            metadata.setContentType("application/javascript");
        else if (keyLC.endsWith(".json"))
            metadata.setContentType("application/json");
        else if (keyLC.endsWith(".mp3") || keyLC.endsWith(".mpeg"))
            metadata.setContentType("audio/mpeg");
        else if (keyLC.endsWith(".mp4"))
            metadata.setContentType("audio/mp4");
        else if (keyLC.endsWith(".ogg"))
            metadata.setContentType("application/ogg");
        else if (keyLC.endsWith(".pdf"))
            metadata.setContentType("application/pdf");
        else if (keyLC.endsWith(".png"))
            metadata.setContentType("image/png");
        else if (keyLC.endsWith(".ppt") || keyLC.endsWith(".pptx"))
            metadata.setContentType("application/vnd.ms-powerpoint");
        else if (keyLC.endsWith(".ps"))
            metadata.setContentType("application/postscript");
        else if (keyLC.endsWith(".qt"))
            metadata.setContentType("video/quicktime");
        else if (keyLC.endsWith(".ra"))
            metadata.setContentType("audio/vnd.rn-realaudio");
        else if (keyLC.endsWith(".tiff"))
            metadata.setContentType("image/tiff");
        else if (keyLC.endsWith(".txt"))
            metadata.setContentType("text/plain");
        else if (keyLC.endsWith(".xls") || keyLC.endsWith(".xlsx"))
            metadata.setContentType("application/vnd.ms-excel");
        else if (keyLC.endsWith(".xml"))
            metadata.setContentType("application/xml");
        else if (keyLC.endsWith(".vcard"))
            metadata.setContentType("text/vcard");
        else if (keyLC.endsWith(".wav"))
            metadata.setContentType("audio/vnd.wave");
        else if (keyLC.endsWith(".webm"))
            metadata.setContentType("audio/webm");
        else if (keyLC.endsWith(".wmv"))
            metadata.setContentType("video/x-ms-wmv");
        else if (keyLC.endsWith(".zip"))
            metadata.setContentType("application/zip");
        else
            metadata.setContentType("application/octet-stream");
    }

    /** Download an object - if the key has any leading slashes, they are removed.
     * When you download an object, you get all of the object's metadata and a
     * stream from which to read the contents. It's important to read the contents of the stream as quickly as
     * possible since the data is streamed directly from Amazon S3 and your network connection will remain open
     * until you read all the data or close the input stream.
     *
     * GetObjectRequest also supports several other options, including conditional downloading of objects
     * based on modification times, ETags, and selectively downloading a range of an object. */
    public InputStream downloadFile(String bucketName, String key) {
        while (key.startsWith("/"))
            key = key.substring(1);
        key = key.replace("//", "/");
        S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
//        System.out.println("Content-Type: " + object.getObjectMetadata().getContentType());
        return object.getObjectContent();
    }

    /** List objects in given bucketName by prefix.
     * @param prefix Any leading slashes are removed if a prefix is specified */
    public String[] listObjectsByPrefix(String bucketName, String prefix) {
        while (null!=prefix && prefix.length()>0 && prefix.startsWith("/"))
            prefix = prefix.substring(1);
        LinkedList<String> result = new LinkedList<String>();
        boolean more = true;
        ObjectListing objectListing = s3.listObjects(new ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix(prefix));
        while (more) {
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries())
                result.add(objectSummary.getKey() + " (size = " + objectSummary.getSize() + ")");
            more = objectListing.isTruncated();
            if (more)
                objectListing = s3.listNextBatchOfObjects(objectListing);
        }
        return result.toArray(new String[result.size()]);
    }

    /** @param prefix Any leading slashes are removed if a prefix is specified
     *  @return collection of S3ObjectSummary; keys are relativized if prefix is adjusted */
    public LinkedList<S3ObjectSummary> getAllObjectData(String bucketName, String prefix) {
        boolean prefixAdjusted = false;
        while (null!=prefix && prefix.length()>0 && prefix.startsWith("/")) {
            prefix = prefix.substring(1);
            prefixAdjusted = true;
        }
        LinkedList<S3ObjectSummary> result = new LinkedList<S3ObjectSummary>();
        boolean more = true;
        ObjectListing objectListing = s3.listObjects(new ListObjectsRequest().withBucketName(bucketName).withPrefix(prefix));
        while (more) {
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                if (prefixAdjusted)
                    objectSummary.setKey(relativize(objectSummary.getKey()));
                result.add(objectSummary);
            }
            more = objectListing.isTruncated();
            if (more)
                objectListing = s3.listNextBatchOfObjects(objectListing);
        }
        return result;
    }

    /** @param prefix Any leading slashes are removed if a prefix is specified
     * @return ObjectSummary with leading "./", prepended if necessary*/
    public S3ObjectSummary getOneObjectData(String bucketName, String prefix) {
        while (null!=prefix && prefix.length()>0 && prefix.startsWith("/"))
            prefix = prefix.substring(1);
        ObjectListing objectListing = s3.listObjects(new ListObjectsRequest().withBucketName(bucketName).withPrefix(prefix));
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            String key = objectSummary.getKey();
            if (key.compareTo(prefix)==0) {
                objectSummary.setKey(relativize(key));
                return objectSummary;
            }
        }
        return null;
    }

    public String getResourceUrl(String bucketName, String key) {
        return s3.getResourceUrl(bucketName, key);
    }

    /** @param key any leading slashes are removed so the key can be used as a relative path */
    public static String relativize(String key) {
        String result = key;
        while (result.startsWith("/"))
            result = result.substring(1);
        result = result.replace("//", "/");
        return result;
    }

    /** Delete an object - if they key has any leading slashes, they are removed.
     * Unless versioning has been turned on for the bucket, there is no way to undelete an object. */
    public void deleteObject(String bucketName, String key) {
        if (key.startsWith("/"))
            key = key.substring(1);
        s3.deleteObject(bucketName, key);
    }

    /** Delete a bucket - The bucket will automatically be emptied if necessary so it can be deleted. */
    public void deleteBucket(String bucketName) throws AmazonClientException {
        emptyBucket(bucketName);
        s3.deleteBucket(bucketName);
    }

    public void emptyBucket(String bucketName) throws AmazonClientException {
        LinkedList<S3ObjectSummary> items = getAllObjectData(bucketName, null);
        for (S3ObjectSummary item : items)
            s3.deleteObject(bucketName, item.getKey());
    }

    /** Displays the contents of the specified input stream as text.
     * @param input The input stream to display as text.
     * @throws IOException  */
    private static void displayTextInputStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;

            System.out.println("    " + line);
        }
        System.out.println();
    }
}

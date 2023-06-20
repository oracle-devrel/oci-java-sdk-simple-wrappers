/*Copyright (c) 2023 Oracle and/or its affiliates.

The Universal Permissive License (UPL), Version 1.0

Subject to the condition set forth below, permission is hereby granted to any
person obtaining a copy of this software, associated documentation and/or data
(collectively the "Software"), free of charge and under any and all copyright
rights in the Software, and any and all patent rights owned or freely
licensable by each licensor hereunder covering either (i) the unmodified
Software as contributed to or provided by such licensor, or (ii) the Larger
Works (as defined below), to deal in both

(a) the Software, and
(b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
one is included with the Software (each a "Larger Work" to which the Software
is contributed by such licensors),

without restriction, including without limitation the rights to copy, create
derivative works of, display, perform, and distribute the Software and make,
use, sell, offer for sale, import, export, have made, and have sold the
Software and the Larger Work(s), and to sublicense the foregoing rights on
either these or other terms.

This license is subject to the following condition:
The above copyright notice and either this complete permission notice or at
a minimum a reference to the UPL must be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.oracle.timg.oci.objectstorage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.Bucket;
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceMetadataRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteBucketResponse;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetBucketResponse;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.transfer.DownloadConfiguration;
import com.oracle.bmc.objectstorage.transfer.DownloadManager;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadResponse;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * this class provides a wrapper around the oci sdk for object storage and makes
 * things easier
 */
@Slf4j
public class ObjectStorageProcessor {

	private UploadManager uploadManager;
	private DownloadManager downloadManager;
	private final AuthenticationProcessor authProcessor;
	private final ObjectStorageClient objectstorageClient;
	private final String namespace;
	private String s3CompatibilityCompartmentOCID;
	public final String DEFAULT_PATH_SEPARATOR_IN_OBJECT_STORAGE = "/";
	@Getter
	@Setter
	private String pathSeparatorInObjectStorage = DEFAULT_PATH_SEPARATOR_IN_OBJECT_STORAGE;

	/**
	 * creates an instance whihc will use the provided AuthenticationProcessor for
	 * authentication
	 * 
	 * @param authProcessor
	 */
	public ObjectStorageProcessor(AuthenticationProcessor authProcessor) {
		this.authProcessor = authProcessor;

		objectstorageClient = ObjectStorageClient.builder().region(authProcessor.getRegionName())
				.build(authProcessor.getProvider());
		// get the namespace
		GetNamespaceResponse namespaceResponse = objectstorageClient
				.getNamespace(GetNamespaceRequest.builder().build());
		namespace = namespaceResponse.getValue();
	}

	/**
	 * allows you to change the region form the default provided by the
	 * AuthenticationProcessor
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		objectstorageClient.setRegion(regionName);
	}

	/**
	 * the S3 compatibility only works in one compartment, by default this is the
	 * tenancy root though it can be overidden for the entire tenancy.
	 * 
	 * @return the OCID of the compartment used for S3 compatibility
	 */
	public String getS3CompatibilityCompartmentOCID() {
		if (s3CompatibilityCompartmentOCID == null) {
			GetNamespaceMetadataRequest request = GetNamespaceMetadataRequest.builder().namespaceName(namespace)
					.build();
			s3CompatibilityCompartmentOCID = objectstorageClient.getNamespaceMetadata(request).getNamespaceMetadata()
					.getDefaultS3CompartmentId();
		}
		return s3CompatibilityCompartmentOCID;
	}

	/**
	 * for functions that are not handled byt he wrapper this lets you get the
	 * underlying storager client.
	 * 
	 * @return
	 */
	public ObjectStorageClient getClient() {
		return objectstorageClient;
	}

	/**
	 * OCI tenancies have a namespace for objects, currently there is only one
	 * supported, but just in case support for additional ones is implemented this
	 * wil provide you with details fo the default one (which is used by all object
	 * operations in this class.
	 * 
	 * @return
	 */
	public String getObjectStorageNamespace() {
		return namespace;
	}

	/**
	 * list all bucket names in the tenancy root conpartment
	 * 
	 * @return list of zero or more bucket names
	 */
	public Collection<String> listBucketsInCompartment() {
		return listBucketNamesInCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * list all bucket names in the specified compartment
	 * 
	 * @param compartment - must not be null
	 * @return list of zero or more bucket names
	 */
	public Collection<String> listBucketNamesInCompartment(@NonNull Compartment compartment) {
		return listBucketNamesInCompartment(compartment.getId());
	}

	/**
	 * list all bucket names in the compartment with the specified ocid
	 * 
	 * @param compartmentOCID - must not be null
	 * @return list of zero or more bucket names
	 */
	public Collection<String> listBucketNamesInCompartment(@NonNull String compartmentOCID) {
		return mapBucketsInCompartment(compartmentOCID).keySet();
	}

	/**
	 * get information on all buckets in the root compartment
	 * 
	 * @return a map keyed on the bucket name with values for the bucket summaries
	 */
	public Map<String, BucketSummary> mapBucketsInCompartment() {
		return mapBucketsInCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * get information on all buckets in the specified compartment
	 * 
	 * @param compartment - must not be null
	 * @return a map keyed on the bucket name with values for the bucket summaries
	 */
	public Map<String, BucketSummary> mapBucketsInCompartment(@NonNull Compartment compartment) {
		return mapBucketsInCompartment(compartment.getId());
	}

	/**
	 * get information on all buckets in the compartment with the specified ocid
	 * 
	 * @param compartmentOCID - must not be null
	 * @return a map keyed on the bucket name with values for the bucket summaries
	 */
	public Map<String, BucketSummary> mapBucketsInCompartment(@NonNull String compartmentOCID) {
		Map<String, BucketSummary> bucketMap = new HashMap<>();
		ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().namespaceName(namespace)
				.compartmentId(compartmentOCID).build();

		Iterable<BucketSummary> BucketSummaryIterator = objectstorageClient.getPaginators()
				.listBucketsRecordIterator(listBucketsRequest);
		for (BucketSummary summary : BucketSummaryIterator) {
			bucketMap.put(summary.getName(), summary);
		}
		return bucketMap;
	}

	/**
	 * Test to see if the bucket names exists in any compartment
	 * 
	 * @param bucketName - must not be null
	 * @return true if it exists, false if not
	 */
	public boolean bucketExists(@NonNull String bucketName) {
		return getBucket(bucketName) != null;
	}

	/**
	 * Gets the details of the bucket in any compartment
	 * 
	 * @param bucketName - must not be null
	 * @return null if no bucket exists with that name, bucket details otherwise
	 */
	public Bucket getBucket(@NonNull String bucketName) {
		GetBucketRequest request = GetBucketRequest.builder().bucketName(bucketName).namespaceName(namespace).build();
		try {
			GetBucketResponse response = objectstorageClient.getBucket(request);
			return response.getBucket();
		} catch (BmcException e) {
			return null;
		}
	}

	/**
	 * Create a bucker with the provided name in the specified compartment
	 * 
	 * @param bucketName  - must not be null
	 * @param compartment - must not be null
	 * @return the new bucket
	 */
	public Bucket createBucketInCompartment(@NonNull String bucketName, @NonNull Compartment compartment) {
		return createBucketInCompartment(bucketName, compartment.getId());
	}

	/**
	 * Create a bucker with the provided name in the compartment with the specified
	 * ocid
	 * 
	 * @param bucketName      - must not be null
	 * @param compartmentOCID - must not be null
	 * @return the new bucket
	 */
	public Bucket createBucketInCompartment(@NonNull String bucketName, @NonNull String compartmentOCID) {
		CreateBucketDetails buildDetails = CreateBucketDetails.builder().compartmentId(compartmentOCID).name(bucketName)
				.build();
		CreateBucketRequest request = CreateBucketRequest.builder().namespaceName(namespace)
				.createBucketDetails(buildDetails).build();
		Bucket bucket = objectstorageClient.createBucket(request).getBucket();
		log.debug("Created bucket " + bucket.getName() + " with OCID " + bucket.getId() + " in compartment "
				+ bucket.getCompartmentId());
		return bucket;
	}

	/**
	 * delete the specified bucket
	 * 
	 * @param bucket - must not be null
	 * @return true if deleted, false if not
	 */
	public boolean deleteBucket(@NonNull Bucket bucket) {
		return deleteBucket(bucket.getName());
	}

	/**
	 * delete the bucket with the specified name
	 * 
	 * @param bucketName - must not be null
	 * @return true if deleted, false if not
	 */
	public boolean deleteBucket(@NonNull String bucketName) {
		DeleteBucketRequest request = DeleteBucketRequest.builder().bucketName(bucketName).namespaceName(namespace)
				.build();
		DeleteBucketResponse response = objectstorageClient.deleteBucket(request);
		log.debug("delete bucket request for name " + bucketName + " has response code "
				+ response.get__httpStatusCode__());
		return response.get__httpStatusCode__() == HttpURLConnection.HTTP_OK;
	}

	/**
	 * return a set of all object names in the bucket including prefixes
	 * 
	 * @param bucketName - must not be null
	 * @return a set of zero or more object names
	 */
	public Set<String> listObjectNamesInBucket(@NonNull String bucketName) {
		return listObjectNamesInBucket(bucketName, null);
	}

	/**
	 * return a set of all object names in the bucket with the specified prefix
	 * 
	 * @param bucketName - must not be null
	 * @param prefix     - if not null then obly object with a matching prefix are
	 *                   returned
	 * @return a set of zero or more objects, if a non null prefix is used it will
	 *         be removed from the returned names
	 */
	public Set<String> listObjectNamesInBucket(@NonNull String bucketName, String prefix) {
		return mapObjectsInBucket(bucketName, prefix).keySet();
	}

	/**
	 * return a collection of all objects in the bucket
	 * 
	 * @param bucketName - must not be null
	 * @return a set of zero or more object names
	 */
	public Collection<ObjectSummary> listObjectsInBucket(@NonNull String bucketName) {
		return listObjectsInBucket(bucketName, null);
	}

	/**
	 * return a collection of all object names in the bucket with the specified
	 * prefix
	 * 
	 * @param bucketName - must not be null
	 * @param prefix     - if not null then only objects with a matching prefix are
	 *                   returned
	 * @return a set of zero or more objects
	 */
	public Collection<ObjectSummary> listObjectsInBucket(@NonNull String bucketName, String prefix) {
		return mapObjectsInBucket(bucketName, prefix).values();
	}

	/**
	 * returns a set of all prefixes (i.e. object names with last part of the name
	 * removed)
	 * 
	 * @see the pathSeparatorInObjectStorage related methods to set tghe prexix
	 *      separator, but default / is used
	 * @param bucketName - must not be null
	 * @return a set of zero of more prefixes
	 */
	public Set<String> listPrefixesInBucket(@NonNull String bucketName) {
		return listPrefixesInBucket(bucketName, null, false);
	}

	/**
	 * returns a set of all prefixes (i.e. object names with last part of the name
	 * removed)
	 * 
	 * @see the pathSeparatorInObjectStorage related methods to set tghe prexix
	 *      separator, but default / is used
	 * @param bucketName         - must not be null
	 * @param startPrefix        - if non null will be removed form the start of the
	 *                           prefix
	 * @param includePrefixPaths - if true will split the prefix out e.g.
	 *                           /department/poject/object wull return prefixes of
	 *                           /, /department and /department/object. if false
	 *                           will only return the prefix e.g.
	 *                           /department/project
	 * @return a set of zero of more prefixes
	 */
	public Set<String> listPrefixesInBucket(@NonNull String bucketName, String startPrefix,
			boolean includePrefixPaths) {
		Collection<String> objectNames = listObjectNamesInBucket(bucketName, startPrefix);
		Set<String> results = new TreeSet<>();
		if (includePrefixPaths) {
			results.add(pathSeparatorInObjectStorage);
		}
		for (String objectName : objectNames) {
			int lastDelim = objectName.lastIndexOf(pathSeparatorInObjectStorage);
			if (lastDelim >= 0) {
				String fullPrefix = objectName.substring(0, lastDelim);
				if (includePrefixPaths) {
					String partPrefix = "";
					String prefixElements[] = fullPrefix.split(pathSeparatorInObjectStorage);
					boolean doneFirst = false;
					for (String prefixPath : prefixElements) {
						if (doneFirst) {
							partPrefix = partPrefix + pathSeparatorInObjectStorage + prefixPath;
							results.add(partPrefix);
						} else {
							partPrefix = prefixPath;
							if (partPrefix.length() > 0) {
								results.add(partPrefix);
							}
							doneFirst = true;
						}
					}
				} else {
					results.add(fullPrefix);
				}
			}

		}
		return results;
	}

	/**
	 * gets all of the objects summaries keyed by the object name
	 * 
	 * @param bucketName - must not be null
	 * @return
	 */
	public Map<String, ObjectSummary> mapObjectsInBucket(@NonNull String bucketName) {
		return mapObjectsInBucket(bucketName, null);
	}

	/**
	 * gets all of the objects summaries keyed by the object name whci if prefix is
	 * non null will be removed from the name
	 * 
	 * @param bucketName - must not be null
	 * @param prefix     - only objects with a matching prefix will be returned, if
	 *                   null all objects will be returned.
	 * @return map of matching objects if prefix is provided it will be removed form
	 *         the object name used as the map key
	 */
	public Map<String, ObjectSummary> mapObjectsInBucket(@NonNull String bucketName, String prefix) {
		return mapObjectsInBucket(bucketName, prefix, null, null);
	}

	/**
	 * get a map of all objects optionally under the prefix, optionally with the
	 * name including or after the start, optionally with the name before the end
	 * 
	 * @param bucketName - must be non null
	 * @param prefix     - if not null the prefix will be applied as a filter
	 * @param start      - if not null then only objects with a name (after prefix
	 *                   filtering) which are the same as or greater than the start
	 *                   will be returned
	 * @param end        - if not null then only objects with a name (after prefix
	 *                   filtering) which are the less than than the start will be
	 *                   returned
	 * @return map of matching objects if prefix is provided it will be removed form
	 *         the object name used as the map key
	 */
	public Map<String, ObjectSummary> mapObjectsInBucket(@NonNull String bucketName, String prefix, String start,
			String end) {
		Map<String, ObjectSummary> objectSummaries = new HashMap<>();
		ListObjectsRequest.Builder listObjectsBuilder = ListObjectsRequest.builder().bucketName(bucketName)
				.namespaceName(namespace);
		if (prefix != null) {
			listObjectsBuilder.prefix(prefix);
		}
		if (start != null) {
			listObjectsBuilder.start(start);
		}
		if (end != null) {
			listObjectsBuilder.end(end);
		}
		Iterable<ObjectSummary> objectSummaryIterator = objectstorageClient.getPaginators()
				.listObjectsRecordIterator(listObjectsBuilder.build());
		for (ObjectSummary objectSummary : objectSummaryIterator) {
			objectSummaries.put(
					prefix == null ? objectSummary.getName() : objectSummary.getName().substring(prefix.length()),
					objectSummary);
		}
		return objectSummaries;
	}

	/**
	 * get the details of an object
	 * 
	 * @param bucketName - must not be null
	 * @param prefix     - if non null will be applied to the object name when
	 *                   searching
	 * @param objectName - must not be null
	 * @return object summary or null if not found
	 */
	public ObjectSummary getObjectSummary(@NonNull String bucketName, String prefix, @NonNull String objectName) {
		Map<String, ObjectSummary> summaries = mapObjectsInBucket(bucketName, prefix, objectName, objectName);
		return summaries.get(objectName);
	}

	public long deleteObjectsInBucket(@NonNull String bucketName) {
		return deleteObjectsInBucket(bucketName, null);
	}

	public long deleteObjectsInBucket(@NonNull String bucketName, String prefix) {
		Map<String, ObjectSummary> objectsToDelete = mapObjectsInBucket(bucketName, prefix);
		return deleteObjects(bucketName, prefix, objectsToDelete.keySet());
	}

	/**
	 * delete the objects with the names in the object males list
	 * 
	 * @param bucketName  - must not be null
	 * @param objectNames - must not be null
	 * @return n umber of deleted objects
	 */
	public long deleteObjects(@NonNull String bucketName, @NonNull Collection<String> objectNames) {
		return deleteObjects(bucketName, null, objectNames);
	}

	/**
	 * delete the objects with the names in the object males list, if non null the
	 * prefix will be applied to the name
	 * 
	 * @param bucketName  - must not be null
	 * @param prefix      - if non null will be applied to the object name when
	 *                    deleting
	 * @param objectNames - must not be null
	 * @return n umber of deleted objects
	 */

	private long deleteObjects(@NonNull String bucketName, String prefix, @NonNull Collection<String> objectNames) {
		return objectNames.stream().map(objectName -> deleteObject(bucketName, prefix, objectName))
				.filter(deleteResp -> deleteResp).count();
	}

	/**
	 * delete the specified object
	 * 
	 * @param bucketName - must not be null
	 * @param objectName - must not be null
	 * @return true if deleted, false if not
	 */
	public boolean deleteObject(@NonNull String bucketName, @NonNull String objectName) {
		return deleteObject(bucketName, null, objectName);
	}

	/**
	 * delete the specified object
	 * 
	 * @param bucketName - must not be null
	 * @param prefix     - if non null will be applied to the object name when
	 *                   deleting
	 * @param objectName - must not be null
	 * @return true if deleted, false if not
	 */
	public boolean deleteObject(@NonNull String bucketName, String prefix, @NonNull String objectName) {
		// the object name may have had the prefix used as a delim
		String fullObjectName = prefix == null ? objectName : prefix + objectName;

		DeleteObjectRequest request = DeleteObjectRequest.builder().bucketName(bucketName).objectName(fullObjectName)
				.namespaceName(namespace).build();
		try {
			DeleteObjectResponse response = objectstorageClient.deleteObject(request);
			log.debug("Delete object " + fullObjectName + " from bucket " + bucketName + " has reponse code "
					+ response.get__httpStatusCode__());
			return response.get__httpStatusCode__() == HttpURLConnection.HTTP_OK;
		} catch (BmcException e) {
			log.warn("Can't delete object, msg is " + e.getLocalizedMessage());
			return false;
		}
	}

	/**
	 * create an input stream to read the object. Note that the caller will have to
	 * close the input stream themselves
	 * 
	 * @param bucketName - must not be null
	 * @param objectName - must not be null
	 * @return InputStream whcih contains the object contents or null if the object
	 *         can't be found
	 */
	// IMPORTANT, the caller is responsible for closing the stream
	// returns null if the bucket / object name is not valid
	public InputStream getObject(@NonNull String bucketName, @NonNull String objectName) {
		GetObjectResponse getResponse = objectstorageClient.getObject(GetObjectRequest.builder()
				.namespaceName(namespace).bucketName(bucketName).objectName(objectName).build());
		// stream contents should match the file uploaded
		return getResponse.getInputStream();
	}

	// IMPORTANT, the caller is responsible for closing the stream
	// will transfer the data from the current position in the input
	// stream up to the end of the stream
	// returns the version ID of the uploaded contents
	/**
	 * Uploads the provided input stream to the object, note that this will not work
	 * with large input streams (though this will work for uploads of 50GiB a mutli
	 * part upload is recommended for uploads greater than 100MiB, to support this a
	 * file driven upload is provided in this class as this input stream based
	 * version has no way to identify the upload size and trigger multi part uploads
	 * if needed )
	 * 
	 * @param bucketName - must not be null
	 * @param objectName - must not be null
	 * @param contents   - must not be null
	 * @return the version id of the object
	 */
	public String putObject(@NonNull String bucketName, @NonNull String objectName, @NonNull InputStream contents) {
		PutObjectResponse putResponse = objectstorageClient
				.putObject(PutObjectRequest.builder().namespaceName(namespace).bucketName(bucketName)
						.objectName(objectName).putObjectBody(contents).build());
		if (putResponse.get__httpStatusCode__() != HttpURLConnection.HTTP_OK) {
			return null;
		} else {
			return putResponse.getVersionId();
		}
	}

	/**
	 * Uploads the provided file to the object storage, triggering a multi part
	 * upload if it's optimal. Note that the object name here shoudl include all
	 * prefixes
	 * 
	 * @param bucketName - must not be null
	 * @param objectName - must not be null
	 * @param localFile  - must not be null
	 * @return MD5 hash of the content
	 * @throws IOException
	 */

	public String uploadFile(@NonNull String bucketName, @NonNull String objectName, @NonNull File localFile)
			throws IOException {
		return uploadFile(bucketName, null, objectName, localFile);
	}

	// upload a file, note that this does not do any checksumming at this time.
	public String uploadFile(@NonNull String bucketName, String objectPrefix, @NonNull String objectName,
			@NonNull File localFile) throws IOException {
		String fullObjectName = objectPrefix == null ? objectName
				: objectPrefix + pathSeparatorInObjectStorage + objectName;
		// share the upload manager and allow lazy instantiation
		if (uploadManager == null) {
			UploadConfiguration uploadConfiguration = UploadConfiguration.builder().allowMultipartUploads(true)
					.allowParallelUploads(true).build();
			uploadManager = new UploadManager(objectstorageClient, uploadConfiguration);
		}
		PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucketName(bucketName).namespaceName(namespace)
				.objectName(fullObjectName).build();
		UploadRequest uploadRequest = UploadRequest.builder(localFile).allowOverwrite(true).build(putObjectRequest);
		UploadResponse uploadResponse = uploadManager.upload(uploadRequest);
		String uploadedMD5 = uploadResponse.getContentMd5();
		return uploadedMD5 == null ? uploadResponse.getMultipartMd5() : uploadedMD5;
	}

	/**
	 * Upload all files in the localStartingPoint directory. The path name
	 * represented by localStartingPoint will be removed from the object name in
	 * objects storage.
	 * 
	 * If provided the objectPrefix will be applied. Thus uploading a
	 * localStartingPoint of /home/tim/filestoupload containing a file called file-a
	 * and a subdirectoy of images containing picture1.jpg and picture2.png will
	 * result in objects being created called /file-a, /images/picture1.jpg and
	 * /images/picture2.png
	 * 
	 * If a prefix of /backup/tim/upload was specified then the objects would be
	 * named /backup/tim/upload/file-a, /backup/tim/upload/images/picture1.jpg and
	 * /backup/tim/upload/images/picture2.png
	 * 
	 * This and the downloadDirectory method are basically a way to transfer a
	 * directory structure to and from object storage
	 * 
	 * @param bucketName         - must not be null
	 * @param objectPrefix       - if provided will be applied to all uploaded
	 *                           objects
	 * @param localStartingPoint - must not be null and must be a directory
	 * @return the relative file names and their MD5 checksums
	 * @throws IOException
	 */
	public String uploadDirectory(@NonNull String bucketName, String objectPrefix, @NonNull File localStartingPoint)
			throws IOException {
		// get all of the objects within it
		File[] dirEntries = localStartingPoint.listFiles();
		String res = "";
		for (File dirEntry : dirEntries) {
			res += uploadObject(bucketName, objectPrefix, dirEntry) + "\n";
		}
		return res;
	}

	/**
	 * Uploads an object or a directory containing objects
	 * 
	 * @param bucketName         - must not be null
	 * @param objectPrefix       - if provided will be applied to all uploaded
	 *                           objects
	 * @param localStartingPoint - must not be null and must be a file or directory
	 * @return the relative file names and their MD5 checksums
	 * @throws IOException
	 */

	public String uploadObject(@NonNull String bucketName, String objectPrefix, @NonNull File localStartingPoint)
			throws IOException {
		// build a full list of the local directories
		if (!localStartingPoint.canRead()) {
			return "Can't read " + localStartingPoint.getPath();
		} else if (localStartingPoint.isFile()) {
			return localStartingPoint.getName() + " "
					+ uploadFile(bucketName, objectPrefix, localStartingPoint.getName(), localStartingPoint);
		} else if (localStartingPoint.isDirectory()) {
			return localStartingPoint.getName() + " directory\n" + uploadDirectory(bucketName,
					objectPrefix + pathSeparatorInObjectStorage + localStartingPoint.getName(), localStartingPoint);
		} else {
			return "while " + localStartingPoint.getPath()
					+ " is readable it's not a file or a directory, don't know what to do with it.";
		}
	}

	/**
	 * Downloads the specified object into the provided localFile
	 * 
	 * @param bucketName - must not be null
	 * @param objectName - must not be null
	 * @param localFile  - must not be null
	 * @return number of bytes downloaded
	 * @throws IOException
	 */
	public int downloadFile(@NonNull String bucketName, @NonNull String objectName, @NonNull File localFile)
			throws IOException {
		return downloadFile(bucketName, null, objectName, localFile);
	}

	/**
	 * Downloads the specified object into the provided localFile
	 * 
	 * @param bucketName   - must not be null
	 * @param objectPrefix - if non null will be applied to the object name before
	 *                     starting the download
	 * @param objectName   - must not be null
	 * @param localFile    - must not be null
	 * @return number of bytes downloaded
	 * @throws IOException
	 */
	public int downloadFile(@NonNull String bucketName, String objectPrefix, @NonNull String objectName,
			@NonNull File localFile) throws IOException {
		String fullObjectName = objectPrefix == null ? objectName : objectPrefix + objectName;
		// share the download manager and allow lazy instantiation
		if (downloadManager == null) {
			DownloadConfiguration downloadConfiguration = DownloadConfiguration.builder().build();
			downloadManager = new DownloadManager(objectstorageClient, downloadConfiguration);
		}
		GetObjectRequest request = GetObjectRequest.builder().bucketName(bucketName).namespaceName(namespace)
				.objectName(fullObjectName).build();
		try {
			GetObjectResponse response = downloadManager.downloadObjectToFile(request, localFile);
			log.debug("download object " + fullObjectName + " in bucket " + bucketName + " to " + localFile.getPath()
					+ " has response code " + response.get__httpStatusCode__());
			return response.get__httpStatusCode__();
		} catch (BmcException e) {
			log.warn("Problem downloading file " + e.getLocalizedMessage());
			return -1;
		}
	}

	/**
	 * Downloads the object to the driectory represented by localStartingPoint , if
	 * the object name includes path elements e.g. /user/tim/file1.txt then if
	 * needed the /user/tim directory will be created under the local starting point
	 * 
	 * @param bucketName         - must not be null
	 * @param objectPrefix       - if non null will be applied to the object name
	 *                           before starting the download
	 * @param objectName         - must not be null
	 * @param localStartingPoint - must not be null, must be a directory
	 * @return number of bytes downloaded
	 * @throws IOException
	 */
	public int downloadFileCreatePath(@NonNull String bucketName, String objectPrefix, @NonNull String objectName,
			@NonNull File localStartingPoint) throws IOException {
		log.info("Downloading into bucket " + bucketName + " object with prefix " + objectPrefix + " and name "
				+ objectName + " with local starting point of " + localStartingPoint.getPath());
		if (!localStartingPoint.isDirectory()) {
			throw new IOException(
					"Provided starting point " + localStartingPoint.getPath() + "is not a directory, cannot continue");
		}
		String relativeDirectoryPath = objectName;
		// if it starts with a path delimiter remove it (allow for multiple)
		while (relativeDirectoryPath.startsWith(pathSeparatorInObjectStorage)) {
			log.info("removed " + pathSeparatorInObjectStorage + " from start of objectName path");
			relativeDirectoryPath = relativeDirectoryPath.substring(pathSeparatorInObjectStorage.length());
		}
		// get the path element of the object, we need to determine if we will be
		// creating sub directories
		int lastPathSeparator = objectName.lastIndexOf(pathSeparatorInObjectStorage);
		File downloadTarget;
		if (lastPathSeparator >= 0) {
			// OK, there is a path separator in the object name, extract the directory
			// element
			relativeDirectoryPath = objectName.substring(0, lastPathSeparator);
			// make sure any paths are in the local FS format
			relativeDirectoryPath = relativeDirectoryPath.replace(pathSeparatorInObjectStorage, File.separator);
			log.info("The directory path element to be added is " + relativeDirectoryPath);
			File endDir = new File(localStartingPoint.getPath() + File.separator + relativeDirectoryPath);
			log.info("crreating local directory including local start og " + localStartingPoint.getPath()
					+ " and relative dir of " + relativeDirectoryPath + " is " + endDir.getPath());
			endDir.mkdirs();
			String finalObjectName = objectName.substring(lastPathSeparator);
			downloadTarget = new File(endDir.getPath() + File.separator + finalObjectName);
			log.info("tried to create the directory tree for download target of " + downloadTarget.getPath());
		} else {
			downloadTarget = new File(localStartingPoint.getPath() + File.separator + objectName);
			log.info(downloadTarget.getPath() + " is a file in the prefix with no path to no need to create the tree");
		}
		log.info("Downloading to " + bucketName + " from prefix " + objectPrefix + " with name " + objectName
				+ " to local file " + downloadTarget.getPath());
		;
		return downloadFile(bucketName, objectPrefix, objectName, downloadTarget);
	}

	/**
	 * Downloads all for objects under objectName (filtering on objectPrefix if it's
	 * non null) the objects are written to directory localStartingPoint with sub
	 * directories created as needed. If object prefix is specified it is not
	 * included in the downloaded object paths.
	 * 
	 * So if the object tree contains objects named /backup/tim/upload/file-a,
	 * /backup/tim/upload/images/picture1.jpg and
	 * /backup/tim/upload/images/picture2.png then if no object prefix is specified
	 * the directories /backup, /backup/tim, /backup/tim/upload and
	 * /backup/tim/upload/images will be created under the localStartingPoint and
	 * files /backup/tim/upload/file-a, /backup/tim/upload/images/picture1.jpg and
	 * /backup/tim/upload/images/picture2.png created
	 * 
	 * If however the objectPrefix of /backup/tim/upload was specified then only the
	 * /images directory would be created under the localStartingPoint and the
	 * resulting files would be /file-a /images-picture1.jpg and
	 * /images/picture2.png
	 * 
	 * This and the uploadDirectory method are basically a way to transfer a
	 * directory structure to and from object storage
	 * 
	 * @param bucketName
	 * @param objectPrefix
	 * @param localStartingPoint
	 * @return
	 */

	public String downloadObject(@NonNull String bucketName, String objectPrefix, @NonNull File localStartingPoint) {
		log.info("Downloading from bucket " + bucketName + " under prefix " + objectPrefix + " to local starting point "
				+ localStartingPoint.getPath());
		// get the object names
		Set<String> objectNames = listObjectNamesInBucket(bucketName, objectPrefix);
		String result = "";
		for (String objectName : objectNames) {
			try {
				result += objectName + " - "
						+ downloadFileCreatePath(bucketName, objectPrefix, objectName, localStartingPoint) + "\n";
			} catch (IOException e) {
				result += objectName + " - " + e.getLocalizedMessage();
			}
		}
		return result;
	}
}

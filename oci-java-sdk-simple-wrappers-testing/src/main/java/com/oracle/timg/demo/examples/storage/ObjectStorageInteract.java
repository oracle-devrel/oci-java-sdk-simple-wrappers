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
package com.oracle.timg.demo.examples.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;
import com.oracle.timg.oci.identity.IdentityProcessor;
import com.oracle.timg.oci.objectstorage.ObjectStorageProcessor;

import lombok.extern.slf4j.Slf4j;
import timgutilities.textio.TextIOUtils;

@Slf4j
public class ObjectStorageInteract {
	public final static String CONFIG_FILE_SECTION = "DEFAULT";
	public final static String MY_COMPARTMENT = "/domain-specialists/tim.graves";
	public final static String DEFAULT_BUCKET_NAME = "testbucket";
	public final static String DOWNLOADED_SUFFIX = "downloaded";

	public static void main(String[] args) throws IllegalArgumentException, Exception {
		String chosenConfigSection = TextIOUtils.getString("Chose the section in the config file", CONFIG_FILE_SECTION);
		AuthenticationProcessor auth = new AuthenticationProcessor(chosenConfigSection);
		IdentityProcessor identity = new IdentityProcessor(auth);
		ObjectStorageProcessor objectStorage = new ObjectStorageProcessor(auth);
		String compartmentToUse = TextIOUtils.getString("Enter the compartment path to use as the parent",
				MY_COMPARTMENT);
		Compartment compartment = identity.locateCompartmentByPath(compartmentToUse);
		if (compartment == null) {
			log.info("Can't locate compatment " + compartmentToUse);
			System.exit(0);
		} else {
			log.info("Located compatment " + compartmentToUse + "it has ocid " + compartment.getId());
		}
		String s3Compatibility = objectStorage.getS3CompatibilityCompartmentOCID();
		log.info("S3 compatibility compatment OCID is " + s3Compatibility);
		// get the buckets list
		Collection<String> bucketNames = objectStorage.listBucketNamesInCompartment(compartment);
		log.info("Located buckets " + bucketNames);
		String bucketName = TextIOUtils.getString("Please chose the bucket name to use", DEFAULT_BUCKET_NAME);
		if (objectStorage.bucketExists(bucketName)) {
			log.info("Bucket named " + bucketName + " already exists");
		} else {
			log.info("Bucket named " + bucketName + " does not exist, creating it");
			objectStorage.createBucketInCompartment(bucketName, compartment);
		}
		if (TextIOUtils.getYN("Do you want to test transfers ?", false)) {
			String prefixRemote = TextIOUtils.getString("Please enter the remote prefix to use", "/Users/Tim");
			String prefixLocal = TextIOUtils.getDirectory("Please enter the local prefix to start from",
					"/users/tg13456");
			String localFileName = TextIOUtils.getFileUnder(
					"please enter the sample file to upload within " + prefixLocal, prefixLocal,
					"Desktop/manifest-k8s-core.json");
			String localFullPathName = prefixLocal + "/" + localFileName;
			if (TextIOUtils.getYN("Do you want to do a test upload ?", false)) {
				log.info("Starting upload");
				objectStorage.uploadFile(bucketName, prefixRemote, localFileName, new File(localFullPathName));
				log.info("Completed upload");
			}
			if (TextIOUtils.getYN("Do you want to do a test download ? (this will add " + DOWNLOADED_SUFFIX
					+ " to the file name you set) ", false)) {
				log.info("Starting download");
				objectStorage.downloadFile(bucketName, prefixRemote, localFileName,
						new File(localFullPathName + DOWNLOADED_SUFFIX));
				log.info("Completed download");
			}
			if (TextIOUtils.getYN("Do you want to delete the object you just uploaded ?", false)) {
				log.info("Starting delete");
				objectStorage.deleteObject(bucketName, prefixRemote, localFileName);
				log.info("Completed delete");
			}
		}
		if (TextIOUtils.getYN("Do you want to try a directory transfers ?", false)) {
			String prefixRemote = TextIOUtils.getString("Please enter the remote prefix to use", "/Users/Tim");
			String dirPrefixRemote = TextIOUtils.getString(
					"Please enter the remote prefix to use for the directory upload", "/Users/Tim/directory");
			String dirPrefixLocal = TextIOUtils.getDirectory("Please enter the local prefix to start from",
					"/users/tg13456");
			if (TextIOUtils.getYN("Do you want to try a directory upload ?", false)) {
				String localName = TextIOUtils
						.getString("please enter the sample directory to upload from within " + dirPrefixLocal);
				String localPathName = dirPrefixLocal + "/" + localName;

				log.info("Starting Upload of " + localPathName + " with object storage prefix " + prefixRemote);
				String res = objectStorage.uploadObject(bucketName, dirPrefixRemote, new File(localPathName));
				log.info("Upload Processed " + res);
			}
			File localParent = null;
			if (TextIOUtils.getYN("Do you want to try a directory download ?", false)) {
				String localName = TextIOUtils
						.getDirectory(
								"please enter the parent directory to download to - this will have a directory"
										+ DOWNLOADED_SUFFIX + " created within it to hold the download ",
								dirPrefixLocal);
				localName = localName + File.separator + DOWNLOADED_SUFFIX;
				localParent = new File(localName);
				if (!localParent.exists()) {
					log.error(localName + " does not exist, attempting to create it");
					localParent.mkdirs();
				}
				if (!localParent.isDirectory()) {
					log.error(localName + " is not a directory, can't retrive a directory to it");
				} else {
					log.info(" starting download of objects under prefix " + dirPrefixRemote + " to local directory of "
							+ localParent.getPath());
					objectStorage.downloadObject(bucketName, dirPrefixRemote, localParent);
				}
			}
			if (TextIOUtils.getYN(
					"Do you want to delete the object storage tree you just uploaded ? (this will not delete any locally downloaded versions) ",
					false)) {
				log.info("Starting delete");
				objectStorage.deleteObjectsInBucket(bucketName, dirPrefixRemote);
				log.info("Completed delete");
			}
			if ((localParent != null) && TextIOUtils.getYN(
					"Do you want to delete the local directory you downloaded (" + localParent.getPath() + " ?",
					false)) {
				Files.walk(localParent.toPath()) // Traverse the file tree in depth-first order
						.sorted(Comparator.reverseOrder()).forEach(path -> {
							try {
								System.out.println("Deleting: " + path);
								Files.delete(path); // delete each file or directory
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
			}
		}
		if (TextIOUtils.getYN("Do you want to check the prefixes ?", false)) {
			String prefixRemote = TextIOUtils.getString("Please enter the remote prefix to use", "/Users/Tim");
			log.info("Retrieving objects within prefix");
			Collection<String> objectsWithinPrefix = objectStorage.listObjectNamesInBucket(bucketName, prefixRemote);
			log.info("Found " + objectsWithinPrefix.size() + " objects with prefoix " + prefixRemote + " they are named"
					+ objectsWithinPrefix);
			log.info("Retrieved non split out prefixes from root "
					+ objectStorage.listPrefixesInBucket(bucketName, null, false));
			log.info("Retrieved split out from root prefixes "
					+ objectStorage.listPrefixesInBucket(bucketName, null, true));
			log.info("Retrieved non split out prefixes from prefix " + prefixRemote + " are "
					+ objectStorage.listPrefixesInBucket(bucketName, prefixRemote, false));
			log.info("Retrieved split out from prefixes from prefix " + prefixRemote + " are "
					+ objectStorage.listPrefixesInBucket(bucketName, prefixRemote, true));
		}
	}

}

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
package com.oracle.timg.oci.compute;

import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.BootVolume;
import com.oracle.bmc.core.model.BootVolumeKmsKey;
import com.oracle.bmc.core.model.BootVolumeSourceFromBootVolumeDetails;
import com.oracle.bmc.core.model.CreateBootVolumeDetails;
import com.oracle.bmc.core.model.Image;
import com.oracle.bmc.core.requests.CreateBootVolumeRequest;
import com.oracle.bmc.core.requests.GetBootVolumeRequest;
import com.oracle.bmc.core.requests.ListBootVolumesRequest;
import com.oracle.bmc.core.responses.CreateBootVolumeResponse;
import com.oracle.bmc.core.responses.GetBootVolumeResponse;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This class creates and manages boot volumes for compute instances
 */
@Slf4j
public class BootVolumeProcessor {

	private AuthenticationProcessor authProcessor;
	private BlockstorageClient blockstorageClient;

	/**
	 * Creats the processor
	 * 
	 * @param authProcessor
	 */
	public BootVolumeProcessor(@NonNull AuthenticationProcessor authProcessor) {
		this.authProcessor = authProcessor;
		blockstorageClient = BlockstorageClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
	}

	/**
	 * Overrides the region from the authentication processor
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		blockstorageClient.setRegion(regionName);
	}

	/**
	 * If needed will give you the underlying block storage client
	 * 
	 * @return
	 */
	public BlockstorageClient getClient() {
		return blockstorageClient;
	}

	/**
	 * locate a boot volume (it's state must be AVAILABLE) based on the provided
	 * image and in the specified availability domain. This boot volume can be used
	 * as a source to create new boot volumes for instances.
	 * 
	 * @param image
	 * @param availabilityDomain
	 * @param compartment
	 * @return
	 */
	public BootVolume locateBootVolumeByImage(@NonNull Image image, @NonNull AvailabilityDomain availabilityDomain,
			@NonNull Compartment compartment) {
		return locateBootVolumeByImage(image.getId(), availabilityDomain.getName(), compartment.getId());
	}

	/**
	 * locate a boot volume (it's state must be AVAILABLE) based on the provided
	 * image OCID and in the specified availability domain. This boot volume can be
	 * used as a source to create new boot volumes for instances.
	 * 
	 * @param image
	 * @param availabilityDomain
	 * @param compartment
	 * @return
	 */
	public BootVolume locateBootVolumeByImage(@NonNull String imageOcid, @NonNull String availabilityDomainName,
			@NonNull String compartmentOcid) {
		// This only seems to work when scanning all of the potential source volumes
		// available and checking for a match
		Iterable<BootVolume> bootVolumes = blockstorageClient.getPaginators()
				.listBootVolumesRecordIterator(ListBootVolumesRequest.builder()
						.availabilityDomain(availabilityDomainName).compartmentId(compartmentOcid).build());
		for (BootVolume bootVolume : bootVolumes) {
			if (BootVolume.LifecycleState.Available.equals(bootVolume.getLifecycleState())
					&& imageOcid.equals(bootVolume.getImageId())) {
				return bootVolume;
			}
		}
		return null;
	}

	/**
	 * Create a new boot volume based on the provided one, this new one can be used
	 * for instances
	 * 
	 * @param sourceBootVolume
	 * @param name
	 * @param availabilityDomain
	 * @param parentCompartment
	 * @param encryptionKey
	 * @return
	 * @throws Exception
	 */
	public BootVolume createFromBootVolume(@NonNull BootVolume sourceBootVolume, @NonNull String name,
			AvailabilityDomain availabilityDomain, Compartment parentCompartment, BootVolumeKmsKey encryptionKey)
			throws Exception {
		return createFromBootVolume(sourceBootVolume.getId(), name, availabilityDomain.getName(),
				parentCompartment.getId(), encryptionKey == null ? null : encryptionKey.getKmsKeyId());
	}

	/**
	 * Create a new boot volume based on the provided OCID's, this new boot volume
	 * can be used for instances.
	 * 
	 * @param sourceBootVolumeOcid
	 * @param name
	 * @param availabilityDomainName
	 * @param parentCompartmentOcid
	 * @param kmsKeyOcid
	 * @return
	 * @throws Exception
	 */
	public BootVolume createFromBootVolume(@NonNull String sourceBootVolumeOcid, @NonNull String name,
			@NonNull String availabilityDomainName, String parentCompartmentOcid, String kmsKeyOcid) throws Exception {
		// create a new boot volume based on existing one
		CreateBootVolumeDetails details = CreateBootVolumeDetails.builder().availabilityDomain(availabilityDomainName)
				.compartmentId(parentCompartmentOcid).displayName(name)
				.sourceDetails(BootVolumeSourceFromBootVolumeDetails.builder().id(sourceBootVolumeOcid).build())
				.kmsKeyId(kmsKeyOcid).build();
		CreateBootVolumeResponse createBootVolumeResponse = blockstorageClient
				.createBootVolume(CreateBootVolumeRequest.builder().createBootVolumeDetails(details).build());
		log.debug("Provisioning new BootVolume: " + createBootVolumeResponse.getBootVolume().getId());

		// wait for boot volume to be ready
		GetBootVolumeResponse getBootVolumeResponse = blockstorageClient.getWaiters()
				.forBootVolume(GetBootVolumeRequest.builder()
						.bootVolumeId(createBootVolumeResponse.getBootVolume().getId()).build(),
						BootVolume.LifecycleState.Available)
				.execute();
		return getBootVolumeResponse.getBootVolume();
	}
}

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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.ComputeWaiters;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.BootVolumeKmsKey;
import com.oracle.bmc.core.model.CreateVnicDetails;
import com.oracle.bmc.core.model.Image;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceAgentConfig;
import com.oracle.bmc.core.model.InstanceSourceViaImageDetails;
import com.oracle.bmc.core.model.LaunchInstanceDetails;
import com.oracle.bmc.core.model.LaunchInstanceShapeConfigDetails;
import com.oracle.bmc.core.model.Shape;
import com.oracle.bmc.core.model.Subnet;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest;
import com.oracle.bmc.core.responses.GetInstanceResponse;
import com.oracle.bmc.core.responses.GetVnicResponse;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.workrequests.WorkRequestClient;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This class helps you setup and manage compute instances, along with providing
 * related support for things like listing available shapes, images that support
 * a given shape and the like
 */
@Slf4j
public class ComputeProcessor {
	private AuthenticationProcessor authProcessor;
	private ComputeClient computeClient;
	private ComputeWaiters computeWaiters;

	public ComputeProcessor(@NonNull AuthenticationProcessor authProcessor)
			throws IllegalArgumentException, IOException {
		this.authProcessor = authProcessor;
		computeClient = ComputeClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
		WorkRequestClient workRequestClient = WorkRequestClient.builder().build(authProcessor.getProvider());
		computeWaiters = computeClient.newWaiters(workRequestClient);
	}

	/**
	 * Allows you to change the region the provder works agains
	 * 
	 * @param regionName - must not be null
	 */
	public void setRegion(@NonNull String regionName) {
		computeClient.setRegion(regionName);
	}

	/**
	 * returns the underlying OCI sdk client if you need to do something not
	 * directly supported by this class
	 * 
	 * @return
	 */
	public ComputeClient getClient() {
		return computeClient;
	}

	/**
	 * Get a list of all available compute shapes for the given availabiity domain
	 * and compartment
	 * 
	 * @param availabilityDomain - must not be null
	 * @param compartment        - must not be null
	 * @return - List of zero or more shapes
	 */
	public List<Shape> getAllShapes(@NonNull AvailabilityDomain availabilityDomain, @NonNull Compartment compartment) {
		return getAllShapes(availabilityDomain.getName(), compartment.getId());
	}

	/**
	 * Get a list of all available compute shapes for the given availabiity domain
	 * name and compartment with ocid
	 * 
	 * @param availabilityDomain - must not be null
	 * @param compartment        - must not be null
	 * @return - List of zero or more shapes
	 */
	public List<Shape> getAllShapes(@NonNull String availabilityDomainName, @NonNull String compartmentOcid) {
		Iterable<Shape> shapes = computeClient.getPaginators().listShapesRecordIterator(ListShapesRequest.builder()
				.availabilityDomain(availabilityDomainName).compartmentId(compartmentOcid).build());
		return StreamSupport.stream(shapes.spliterator(), false).toList();
	}

	/**
	 * Get a list of available compute shapes starting with VM for the given
	 * availability domain and compartment
	 * 
	 * @param availabilityDomain - must not be null
	 * @param compartment        - must not be null
	 * @return - List of zero or more shapes
	 */
	public List<Shape> getVmShapes(@NonNull AvailabilityDomain availabilityDomain, @NonNull Compartment compartment) {
		return getVmShapes(availabilityDomain.getName(), compartment.getId());
	}

	/**
	 * Get a list of available compute shapes starting with VM for the given
	 * availabiity domain name and compartment with ocid
	 * 
	 * @param availabilityDomain - must not be null
	 * @param compartment        - must not be null
	 * @return - List of zero or more shapes
	 */
	public List<Shape> getVmShapes(@NonNull String availabilityDomainName, @NonNull String compartmentOcid) {
		return getAllShapes(availabilityDomainName, compartmentOcid).stream()
				.filter(shape -> shape.getShape().startsWith("VM")).toList();
	}

	/**
	 * Get the list of Images that can run on the specified shape in the compartment
	 * for a given operating system
	 * 
	 * @param shape           - must not be null
	 * @param compartment     - must not be null
	 * @param operatingSystem - must not be null
	 * @return - List of zero or more images
	 */
	public List<Image> getImages(@NonNull Shape shape, @NonNull Compartment compartment,
			@NonNull String operatingSystem) {
		return getImages(shape.getShape(), compartment.getId(), operatingSystem);
	}

	/**
	 * Get the list of Images that can run on the specified shape name in the
	 * compartment with ocid for a given operating system
	 * 
	 * @param shape           - must not be null
	 * @param compartment     - must not be null
	 * @param operatingSystem - must not be null
	 * @return - List of zero or more images
	 */

	public List<Image> getImages(@NonNull String shapeName, @NonNull String compartmentOcid,
			@NonNull String operatingSystem) {
		Iterable<Image> images = computeClient.getPaginators().listImagesRecordIterator(ListImagesRequest.builder()
				.shape(shapeName).compartmentId(compartmentOcid).operatingSystem(operatingSystem).build());
		return StreamSupport.stream(images.spliterator(), false).toList();
	}

	/**
	 * Locate the instance details for the instance name in the compartment in the
	 * given availability domain Will only return instances that are Starting,
	 * Provisioning or Running. Instances with other states (e.g. deleting, deleted)
	 * are not returned
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @return located instance or null if no instance found
	 */

	public Instance locateInstance(@NonNull String name, @NonNull Compartment compartment,
			@NonNull AvailabilityDomain availabilityDomain) {
		return locateInstance(name, compartment.getId(), availabilityDomain.getName());
	}

	/**
	 * Locate the instance details for the instance name in the compartment with
	 * ocid in the availability domain with it's ocid Will only return instances
	 * that are Starting, Provisioning or Running. Instances with other states (e.g.
	 * deleting, deleted) are not returned
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @return located instance or null if no instance found
	 */

	public Instance locateInstance(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName) {
		Iterable<Instance> instances = computeClient.getPaginators().listInstancesRecordIterator(ListInstancesRequest
				.builder().displayName(name).compartmentId(compartmentOcid).availabilityDomain(availabilityDomainName)
				.lifecycleState(Instance.LifecycleState.Starting).lifecycleState(Instance.LifecycleState.Provisioning)
				.lifecycleState(Instance.LifecycleState.Running).limit(1).build());
		// if there is content return it, otherwise null
		if (instances.iterator().hasNext()) {
			return instances.iterator().next();
		}
		return null;
	}

	/**
	 * Create the launch details (instance configuration basically) based on a shape
	 * using FIXED OCPU / Memory using the provided details, the kmsKeyId can be
	 * null, all other arguments are required
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @param shape              - must not be null
	 * @param image              - must not be null
	 * @param subnet             - must not be null
	 * @param sshPublicKey       - must not be null
	 * @param kmsKeyId           - the key to use to encrypt the boot volume (in a
	 *                           vault) can be null if you want OCI to use it's own
	 *                           keys
	 * @return the details to be used when creating the instance
	 */

	public LaunchInstanceDetails createLaunchInstanceDetails(@NonNull String name, @NonNull Compartment compartment,
			@NonNull AvailabilityDomain availabilityDomain, @NonNull Shape shape, @NonNull Image image,
			@NonNull Subnet subnet, String sshPublicKey, BootVolumeKmsKey kmsKeyId) {
		return createLaunchInstanceDetails(name, compartment.getId(), availabilityDomain.getName(), shape.getShape(),
				image.getId(), subnet.getId(), sshPublicKey, kmsKeyId == null ? null : kmsKeyId.getKmsKeyId());
	}

	/**
	 * Create the launch details (instance configuration basically) based on a shape
	 * using flex OCPU / Memory using the provided details, the kmsKeyId can be
	 * null, all other arguments are required
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @param shape              - must not be null
	 * @param image              - must not be null
	 * @param subnet             - must not be null
	 * @param sshPublicKey       - must not be null
	 * @param kmsKeyId           - the key to use to encrypt the boot volume (in a
	 *                           vault) can be null if you want OCI to use it's own
	 *                           keys
	 * @param ocpuCount          - how many OCPU's wanted for the instance
	 * @param memoryInGbs        - how much memory to allocate to the instance
	 * @return - the details that can be used to create an instance
	 */

	public LaunchInstanceDetails createLaunchInstanceDetails(@NonNull String name, @NonNull Compartment compartment,
			@NonNull AvailabilityDomain availabilityDomain, @NonNull Shape shape, @NonNull Image image,
			@NonNull Subnet subnet, @NonNull String sshPublicKey, BootVolumeKmsKey kmsKeyId, float ocpuCount,
			float memoryInGbs) {
		return createLaunchInstanceDetails(name, compartment.getId(), availabilityDomain.getName(), shape.getShape(),
				image.getId(), subnet.getId(), sshPublicKey, kmsKeyId == null ? null : kmsKeyId.getKmsKeyId(),
				ocpuCount, memoryInGbs);
	}

	/**
	 * Create the launch details (instance configuration basically) based on a shape
	 * using FIXED OCPU / Memory using the provided ocids, the kmsKeyId can be null,
	 * all other arguments are required
	 * 
	 * @param name                   - must not be null
	 * @param compartmentOcid        - must not be null
	 * @param availabilityDomainName - must not be null
	 * @param shapeName              - must not be null
	 * @param imageOcid              - must not be null
	 * @param subnetOcid             - must not be null
	 * @param sshPublicKey           - must not be null
	 * @param kmsKeyIdOcid           - the ocid of the key to use to encrypt the
	 *                               boot volume (in a vault) can be null if you
	 *                               want OCI to use it's own keys
	 * @return - the details that can be used to create an instance
	 */

	public LaunchInstanceDetails createLaunchInstanceDetails(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName, @NonNull String shapeName, @NonNull String imageOcid,
			@NonNull String subnetOcid, @NonNull String sshPublicKey, String kmsKeyOcid) {
		return createLaunchInstanceDetails(name, compartmentOcid, availabilityDomainName, shapeName, imageOcid,
				subnetOcid, sshPublicKey, kmsKeyOcid, null);
	}

	/**
	 * Create the launch details (instance configuration basically) based on a shape
	 * using flex OCPU / Memory using the provided ocids, the kmsKeyId can be null,
	 * all other arguments are required
	 * 
	 * @param name                   - must not be null
	 * @param compartmentOcid        - must not be null
	 * @param availabilityDomainName - must not be null
	 * @param shapeName              - must not be null
	 * @param imageOcid              - must not be null
	 * @param subnetOcid             - must not be null
	 * @param sshPublicKey           - must not be null
	 * @param kmsKeyIdOcid           - the ocid of the key to use to encrypt the
	 *                               boot volume (in a vault) can be null if you
	 *                               want OCI to use it's own keys
	 * @param ocpuCount              - how many OCPU's wanted for the instance
	 * @param memoryInGbs            - how much memory to allocate to the instance
	 * @return - the details that can be used to create an instance
	 */

	public LaunchInstanceDetails createLaunchInstanceDetails(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName, @NonNull String shapeName, @NonNull String imageOcid,
			@NonNull String subnetOcid, @NonNull String sshPublicKey, String kmsKeyOcid, float ocpuCount,
			float memoryInGbs) {
		LaunchInstanceShapeConfigDetails instanceShapeConfigDetails = LaunchInstanceShapeConfigDetails.builder()
				.ocpus(ocpuCount).memoryInGBs(memoryInGbs).build();
		return createLaunchInstanceDetails(name, compartmentOcid, availabilityDomainName, shapeName, imageOcid,
				subnetOcid, sshPublicKey, kmsKeyOcid, instanceShapeConfigDetails);
	}

	/**
	 * Create the launch details (instance configuration basically) based on a shape
	 * which must match the instanceShapeConfigDetails , the kmsKeyId can be null,
	 * all other arguments are required
	 * 
	 * @param name                       - must not be null
	 * @param compartmentOcid            - must not be null
	 * @param availabilityDomainName     - must not be null
	 * @param shapeName                  - must not be null
	 * @param imageOcid                  - must not be null
	 * @param subnetOcid                 - must not be null
	 * @param sshPublicKey               - must not be null
	 * @param kmsKeyIdOcid               - the ocid of the key to use to encrypt the
	 *                                   boot volume (in a vault) can be null if you
	 *                                   want OCI to use it's own keys
	 * @param instanceShapeConfigDetails - must not be null
	 * @return - the details that can be used to create an instance
	 */

	public LaunchInstanceDetails createLaunchInstanceDetails(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName, @NonNull String shapeName, @NonNull String imageOcid,
			@NonNull String subnetOcid, @NonNull String sshPublicKey, String kmsKeyOcid,
			@NonNull LaunchInstanceShapeConfigDetails instanceShapeConfigDetails) {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("ssh_authorized_keys", sshPublicKey);
		Map<String, Object> extendedMetadata = new HashMap<>();
		InstanceSourceViaImageDetails instanceSourceViaImageDetails = InstanceSourceViaImageDetails.builder()
				.imageId(imageOcid).kmsKeyId(kmsKeyOcid).build();
		CreateVnicDetails createVnicDetails = CreateVnicDetails.builder().subnetId(subnetOcid).build();

		// save the partial builder in case we need a launch instance shap config adding
		LaunchInstanceDetails.Builder launchInstanceDetailsBuilder = LaunchInstanceDetails.builder()
				.availabilityDomain(availabilityDomainName).compartmentId(compartmentOcid).displayName(name)
				.sourceDetails(instanceSourceViaImageDetails).metadata(metadata).extendedMetadata(extendedMetadata)
				.shape(shapeName).createVnicDetails(createVnicDetails);
		if (instanceShapeConfigDetails != null) {
			launchInstanceDetailsBuilder.shapeConfig(instanceShapeConfigDetails);
		}
		return launchInstanceDetailsBuilder.build();
	}

	/**
	 * Create an instance based on a shape using fixed OCPU / Memory using the
	 * provided ocids, the kmsKeyId can be null, all other arguments are required
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @param shape              - must not be null
	 * @param image              - must not be null
	 * @param subnet             - must not be null
	 * @param sshPublicKey       - must not be null
	 * @param kmsKeyId           - the key to use to encrypt the boot volume (in a
	 *                           vault) can be null if you want OCI to use it's own
	 *                           keys
	 * @return - The created instance
	 * @throws Exception
	 */

	public Instance createInstance(@NonNull String name, @NonNull Compartment compartment,
			@NonNull AvailabilityDomain availabilityDomain, @NonNull Shape shape, @NonNull Image image,
			@NonNull Subnet subnet, @NonNull String sshPublicKey, BootVolumeKmsKey kmsKeyId) throws Exception {
		return createInstance(name, compartment.getId(), availabilityDomain.getName(), shape.getShape(), image.getId(),
				subnet.getId(), sshPublicKey, kmsKeyId == null ? null : kmsKeyId.getKmsKeyId());
	}

	/**
	 * 
	 * Create an instance based on a shape using flex OCPU / Memory using the
	 * provided ocids, the kmsKeyId can be null, all other arguments are required
	 * 
	 * @param name               - must not be null
	 * @param compartment        - must not be null
	 * @param availabilityDomain - must not be null
	 * @param shape              - must not be null
	 * @param image              - must not be null
	 * @param subnet             - must not be null
	 * @param sshPublicKey       - must not be null
	 * @param kmsKeyId           - the key to use to encrypt the boot volume (in a
	 *                           vault) can be null if you want OCI to use it's own
	 *                           keys
	 * @param ocpuCount          - how many OCPU's wanted for the instance
	 * @param memoryInGbs        - how much memory to allocate to the instance
	 * @return - The created instance
	 * @throws Exception
	 */

	public Instance createInstance(@NonNull String name, @NonNull Compartment compartment,
			@NonNull AvailabilityDomain availabilityDomain, @NonNull Shape shape, @NonNull Image image,
			@NonNull Subnet subnet, @NonNull String sshPublicKey, BootVolumeKmsKey kmsKeyId, float ocpuCount,
			float memCount) throws Exception {
		return createInstance(name, compartment.getId(), availabilityDomain.getName(), shape.getShape(), image.getId(),
				subnet.getId(), sshPublicKey, kmsKeyId == null ? null : kmsKeyId.getKmsKeyId(), ocpuCount, memCount);
	}

	/**
	 * Create an instance based on a shape using fixed OCPU / Memory using the
	 * provided ocids, the kmsKeyId can be null, all other arguments are required
	 * 
	 * @param name                   - must not be null
	 * @param compartmentOcid        - must not be null
	 * @param availabilityDomainName - must not be null
	 * @param shapeName              - must not be null
	 * @param imageOcid              - must not be null
	 * @param subnetOcid             - must not be null
	 * @param sshPublicKey           - must not be null
	 * @param kmsKeyOcid             - the ocid of key to use to encrypt the boot
	 *                               volume (in a vault) can be null if you want OCI
	 *                               to use it's own keys
	 * @return - The created instance
	 * @throws Exception
	 */

	public Instance createInstance(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName, @NonNull String shapeName, @NonNull String imageOcid,
			@NonNull String subnetOcid, @NonNull String sshPublicKey, String kmsKeyOcid) throws Exception {
		LaunchInstanceDetails details = createLaunchInstanceDetails(name, compartmentOcid, availabilityDomainName,
				shapeName, imageOcid, subnetOcid, sshPublicKey, kmsKeyOcid);
		return createInstance(details);
	}

	/**
	 * 
	 * @param name                   - must not be null
	 * @param compartmentOcid        - must not be null
	 * @param availabilityDomainName - must not be null
	 * @param shapeName              - must not be null
	 * @param imageOcid              - must not be null
	 * @param subnetOcid             - must not be null
	 * @param sshPublicKey           - must not be null
	 * @param kmsKeyOcid             - the ocid of key to use to encrypt the boot
	 *                               volume (in a vault) can be null if you want OCI
	 *                               to use it's own keys
	 * @param ocpuCount              - how many OCPU's wanted for the instance
	 * @param memoryInGbs            - how much memory to allocate to the instance
	 * @return - The created instance
	 * @throws Exception
	 */

	public Instance createInstance(@NonNull String name, @NonNull String compartmentOcid,
			@NonNull String availabilityDomainName, @NonNull String shapeName, @NonNull String imageOcid,
			@NonNull String subnetOcid, @NonNull String sshPublicKey, String kmsKeyOcid, float ocpuCount,
			float memCount) throws Exception {
		LaunchInstanceDetails details = createLaunchInstanceDetails(name, compartmentOcid, availabilityDomainName,
				shapeName, imageOcid, subnetOcid, sshPublicKey, kmsKeyOcid, ocpuCount, memCount);
		return createInstance(details);
	}

	/**
	 * Create an instance based on the provide launch details
	 * 
	 * @param launchInstanceDetails - must not be null
	 * @return - The created instance
	 * @throws Exception
	 */

	public Instance createInstance(@NonNull LaunchInstanceDetails launchInstanceDetails) throws Exception {
		LaunchInstanceRequest launchInstanceRequest = LaunchInstanceRequest.builder()
				.launchInstanceDetails(launchInstanceDetails).build();
		LaunchInstanceResponse launchInstanceResponse = computeWaiters.forLaunchInstance(launchInstanceRequest)
				.execute();

		GetInstanceRequest getInstanceRequest = GetInstanceRequest.builder()
				.instanceId(launchInstanceResponse.getInstance().getId()).build();
		GetInstanceResponse getInstanceResponse = computeWaiters
				.forInstance(getInstanceRequest, Instance.LifecycleState.Running).execute();
		Instance instance = getInstanceResponse.getInstance();
		log.debug("Created instance " + instance);
		return instance;
	}

	/**
	 * For the specified instance get it's VNIC attachments (these connect to the
	 * subnets)
	 * 
	 * @param instance - must not be null
	 * @return - List of zero or more vnic attachments
	 */

	public List<VnicAttachment> getInstanceVnicAttachements(@NonNull Instance instance) {
		return getInstanceVnicAttachements(instance.getId(), instance.getCompartmentId());
	}

	/**
	 * For the specified instance with ocid in it's parent comparment get it's VNIC
	 * attachments (these connect to the subnets)
	 * 
	 * @param instanceOcid          - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @return - List of zero or more vnic attachments
	 */
	public List<VnicAttachment> getInstanceVnicAttachements(@NonNull String instanceOcid,
			@NonNull String parentCompartmentOcid) {
		ListVnicAttachmentsRequest listVnicAttachmentsRequest = ListVnicAttachmentsRequest.builder()
				.compartmentId(parentCompartmentOcid).instanceId(instanceOcid).build();
		ListVnicAttachmentsResponse listVnicAttachmentsResponse = computeClient
				.listVnicAttachments(listVnicAttachmentsRequest);
		return listVnicAttachmentsResponse.getItems();
	}

	/**
	 * For the specified instance get the details. if the use the virtual network
	 * client is non null this will also report the vnic details
	 * 
	 * @param instance             - must not be null
	 * @param virtualNetworkClient - if null then network vnic details will be
	 *                             located
	 * @return
	 */

	public String printInstance(@NonNull Instance instance, VirtualNetworkClient virtualNetworkClient) {
		String info = "";
		if (virtualNetworkClient != null) {
			info += "Virtual Network Interface Cards\n";
			List<VnicAttachment> vnicAttachments = getInstanceVnicAttachements(instance);
			for (VnicAttachment vnicAttachment : vnicAttachments) {
				GetVnicRequest getVnicRequest = GetVnicRequest.builder().vnicId(vnicAttachment.getVnicId()).build();
				GetVnicResponse getVnicResponse = virtualNetworkClient.getVnic(getVnicRequest);
				Vnic vnic = getVnicResponse.getVnic();
				info += "    " + vnic.getId() + "\n";
			}

		}
		InstanceAgentConfig instanceAgentConfig = instance.getAgentConfig();
		boolean monitoringEnabled = (instanceAgentConfig != null) && !instanceAgentConfig.getIsMonitoringDisabled();
		String monitoringStatus = (monitoringEnabled ? "Enabled" : "Disabled");
		info += "Instance " + instance.getId() + " has monitoring " + monitoringStatus + "\n";
		return info;
	}
}
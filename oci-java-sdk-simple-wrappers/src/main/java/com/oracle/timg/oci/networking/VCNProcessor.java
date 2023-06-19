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
package com.oracle.timg.oci.networking;

import java.io.IOException;
import java.util.List;
import java.util.stream.StreamSupport;

import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.CreateInternetGatewayDetails;
import com.oracle.bmc.core.model.CreateSubnetDetails;
import com.oracle.bmc.core.model.CreateVcnDetails;
import com.oracle.bmc.core.model.InternetGateway;
import com.oracle.bmc.core.model.RouteRule;
import com.oracle.bmc.core.model.RouteTable;
import com.oracle.bmc.core.model.Subnet;
import com.oracle.bmc.core.model.UpdateRouteTableDetails;
import com.oracle.bmc.core.model.Vcn;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.core.requests.CreateInternetGatewayRequest;
import com.oracle.bmc.core.requests.CreateSubnetRequest;
import com.oracle.bmc.core.requests.CreateVcnRequest;
import com.oracle.bmc.core.requests.DeleteInternetGatewayRequest;
import com.oracle.bmc.core.requests.DeleteSubnetRequest;
import com.oracle.bmc.core.requests.DeleteVcnRequest;
import com.oracle.bmc.core.requests.GetInternetGatewayRequest;
import com.oracle.bmc.core.requests.GetRouteTableRequest;
import com.oracle.bmc.core.requests.GetSubnetRequest;
import com.oracle.bmc.core.requests.GetVcnRequest;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.ListInternetGatewaysRequest;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.requests.ListVcnsRequest;
import com.oracle.bmc.core.requests.UpdateRouteTableRequest;
import com.oracle.bmc.core.responses.CreateInternetGatewayResponse;
import com.oracle.bmc.core.responses.CreateSubnetResponse;
import com.oracle.bmc.core.responses.CreateVcnResponse;
import com.oracle.bmc.core.responses.GetInternetGatewayResponse;
import com.oracle.bmc.core.responses.GetRouteTableResponse;
import com.oracle.bmc.core.responses.GetSubnetResponse;
import com.oracle.bmc.core.responses.GetVcnResponse;
import com.oracle.bmc.core.responses.GetVnicResponse;
import com.oracle.bmc.core.responses.ListInternetGatewaysResponse;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This class allows you to create, destring and list VCN's and resources.
 */
@Slf4j
public class VCNProcessor {

	public final static String ALL_IP_CIDR = "0.0.0.0/0";

	private AuthenticationProcessor authProcessor;
	private VirtualNetworkClient vcnClient;

	/**
	 * Creates a VCNProcessor which will use the supplied AuthenticationProcessor
	 * 
	 * @param authProcessor
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	public VCNProcessor(AuthenticationProcessor authProcessor) throws IllegalArgumentException, IOException {
		this.authProcessor = authProcessor;
		vcnClient = VirtualNetworkClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
	}

	/**
	 * changes the region this processor will work against.
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		vcnClient.setRegion(regionName);
	}

	/**
	 * provides the underlying OCI JDK client for operations not supported within
	 * this wrapper class.
	 * 
	 * @return
	 */
	public VirtualNetworkClient getClient() {
		return vcnClient;
	}

	/**
	 * Finds an available, provisioning or updating VCN by name in the root
	 * compartment of the tenancy
	 * 
	 * @param name - must not be null
	 * @return the VCN or null if not found
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Vcn locateVCN(@NonNull String name) throws IllegalArgumentException, Exception {
		return locateVCN(name, authProcessor.getTenancyOCID());
	}

	/**
	 * Finds an available, provisioning or updating VCN by name in the specified
	 * compartment
	 * 
	 * @param name              - must not be null
	 * @param parentCompartment - must not be null
	 * @return the VCN or null if not found
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */

	public Vcn locateVCN(@NonNull String name, @NonNull Compartment parentCompartment)
			throws IllegalArgumentException, Exception {
		return locateVCN(name, parentCompartment.getId());
	}

	/**
	 * Finds an available, provisioning or updating VCN by name in the compartment
	 * with the specified ocid
	 * 
	 * @param name                  - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @return the VCN or null if not found
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Vcn locateVCN(@NonNull String name, @NonNull String parentCompartmentOcid)
			throws IllegalArgumentException, Exception {
		// locate the first active compartment with the matching name
		Iterable<Vcn> vcns = vcnClient.getPaginators()
				.listVcnsRecordIterator(ListVcnsRequest.builder().displayName(name).compartmentId(parentCompartmentOcid)
						.lifecycleState(Vcn.LifecycleState.Available).lifecycleState(Vcn.LifecycleState.Provisioning)
						.lifecycleState(Vcn.LifecycleState.Updating).limit(1).build());
		if (vcns.iterator().hasNext()) {
			return vcns.iterator().next();
		} else {
			return null;
		}
	}

	/**
	 * get the vcn's in the tenancy root compartment in the available, provisioning
	 * or updating state only
	 * 
	 * @return a list of zero or more vcn's
	 */
	public List<Vcn> listVcns() {
		return listVcns(authProcessor.getTenancyOCID());
	}

	/**
	 * get the vcn's in the provided compartment in the available, provisioning or
	 * updating state only
	 * 
	 * @param parentCompartment - must not be null
	 * @return a list of zero or more vcn's
	 */
	public List<Vcn> listVcns(@NonNull Compartment parentCompartment) {
		return listVcns(parentCompartment.getId());
	}

	/**
	 * 
	 * get the vcn's in the compartment with the specified ocid in the available,
	 * provisioning or updating state only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<Vcn> listVcns(@NonNull String parentCompartmentOcid) {
		Iterable<Vcn> vcns = vcnClient.getPaginators()
				.listVcnsRecordIterator(new ListVcnsRequest().builder().compartmentId(parentCompartmentOcid)
						.lifecycleState(Vcn.LifecycleState.Available).lifecycleState(Vcn.LifecycleState.Provisioning)
						.lifecycleState(Vcn.LifecycleState.Updating).build());
		return StreamSupport.stream(vcns.spliterator(), false).toList();
	}

	/**
	 * create a VCN with the provides name and using the CIDR block in the specified
	 * compartment
	 * 
	 * @param name              - must not be null
	 * @param cidrBlock         - must not be null
	 * @param parentCompartment - must not be null
	 * @return the created VCN
	 * @throws Exception
	 */
	public Vcn createVcn(@NonNull String name, @NonNull String cidrBlock, @NonNull Compartment parentCompartment)
			throws Exception {
		return createVcn(name, cidrBlock, parentCompartment.getId());
	}

	/**
	 * create a VCN with the provides name and using the CIDR block in the
	 * compartment with the specified ocid
	 * 
	 * @param name                  - must not be null
	 * @param cidrBlock             - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @return the created VCN
	 * @throws Exception
	 */
	public Vcn createVcn(@NonNull String name, @NonNull String cidrBlock, @NonNull String parentCompartmentOcid)
			throws Exception {
		CreateVcnResponse createVcnResponse = vcnClient
				.createVcn(CreateVcnRequest.builder().createVcnDetails(CreateVcnDetails.builder().cidrBlock(cidrBlock)
						.compartmentId(parentCompartmentOcid).displayName(name).build()).build());

		final GetVcnResponse getVcnResponse = vcnClient.getWaiters()
				.forVcn(GetVcnRequest.builder().vcnId(createVcnResponse.getVcn().getId()).build(),
						Vcn.LifecycleState.Available)
				.execute();
		Vcn vcn = getVcnResponse.getVcn();
		log.debug("Created vcn with ocid " + vcn.getId() + " in compartment " + vcn.getCompartmentId());

		return vcn;
	}

	/**
	 * Delete the specified VCN - this cannot have any other resources connected to
	 * it (e.g. vnics, gateways etc.) it.
	 * 
	 * @param vcn - must not be null
	 * @throws Exception
	 */
	public void deleteVcn(@NonNull Vcn vcn) throws Exception {
		deleteVcn(vcn.getId());
	}

	/**
	 * Delete the VCN with the specified ocid - this cannot have any other resources
	 * connected to it (e.g. vnics, gateways etc.)
	 * 
	 * @param vcnOcid - must not be null
	 * @throws Exception
	 */
	public void deleteVcn(@NonNull String vcnOcid) throws Exception {
		vcnClient.deleteVcn(DeleteVcnRequest.builder().vcnId(vcnOcid).build());
		vcnClient.getWaiters().forVcn(GetVcnRequest.builder().vcnId(vcnOcid).build(), Vcn.LifecycleState.Terminated)
				.execute();
		log.debug("Deleted VCN " + vcnOcid);
	}

	/**
	 * Locate the first VCN with the specified name in the parentVCN i nthe
	 * parentCompartment
	 * 
	 * @param name              - must not be null
	 * @param parentCompartment - must not be null
	 * @param parentVcn         - must not be null
	 * @return - the subnet or null if it can't be found
	 */
	public Subnet locateSubnet(@NonNull String name, @NonNull Compartment parentCompartment, @NonNull Vcn parentVcn) {
		return locateSubnet(name, parentCompartment.getId(), parentVcn.getId());
	}

	/**
	 * Locate the first VCN with the specified name in the parwentVCN withthe
	 * specified OCID in the parentCompartment with the specified OCID
	 * 
	 * @param name                  - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @param parentVcnOcid         - must not be null
	 * @return - the subnet or null if it can't be found
	 */
	public Subnet locateSubnet(@NonNull String name, @NonNull String parentCompartmentOcid,
			@NonNull String parentVcnOcid) {
		// locate the first active subnet with the matching name
		Iterable<Subnet> subnets = vcnClient.getPaginators().listSubnetsRecordIterator(
				ListSubnetsRequest.builder().compartmentId(parentCompartmentOcid).vcnId(parentVcnOcid).displayName(name)
						.lifecycleState(Subnet.LifecycleState.Available).limit(1).build());
		if (subnets.iterator().hasNext()) {
			return subnets.iterator().next();
		} else {
			return null;
		}
	}

	/**
	 * list the subnets in the specified vcn in the root comparment of the tenancy
	 * 
	 * @param parentVcn - must not be null
	 * @return - a list of zero or more subnets
	 */
	public List<Subnet> listSubnets(@NonNull Vcn parentVcn) {
		return listSubnets(parentVcn.getId());
	}

	/**
	 * list the subnets in the vcn with the specified ocid in the root comparment of
	 * the tenancy
	 * 
	 * @param parentVcnOcid - must not be null
	 * @return - a list of zero or more subnets
	 */
	public List<Subnet> listSubnets(@NonNull String parentVcnOcid) {
		return listSubnets(parentVcnOcid, authProcessor.getTenancyOCID());
	}

	/**
	 * list the subnets in the specified vcn in the specified comparment
	 * 
	 * @param parentVcn         - must not be null
	 * @param parentCompartment - must not be null
	 * @return - a list of zero or more subnets
	 */
	public List<Subnet> listSubnets(@NonNull Vcn parentVcn, @NonNull Compartment parentCompartment) {
		return listSubnets(parentVcn.getId(), parentCompartment.getId());
	}

	/**
	 * 
	 * list the subnets in the vcn with the specified ocid in the comparment with
	 * the specified ocid
	 * 
	 * @param parentVcnOcid         - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @return - a list of zero or more subnets
	 */
	public List<Subnet> listSubnets(@NonNull String parentVcnOcid, @NonNull String parentCompartmentOcid) {
		Iterable<Subnet> subnets = vcnClient.getPaginators()
				.listSubnetsRecordIterator(ListSubnetsRequest.builder().compartmentId(parentCompartmentOcid)
						.vcnId(parentVcnOcid).lifecycleState(Subnet.LifecycleState.Available).build());
		return StreamSupport.stream(subnets.spliterator(), false).toList();
	}

	/**
	 * Create a subnet using the provided name and CIRD address range in the parent
	 * VCN and parent compartment
	 * 
	 * @param name              - must not be null
	 * @param cidrBlock         - must not be null
	 * @param parentVcn         - must not be null
	 * @param parentCompartment - must not be null
	 * @param privateOnly       - if true will not have a public facing IP
	 *                          capability
	 * @return the newly created subnet
	 * @throws Exception if the subnet can't be created (e.g. invalid compartment,
	 *                   no permission etc.
	 */
	public Subnet createSubnet(@NonNull String name, @NonNull String cidrBlock, @NonNull Vcn parentVcn,
			@NonNull Compartment parentCompartment, boolean privateOnly) throws Exception {
		return createSubnet(name, cidrBlock, parentVcn.getId(), parentCompartment.getId(), privateOnly);
	}

	/**
	 * Create a subnet using the provided name and CIRD address range in the parent
	 * VCN with the specified ocid and parent compartment with the specified ocid
	 * 
	 * @param name                  - must not be null
	 * @param cidrBlock             - must not be null
	 * @param parentVcnOcid         - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @param privateOnly           - if true will not have a public facing IP
	 *                              capability
	 * @return the newly created subnet
	 * @throws Exception if the subnet can't be created (e.g. invalid compartment,
	 *                   no permission etc.
	 */
	public Subnet createSubnet(@NonNull String name, @NonNull String cidrBlock, @NonNull String parentVcnOcid,
			@NonNull String parentCompartmentOcid, boolean privateOnly) throws Exception {
		CreateSubnetResponse createSubnetResponse = vcnClient
				.createSubnet(CreateSubnetRequest.builder()
						.createSubnetDetails(CreateSubnetDetails.builder().compartmentId(parentCompartmentOcid)
								.displayName(name).cidrBlock(cidrBlock).vcnId(parentVcnOcid)
								.prohibitInternetIngress(privateOnly).build())
						.build());

		GetSubnetResponse getSubnetResponse = vcnClient.getWaiters()
				.forSubnet(GetSubnetRequest.builder().subnetId(createSubnetResponse.getSubnet().getId()).build(),
						Subnet.LifecycleState.Available)
				.execute();
		Subnet subnet = getSubnetResponse.getSubnet();
		log.debug("Created subnet with ocid " + subnet.getId() + " on vcn ocid " + subnet.getVcnId()
				+ " in compartment " + subnet.getCompartmentId());
		return subnet;
	}

	/**
	 * delete the provided subnet
	 * 
	 * @param subnet - must not be null
	 * @throws Exception
	 */
	public void deleteSubnet(@NonNull Subnet subnet) throws Exception {
		deleteSubnet(subnet.getId());
	}

	/**
	 * delete the subnet with the specified ocid
	 * 
	 * @param subnetOcid - must not be null
	 * @throws Exception
	 */
	public void deleteSubnet(@NonNull String subnetOcid) throws Exception {
		vcnClient.deleteSubnet(DeleteSubnetRequest.builder().subnetId(subnetOcid).build());
		vcnClient.getWaiters()
				.forSubnet(GetSubnetRequest.builder().subnetId(subnetOcid).build(), Subnet.LifecycleState.Terminated)
				.execute();
		log.debug("Deleted subnet " + subnetOcid);
	}

	/**
	 * list available or provisioning internet gateways in the specified compartment
	 * and vcn
	 * 
	 * @param parentCompartment - must not be null
	 * @param vcn               - must not be null
	 * @return - a list of zero or more internet gateways
	 */
	public List<InternetGateway> listInternetGateways(@NonNull Compartment parentCompartment, @NonNull Vcn vcn) {
		return listInternetGateways(parentCompartment.getId(), vcn.getId());
	}

	/**
	 * list available or provisioning internet gateways in the compartment with the
	 * specified ocid and vcn with the specified ocid
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @param vcnOcid               - must not be null
	 * @return - a list of zero or more internet gateways
	 */
	public List<InternetGateway> listInternetGateways(@NonNull String parentCompartmentOcid, @NonNull String vcnOcid) {
		Iterable<InternetGateway> gateways = vcnClient.getPaginators()
				.listInternetGatewaysRecordIterator(ListInternetGatewaysRequest.builder().vcnId(vcnOcid)
						.compartmentId(parentCompartmentOcid).lifecycleState(InternetGateway.LifecycleState.Available)
						.lifecycleState(InternetGateway.LifecycleState.Provisioning).build());
		return StreamSupport.stream(gateways.spliterator(), false).toList();
	}

	/**
	 * find a matching available or provisioning internet gateway in the specified
	 * compartment and vcn
	 * 
	 * @param name              - must not be null
	 * @param parentCompartment - must not be null
	 * @param vcn               - must not be null
	 * @return - the matching internet gateway or null if not found
	 */
	public InternetGateway locateInternetGateway(@NonNull String name, @NonNull Compartment parentCompartment,
			@NonNull Vcn vcn) throws Exception {
		return locateInternetGateway(name, parentCompartment.getId(), vcn.getId());
	}

	/**
	 * 
	 * find a matching available or provisioning internet gateway in the compartment
	 * with the specified ocid and vcn with the specified ocid
	 * 
	 * @param name                  - must not be null
	 * @param parentCompartmentOcid - must not be null
	 * @param vcnOcid               - must not be null
	 * @return - the matching internet gateway or null if not found
	 */
	public InternetGateway locateInternetGateway(@NonNull String name, @NonNull String parentCompartmentOcid,
			@NonNull String vcnOcid) throws Exception {
		ListInternetGatewaysResponse response = vcnClient
				.listInternetGateways(ListInternetGatewaysRequest.builder().displayName(name).vcnId(vcnOcid)
						.compartmentId(parentCompartmentOcid).lifecycleState(InternetGateway.LifecycleState.Available)
						.lifecycleState(InternetGateway.LifecycleState.Provisioning).limit(1).build());
		// look at the resulting data
		List<InternetGateway> items = response.getItems();
		// if there's a match return it, if not null
		if (items.size() > 0) {
			return items.get(0);
		} else {
			return null;
		}
	}

	/**
	 * Create an internet gateway in the specified compartment and vcn
	 * 
	 * @param name              - must not be null
	 * @param parentCompartment - must not be null
	 * @param vcn               - must not be null
	 * @return - the created internet gateway
	 * @throws Exception
	 */
	public InternetGateway createInternetGateway(@NonNull String name, @NonNull Compartment parentCompartment,
			@NonNull Vcn vcn) throws Exception {
		return createInternetGateway(name, parentCompartment.getId(), vcn.getId());
	}

	/**
	 * Create an internet gateway in the compartment with the specified ocid and vcn
	 * with the specified ocid
	 * 
	 * @param name              - must not be null
	 * @param parentCompartment - must not be null
	 * @param vcn               - must not be null
	 * @return - the created internet gateway
	 * @throws Exception
	 */
	public InternetGateway createInternetGateway(@NonNull String name, @NonNull String parentCompartmentOcid,
			@NonNull String vcnOcid) throws Exception {
		CreateInternetGatewayRequest createInternetGatewayRequest = CreateInternetGatewayRequest.builder()
				.createInternetGatewayDetails(CreateInternetGatewayDetails.builder()
						.compartmentId(parentCompartmentOcid).displayName(name).isEnabled(true).vcnId(vcnOcid).build())
				.build();
		CreateInternetGatewayResponse createInternetGatewayResponse = vcnClient
				.createInternetGateway(createInternetGatewayRequest);

		GetInternetGatewayRequest getInternetGatewayRequest = GetInternetGatewayRequest.builder()
				.igId(createInternetGatewayResponse.getInternetGateway().getId()).build();
		GetInternetGatewayResponse getInternetGatewayResponse = vcnClient.getWaiters()
				.forInternetGateway(getInternetGatewayRequest, InternetGateway.LifecycleState.Available).execute();
		InternetGateway internetGateway = getInternetGatewayResponse.getInternetGateway();
		log.debug("Created internet gateway with ocid " + internetGateway.getId() + " on vcn ocid "
				+ internetGateway.getVcnId() + " in compartment " + internetGateway.getCompartmentId());
		return internetGateway;
	}

	/**
	 * Delete the specified internet gateway
	 * 
	 * @param internetGateway - must not be null
	 * @throws Exception
	 */
	public void deleteInternetGateway(@NonNull InternetGateway internetGateway) throws Exception {
		deleteInternetGateway(internetGateway.getId());
	}

	/**
	 * Delete the internet gateway with the specified ocid
	 * 
	 * @param internetGatewayOcid - must not be null
	 * @throws Exception
	 */
	public void deleteInternetGateway(@NonNull String internetGatewayOcid) throws Exception {
		vcnClient.deleteInternetGateway(DeleteInternetGatewayRequest.builder().igId(internetGatewayOcid).build());

		GetInternetGatewayRequest getInternetGatewayRequest = GetInternetGatewayRequest.builder()
				.igId(internetGatewayOcid).build();
		vcnClient.getWaiters().forInternetGateway(getInternetGatewayRequest, InternetGateway.LifecycleState.Terminated)
				.execute();
		log.debug("delete internet gateway with ocid " + internetGatewayOcid);
	}

	/**
	 * adds the provided internet gateway as a destination in the route table
	 * 
	 * @param vcn             - must not be null
	 * @param internetGateway - must not be null
	 * @return - the route table entry if created
	 * @throws Exception
	 */
	public RouteRule addInternetGatewayToDefaultRouteTable(@NonNull Vcn vcn, @NonNull InternetGateway internetGateway)
			throws Exception {
		return addGatewayToRouteTable(internetGateway.getId(), vcn.getDefaultRouteTableId(), ALL_IP_CIDR);
	}

	/**
	 * adds the provided internet gateway as a destination in the route table for
	 * the internted gateway and cidr range with the specified ocid and routetable
	 * with the specified ocid
	 * 
	 * @param vcn             - must not be null
	 * @param internetGateway - must not be null
	 * @return - the route table entry if created
	 * @throws Exception
	 */
	public RouteRule addGatewayToRouteTable(@NonNull String destinationOcid, @NonNull String routeTableOcid,
			@NonNull String destinationCidrBlock) throws Exception {
		GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder().rtId(routeTableOcid).build();
		GetRouteTableResponse getRouteTableResponse = vcnClient.getRouteTable(getRouteTableRequest);
		List<RouteRule> routeRules = getRouteTableResponse.getRouteTable().getRouteRules();

		RouteRule existingRouteRule = locateRouteRule(routeTableOcid, destinationOcid, destinationCidrBlock);
		if (existingRouteRule == null) {
			RouteRule accessRoute = RouteRule.builder().destination(destinationCidrBlock)
					.destinationType(RouteRule.DestinationType.CidrBlock).networkEntityId(destinationOcid).build();
			routeRules.add(accessRoute);
			UpdateRouteTableDetails updateRouteTableDetails = UpdateRouteTableDetails.builder().routeRules(routeRules)
					.build();
			UpdateRouteTableRequest updateRouteTableRequest = UpdateRouteTableRequest.builder()
					.updateRouteTableDetails(updateRouteTableDetails).rtId(routeTableOcid).build();
			vcnClient.updateRouteTable(updateRouteTableRequest);

			getRouteTableResponse = vcnClient.getWaiters()
					.forRouteTable(getRouteTableRequest, RouteTable.LifecycleState.Available).execute();
			routeRules = getRouteTableResponse.getRouteTable().getRouteRules();
			log.debug("Added gateway rule to destination " + destinationCidrBlock + " via gateway "
					+ destinationCidrBlock);
			return accessRoute;
		} else {
			log.debug("Gateway rule to destination " + destinationCidrBlock + " via gateway " + destinationCidrBlock
					+ " already exists in route table");
			return existingRouteRule;
		}
	}

	/**
	 * Locate the route rule if any in the route table with the specified ocid, to
	 * the destination with the specified ocid and the specified cidr block
	 * 
	 * @param routeTableOcid       - must not be null
	 * @param destinationOcid      - must not be null
	 * @param destinationCidrBlock - must not be null
	 * @return matching route table if found or null
	 * @throws Exception
	 */
	public RouteRule locateRouteRule(@NonNull String routeTableOcid, @NonNull String destinationOcid,
			@NonNull String destinationCidrBlock) throws Exception {
		GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder().rtId(routeTableOcid).build();
		GetRouteTableResponse getRouteTableResponse = vcnClient.getRouteTable(getRouteTableRequest);
		List<RouteRule> routeRules = getRouteTableResponse.getRouteTable().getRouteRules();
		List<RouteRule> filteredRouteRules = routeRules.stream()
				.filter(routeRule -> routeRule.getDestinationType().equals(RouteRule.DestinationType.CidrBlock))
				.filter(routeRule -> routeRule.getDestination().equals(destinationCidrBlock))
				.filter(routeRule -> routeRule.getNetworkEntityId().equals(destinationOcid)).toList();
		if (filteredRouteRules.size() > 0) {
			return filteredRouteRules.get(0);
		} else {
			return null;
		}
	}

	/**
	 * remove the specified internet gateway from the vcn route table
	 * 
	 * @param vcn             - must not be null
	 * @param internetGateway - must not be null
	 * @throws Exception
	 */
	public void removeInternetGatewayFromDefaultRouteTable(@NonNull Vcn vcn, @NonNull InternetGateway internetGateway)
			throws Exception {
		deleteGatewayFromRouteTable(internetGateway.getId(), vcn.getDefaultRouteTableId(), ALL_IP_CIDR);
	}

	/**
	 * remove the specified gateway from the vcn route table
	 * 
	 * @param destinationOcid      - must not be null
	 * @param routeTableOcid       - must not be null
	 * @param destinationCidrBlock - must not be null
	 * @throws Exception
	 */
	/**
	 * 
	 * @p
	 */
	public void deleteGatewayFromRouteTable(@NonNull String destinationOcid, @NonNull String routeTableOcid,
			@NonNull String destinationCidrBlock) throws Exception {
		RouteRule existingRouteRule = locateRouteRule(routeTableOcid, destinationOcid, destinationCidrBlock);
		if (existingRouteRule == null) {
			return;
		}

		GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder().rtId(routeTableOcid).build();
		GetRouteTableResponse getRouteTableResponse = vcnClient.getRouteTable(getRouteTableRequest);

		List<RouteRule> routeRules = getRouteTableResponse.getRouteTable().getRouteRules();
		routeRules.remove(existingRouteRule);
		UpdateRouteTableDetails updateRouteTableDetails = UpdateRouteTableDetails.builder().routeRules(routeRules)
				.build();
		UpdateRouteTableRequest updateRouteTableRequest = UpdateRouteTableRequest.builder()
				.updateRouteTableDetails(updateRouteTableDetails).rtId(routeTableOcid).build();
		vcnClient.updateRouteTable(updateRouteTableRequest);

		vcnClient.getWaiters().forRouteTable(getRouteTableRequest, RouteTable.LifecycleState.Available).execute();
		log.debug("deleted gateway " + destinationOcid + "with cidr block " + destinationCidrBlock
				+ " from route table " + routeTableOcid);
	}

	/**
	 * gets the vnic used by the specified attachement
	 * 
	 * @param vnicAttachment - must not be null
	 * @return
	 */
	public Vnic getVnicFromAttachement(@NonNull VnicAttachment vnicAttachment) {
		return getVnicFromAttachement(vnicAttachment.getVnicId());
	}

	/**
	 * gets the vnic used by the attachement with the specified ocid
	 * 
	 * @param vnicAttachmentOcid - must not be null
	 * @return
	 */
	public Vnic getVnicFromAttachement(@NonNull String vnicAttachmentOcid) {
		GetVnicRequest getVnicRequest = GetVnicRequest.builder().vnicId(vnicAttachmentOcid).build();
		GetVnicResponse getVnicResponse = vcnClient.getVnic(getVnicRequest);
		return getVnicResponse.getVnic();
	}

}
